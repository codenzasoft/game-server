package com.codenzasoft.games.spring.services

import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import com.codenzasoft.games.hearts.HeartsGame
import com.codenzasoft.games.hearts.HeartsState
import com.codenzasoft.games.hearts.Pass
import com.codenzasoft.games.hearts.PlayedCard
import com.codenzasoft.games.hearts.RoundState
import com.codenzasoft.games.hearts.TrickState
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.spring.repositories.hearts.HeartsRoundHandRepository
import com.codenzasoft.games.spring.repositories.hearts.HeartsRoundPassRepository
import com.codenzasoft.games.spring.repositories.hearts.HeartsTrickCardRepository
import com.codenzasoft.games.spring.services.hearts.HeartsPersistenceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.util.concurrent.Executors

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HeartsPersistenceServiceSpec extends Specification {

    @Autowired HeartsPersistenceService heartsPersistenceService
    @Autowired GameService gameService
    @Autowired UsersRepository usersRepository
    @Autowired HeartsRoundHandRepository handRepository
    @Autowired HeartsRoundPassRepository passRepository
    @Autowired HeartsTrickCardRepository trickRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        jdbcTemplate.execute("DELETE FROM hearts_tricks")
        jdbcTemplate.execute("DELETE FROM hearts_round_passes")
        jdbcTemplate.execute("DELETE FROM hearts_round_hands")
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")

        testUser = new UserEntity()
        testUser.setUserName("hearts-persist-user")
        testUser = usersRepository.save(testUser)
    }

    // ── roundStarted ─────────────────────────────────────────────────────────

    def "roundStarted persists 52 hand rows for a new round"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def state = stateWithDeal(entity.players*.username, buildDeal(entity.players*.username), 1, Pass.LEFT)

        when:
        heartsPersistenceService.roundStarted(game, state)

        then:
        handRepository.findByGameIdAndRoundNumber(entity.id, 1).size() == 52
    }

    def "roundStarted persists 13 cards per player"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username
        def deal = buildDeal(playerNames)
        def state = stateWithDeal(playerNames, deal, 1, Pass.LEFT)

        when:
        heartsPersistenceService.roundStarted(game, state)

        then:
        playerNames.every { name ->
            def userId = entity.players.find { it.username == name }.id
            handRepository.findByGameIdAndRoundNumber(entity.id, 1)
                .count { it.userId == userId } == 13
        }
    }

    // ── passSelected ──────────────────────────────────────────────────────────

    def "passSelected persists 3 pass rows for the player"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username
        def deal = buildDeal(playerNames)
        def state = stateWithDeal(playerNames, deal, 1, Pass.LEFT)
        def humanPlayer = entity.players.find { !it.isBot() }.toPlayer()
        def passCards = deal[humanPlayer.name()].take(3)

        when:
        heartsPersistenceService.passSelected(game, state, humanPlayer, passCards)

        then:
        passRepository.findByGameIdAndRoundNumber(entity.id, 1).size() == 3
    }

    // ── cardPlayed ────────────────────────────────────────────────────────────

    def "cardPlayed persists a trick row with the correct play_order"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username
        def deal = buildDeal(playerNames)
        def trick = new TrickState(1, playerNames[0], [])
        def round = new RoundState(1, Pass.LEFT, playerNames[0], deal, [:], true, [trick], deal)
        def state = new HeartsState(playerNames, [round], false, null)
        def player = entity.players[0].toPlayer()
        def card = deal[playerNames[0]][0]

        when:
        heartsPersistenceService.cardPlayed(game, state, player, card, 1)

        then:
        def rows = trickRepository.findByGameIdAndRoundNumberOrderByTrickNumberAscPlayOrderAsc(entity.id, 1)
        rows.size() == 1
        rows[0].trickNumber == 1
        rows[0].playOrder == 1
        rows[0].userId == player.id()
        rows[0].cardRank == card.rank.name()
        rows[0].cardSuit == card.suit.name()
    }

    // ── reconstructState ─────────────────────────────────────────────────────

    def "reconstructState returns empty when no hands have been persisted"() {
        given:
        def entity = gameService.createHeartsGame(testUser)

        when:
        def result = heartsPersistenceService.reconstructState(entity.id, entity.players)

        then:
        result.isEmpty()
    }

    def "reconstructState returns the persisted round with correct player count and hand sizes"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username
        def deal = buildDeal(playerNames)
        def state = stateWithDeal(playerNames, deal, 1, Pass.LEFT)

        heartsPersistenceService.roundStarted(game, state)

        when:
        def result = heartsPersistenceService.reconstructState(entity.id, entity.players)

        then:
        result.isPresent()
        def restored = result.get()
        restored.players() == playerNames
        restored.rounds().size() == 1
        def round = restored.getCurrentRound().get()
        round.number() == 1
        round.pass() == Pass.LEFT
        round.dealer() == playerNames[0]
        // hands should equal original deal (no tricks played yet, pass not complete)
        playerNames.every { name -> round.hands().get(name).size() == 13 }
    }

    def "reconstructState reconstructs correct hand sizes after tricks are played"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username
        def deal = buildDeal(playerNames)
        def state = stateWithDeal(playerNames, deal, 1, Pass.LEFT)
        heartsPersistenceService.roundStarted(game, state)

        // Simulate one complete trick (4 cards played, one from each player at positions 1-4)
        def trickCards = playerNames.collect { name -> deal[name][0] }
        def playedCards = playerNames.withIndex().collect { name, i ->
            new PlayedCard(name, trickCards[i])
        }
        def trick = new TrickState(1, playerNames[0], playedCards)
        def stateAfterTrick = new HeartsState(playerNames, [
            new RoundState(1, Pass.LEFT, playerNames[0], deal, [:], true, [trick], deal)
        ], false, null)

        playerNames.eachWithIndex { name, i ->
            def player = entity.players.find { it.username == name }.toPlayer()
            heartsPersistenceService.cardPlayed(game, stateAfterTrick, player, trickCards[i], i + 1)
        }

        when:
        def result = heartsPersistenceService.reconstructState(entity.id, entity.players)

        then:
        result.isPresent()
        def round = result.get().getCurrentRound().get()
        // One card played per player → 12 remaining each
        playerNames.every { name -> round.hands().get(name).size() == 12 }
    }

    def "reconstructState pass direction cycles correctly across rounds"() {
        given:
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username

        // Persist hands for 4 rounds to exercise the full pass cycle
        [Pass.LEFT, Pass.RIGHT, Pass.ACROSS, Pass.KEEPER].eachWithIndex { expectedPass, i ->
            def roundNum = i + 1
            def deal = buildDeal(playerNames)
            // Build a complete round (all 52 cards played) for rounds 1-3 so round 4 can be current
            if (roundNum < 4) {
                def completedTricks = buildCompletedTricks(playerNames, deal)
                def completedRound = new RoundState(roundNum, expectedPass, playerNames[(roundNum - 1) % 4],
                    deal, [:], true, completedTricks, emptyHands(playerNames))
                def completedState = new HeartsState(playerNames, [completedRound], false, null)
                heartsPersistenceService.roundStarted(game, completedState)
                completedTricks.each { trick ->
                    trick.cards().each { pc ->
                        def player = entity.players.find { it.username == pc.player() }.toPlayer()
                        heartsPersistenceService.cardPlayed(game, completedState, player, pc.card(),
                            trick.cards().indexOf(pc) + 1)
                    }
                }
            } else {
                def state = stateWithDeal(playerNames, deal, roundNum, expectedPass)
                heartsPersistenceService.roundStarted(game, state)
            }
        }

        when:
        def result = heartsPersistenceService.reconstructState(entity.id, entity.players)

        then:
        result.isPresent()
        result.get().rounds().size() == 4
        result.get().rounds()[0].pass() == Pass.LEFT
        result.get().rounds()[1].pass() == Pass.RIGHT
        result.get().rounds()[2].pass() == Pass.ACROSS
        result.get().rounds()[3].pass() == Pass.KEEPER
    }

    // ── reconstructState: isOver flag ────────────────────────────────────────

    def "reconstructState sets isOver true when cumulative score reaches 100"() {
        given: """
            Four complete rounds where player 0 holds all clubs and leads every trick,
            winning all 13 tricks each round. Player 3 holds all hearts (13 pts) and
            player 2 holds Q♠ (13 pts), so player 0 takes 26 pts per round — a moon-shot
            that scores 0 for player 0 and 26 for each other. After 4 rounds the others
            reach 104 pts ≥ 100, so the game is over.
        """
        def entity = gameService.createHeartsGame(testUser)
        def game = dummyGame(entity)
        def playerNames = entity.players*.username

        def pass = Pass.LEFT
        (1..4).each { roundNum ->
            def deal = buildDeal(playerNames)
            persistRoundWithAllTricks(game, entity, playerNames, deal, roundNum, pass)
            pass = pass.nextPass()
        }

        when:
        def result = heartsPersistenceService.reconstructState(entity.id, entity.players)

        then: "the reconstructed state is present and marked over"
        result.isPresent()
        result.get().isOver()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private HeartsGame dummyGame(def entity) {
        def players = entity.players.collect { it.toPlayer() }
        def playerNames = players*.name()
        def emptyState = new HeartsState(playerNames, [], false, null)
        return new HeartsGame.Builder()
            .withId(entity.id)
            .withPlayers(players)
            .withRestoredState(emptyState)
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .build()
    }

    private HeartsState stateWithDeal(List<String> playerNames, Map<String, List<Card>> deal,
                                       int roundNumber, Pass pass) {
        def round = new RoundState(roundNumber, pass, playerNames[(roundNumber - 1) % playerNames.size()],
            deal, [:], false, [], deal)
        return new HeartsState(playerNames, [round], false, null)
    }

    /** Deals all 52 cards evenly across the given players. */
    private Map<String, List<Card>> buildDeal(List<String> playerNames) {
        def allCards = Suit.values().collectMany { suit ->
            Rank.values().collect { rank -> new Card(suit, rank) }
        }
        def deal = [:]
        playerNames.eachWithIndex { name, i ->
            deal[name] = new ArrayList<>(allCards.subList(i * 13, (i + 1) * 13))
        }
        return deal
    }

    /** Builds 13 complete tricks that account for all dealt cards. */
    private List<TrickState> buildCompletedTricks(List<String> playerNames, Map<String, List<Card>> deal) {
        (0..<13).collect { i ->
            def playedCards = playerNames.withIndex().collect { name, j ->
                new PlayedCard(name, deal[name][i])
            }
            new TrickState(i + 1, playerNames[0], playedCards)
        }
    }

    private Map<String, List<Card>> emptyHands(List<String> playerNames) {
        playerNames.collectEntries { [it, []] }
    }

    /**
     * Persists a complete round: 52 hand rows via roundStarted, then all 13 tricks via
     * cardPlayed. Each cardPlayed call receives a state whose current trick is the trick
     * being persisted, so trickNumber is recorded correctly in the DB.
     */
    private void persistRoundWithAllTricks(def game, def entity, List<String> playerNames,
                                           Map<String, List<Card>> deal, int roundNum, Pass pass) {
        def dealer = playerNames[(roundNum - 1) % playerNames.size()]
        def startRound = new RoundState(roundNum, pass, dealer, deal, [:], true, [], deal)
        def startState = new HeartsState(playerNames, [startRound], false, null)
        heartsPersistenceService.roundStarted(game, startState)

        def completedTricks = buildCompletedTricks(playerNames, deal)
        completedTricks.each { trick ->
            def trickState = new HeartsState(playerNames, [
                new RoundState(roundNum, pass, dealer, deal, [:], true, [trick], emptyHands(playerNames))
            ], false, null)
            trick.cards().eachWithIndex { pc, idx ->
                def player = entity.players.find { it.username == pc.player() }.toPlayer()
                heartsPersistenceService.cardPlayed(game, trickState, player, pc.card(), idx + 1)
            }
        }
    }
}
