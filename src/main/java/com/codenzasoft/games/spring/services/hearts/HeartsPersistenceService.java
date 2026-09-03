package com.codenzasoft.games.spring.services.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.*;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsGameEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsRoundHandEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsRoundPassEntity;
import com.codenzasoft.games.spring.entities.hearts.HeartsTrickCardEntity;
import com.codenzasoft.games.spring.repositories.hearts.HeartsGameRepository;
import com.codenzasoft.games.spring.repositories.hearts.HeartsRoundHandRepository;
import com.codenzasoft.games.spring.repositories.hearts.HeartsRoundPassRepository;
import com.codenzasoft.games.spring.repositories.hearts.HeartsTrickCardRepository;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HeartsPersistenceService implements HeartsListener {
  private static final Logger log = LoggerFactory.getLogger(HeartsPersistenceService.class);

  @Autowired private HeartsGameRepository heartsGameRepository;
  @Autowired private HeartsRoundHandRepository handRepository;
  @Autowired private HeartsRoundPassRepository passRepository;
  @Autowired private HeartsTrickCardRepository trickRepository;

  @Override
  @Transactional
  public void roundStarted(final HeartsGame game, final HeartsState state) {
    final RoundState round = state.getCurrentRound().orElseThrow();
    final Map<String, UUID> nameToId =
        game.getPlayers().stream().collect(Collectors.toMap(Player::name, Player::id));

    final List<HeartsRoundHandEntity> rows = new ArrayList<>();
    round
        .deal()
        .forEach(
            (playerName, cards) -> {
              final UUID userId = nameToId.get(playerName);
              cards.forEach(
                  card ->
                      rows.add(
                          new HeartsRoundHandEntity(
                              game.getId(),
                              round.number(),
                              userId,
                              card.getRank().name(),
                              card.getSuit().name())));
            });

    handRepository.saveAll(rows);
    log.debug(
        "Persisted {} hand rows for game {} round {}", rows.size(), game.getId(), round.number());
  }

  @Override
  @Transactional
  public void passSelected(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> cards) {
    final int roundNumber = state.getCurrentRound().map(RoundState::number).orElseThrow();
    final List<HeartsRoundPassEntity> rows =
        cards.stream()
            .map(
                card ->
                    new HeartsRoundPassEntity(
                        game.getId(),
                        roundNumber,
                        player.id(),
                        card.getRank().name(),
                        card.getSuit().name()))
            .toList();
    passRepository.saveAll(rows);
    log.debug(
        "Persisted {} pass rows for game {} round {} player {}",
        rows.size(),
        game.getId(),
        roundNumber,
        player.name());
  }

  @Override
  @Transactional
  public void cardPlayed(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Card card,
      final int cardNumber) {
    final RoundState round = state.getCurrentRound().orElseThrow();
    final int trickNumber = state.getCurrentTrick().map(TrickState::number).orElseThrow();
    trickRepository.save(
        new HeartsTrickCardEntity(
            game.getId(),
            round.number(),
            trickNumber,
            cardNumber,
            player.id(),
            card.getRank().name(),
            card.getSuit().name()));
    log.debug(
        "Persisted trick card for game {} round {} trick {} play_order {}",
        game.getId(),
        round.number(),
        trickNumber,
        cardNumber);
  }

  @Override
  public void gameStarted(final HeartsGame game, final HeartsState state) {}

  @Override
  @Transactional
  public void gameOver(final HeartsGame game, final HeartsState state) {
    final Optional<HeartsGameEntity> maybeGame = heartsGameRepository.findById(game.getId());
    if (maybeGame.isPresent()) {
      final HeartsGameEntity gameEntity = maybeGame.get();
      gameEntity.setOverAt(new Date());
      heartsGameRepository.save(gameEntity);
    }
  }

  @Override
  @Transactional
  public void gameQuit(final HeartsGame game, final HeartsState state) {
    final Optional<HeartsGameEntity> maybeGame = heartsGameRepository.findById(game.getId());
    if (maybeGame.isPresent()) {
      final HeartsGameEntity gameEntity = maybeGame.get();
      gameEntity.setQuitAt(new Date());
      heartsGameRepository.save(gameEntity);
    }
  }

  @Override
  public void roundEnded(final HeartsGame game, final HeartsState state) {}

  @Override
  public void trickStarted(final HeartsGame game, final HeartsState state) {}

  @Override
  public void trickEnded(final HeartsGame game, final HeartsState state) {}

  @Override
  public void passComplete(final HeartsGame game, final HeartsState state) {}

  /**
   * Reconstructs a {@link HeartsState} from persisted rows. Returns {@link Optional#empty()} if no
   * state has been persisted yet (i.e., the game has not started its first round).
   *
   * @param gameId The game to restore.
   * @param players The players of the game, in seat order.
   * @return The restored state, or empty if no persisted data exists.
   */
  public Optional<HeartsState> reconstructState(final UUID gameId, final List<UserEntity> players) {
    if (!handRepository.existsByGameId(gameId)) {
      return Optional.empty();
    }

    final Map<UUID, String> idToName =
        players.stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
    final List<String> playerNames = players.stream().map(UserEntity::getUsername).toList();

    final int maxRound = handRepository.findMaxRoundNumberByGameId(gameId);
    final List<RoundState> rounds = new ArrayList<>();
    for (int roundNum = 1; roundNum <= maxRound; roundNum++) {
      rounds.add(buildRoundState(gameId, roundNum, playerNames, idToName));
    }

    log.debug("Reconstructed {} rounds for game {}", rounds.size(), gameId);
    final List<RoundState> immutableRounds = Collections.unmodifiableList(rounds);
    final boolean isOver =
        heartsGameRepository.findById(gameId).map(e -> e.isOver() || e.isQuit()).orElse(false);
    return Optional.of(new HeartsState(playerNames, immutableRounds, isOver, null));
  }

  private RoundState buildRoundState(
      UUID gameId, int roundNum, List<String> playerNames, Map<UUID, String> idToName) {
    // Pass direction cycles LEFT → RIGHT → ACROSS → KEEPER → LEFT …
    Pass pass = Pass.LEFT;
    for (int i = 1; i < roundNum; i++) {
      pass = pass.nextPass();
    }

    // Dealer rotates one seat left each round; round 1 dealer is seat 0
    final String dealer = playerNames.get((roundNum - 1) % playerNames.size());

    final List<HeartsRoundHandEntity> handEntities =
        handRepository.findByGameIdAndRoundNumber(gameId, roundNum);
    final Map<String, List<Card>> deal = buildCardMap(handEntities, idToName, playerNames);

    final List<HeartsRoundPassEntity> passEntities =
        passRepository.findByGameIdAndRoundNumber(gameId, roundNum);
    final Map<String, List<Card>> passMap = buildPassMap(passEntities, idToName);

    final List<HeartsTrickCardEntity> trickEntities =
        trickRepository.findByGameIdAndRoundNumberOrderByTrickNumberAscPlayOrderAsc(
            gameId, roundNum);
    final List<TrickState> tricks = buildTrickStates(trickEntities, idToName);

    final boolean passComplete = pass == Pass.KEEPER || !tricks.isEmpty();

    final Map<String, List<Card>> hands =
        computeHands(playerNames, deal, passMap, passComplete, pass, tricks);

    return new RoundState(
        roundNum,
        pass,
        dealer,
        Collections.unmodifiableMap(deal),
        Collections.unmodifiableMap(passMap),
        passComplete,
        Collections.unmodifiableList(tricks),
        Collections.unmodifiableMap(hands));
  }

  private Map<String, List<Card>> buildCardMap(
      List<HeartsRoundHandEntity> entities, Map<UUID, String> idToName, List<String> playerNames) {
    final Map<String, List<Card>> result = new HashMap<>();
    playerNames.forEach(p -> result.put(p, new ArrayList<>()));
    for (final HeartsRoundHandEntity e : entities) {
      final String name = idToName.get(e.getUserId());
      if (name != null) {
        result
            .get(name)
            .add(new Card(Suit.valueOf(e.getCardSuit()), Rank.valueOf(e.getCardRank())));
      }
    }
    return result;
  }

  private Map<String, List<Card>> buildPassMap(
      List<HeartsRoundPassEntity> entities, Map<UUID, String> idToName) {
    final Map<String, List<Card>> result = new HashMap<>();
    for (final HeartsRoundPassEntity e : entities) {
      final String name = idToName.get(e.getUserId());
      if (name != null) {
        result
            .computeIfAbsent(name, k -> new ArrayList<>())
            .add(new Card(Suit.valueOf(e.getCardSuit()), Rank.valueOf(e.getCardRank())));
      }
    }
    return result;
  }

  private List<TrickState> buildTrickStates(
      List<HeartsTrickCardEntity> entities, Map<UUID, String> idToName) {
    if (entities.isEmpty()) return List.of();

    final Map<Integer, List<HeartsTrickCardEntity>> byTrick =
        entities.stream().collect(Collectors.groupingBy(HeartsTrickCardEntity::getTrickNumber));

    return byTrick.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry -> {
              final List<PlayedCard> playedCards =
                  entry.getValue().stream()
                      .sorted(Comparator.comparingInt(HeartsTrickCardEntity::getPlayOrder))
                      .map(
                          tc ->
                              new PlayedCard(
                                  idToName.get(tc.getUserId()),
                                  new Card(
                                      Suit.valueOf(tc.getCardSuit()),
                                      Rank.valueOf(tc.getCardRank()))))
                      .toList();
              return new TrickState(entry.getKey(), playedCards.get(0).player(), playedCards);
            })
        .toList();
  }

  private Map<String, List<Card>> computeHands(
      List<String> playerNames,
      Map<String, List<Card>> deal,
      Map<String, List<Card>> passMap,
      boolean passComplete,
      Pass pass,
      List<TrickState> tricks) {
    final Map<String, List<Card>> hands = new HashMap<>();
    deal.forEach((p, cards) -> hands.put(p, new ArrayList<>(cards)));

    if (passComplete && pass != Pass.KEEPER && !passMap.isEmpty()) {
      for (final String player : playerNames) {
        final List<Card> theirPass = passMap.get(player);
        if (theirPass == null) continue;
        final int idx = playerNames.indexOf(player);
        final int recipientIdx = Math.floorMod(idx + pass.getOffset(), playerNames.size());
        final String recipient = playerNames.get(recipientIdx);
        hands.get(player).removeAll(theirPass);
        hands.get(recipient).addAll(theirPass);
      }
    }

    for (final TrickState trick : tricks) {
      for (final PlayedCard pc : trick.cards()) {
        final List<Card> hand = hands.get(pc.player());
        if (hand != null) hand.remove(pc.card());
      }
    }

    final Map<String, List<Card>> finalHands = new HashMap<>();
    hands.forEach((p, cards) -> finalHands.put(p, Collections.unmodifiableList(cards)));
    return finalHands;
  }
}
