package com.codenzasoft.games.spring.services.kh;

import com.codenzasoft.games.kingshand.CardTarget;
import com.codenzasoft.games.kingshand.EffectTarget;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.KhObserver;
import com.codenzasoft.games.kingshand.ResourceTarget;
import com.codenzasoft.games.spring.entities.kh.KhGameStateEntity;
import com.codenzasoft.games.spring.repositories.GamesRepository;
import com.codenzasoft.games.spring.repositories.kh.KhGameStateRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Persists King's Hand state as a single JSON snapshot per game (GAME-81), in contrast to Hearts'
 * granular per-round/trick row model. Registered as a {@link KhObserver} for the
 * game-started/game-over hooks; {@link #save(GameEngine)} is also called directly by {@code
 * KhController} after every mutating move, since {@link KhObserver} has no per-move hook.
 */
@Service
public class KhPersistenceService implements KhObserver {

  private static final Logger log = LoggerFactory.getLogger(KhPersistenceService.class);

  @Autowired private KhGameStateRepository khGameStateRepository;
  @Autowired private GamesRepository gamesRepository;

  // Dedicated mapper (isolated from the production wire-format mapper used for WS broadcasts) so
  // the EffectTarget deserializer below only ever applies to KH persistence reads/writes.
  private final ObjectMapper objectMapper = buildObjectMapper();

  private static ObjectMapper buildObjectMapper() {
    final SimpleModule module = new SimpleModule();
    module.addDeserializer(EffectTarget.class, new EffectTargetDeserializer());
    return new ObjectMapper().registerModule(module);
  }

  @Override
  @Transactional
  public void gameStarted(final GameEngine game, final GameState state) {
    save(game.getId(), state);
  }

  @Override
  @Transactional
  public void gameOver(final GameEngine game, final GameState state) {
    save(game.getId(), state);
    gamesRepository
        .findById(game.getId())
        .ifPresent(
            entity -> {
              entity.setOverAt(new Date());
              gamesRepository.save(entity);
            });
  }

  /** Persists the given engine's current state as the single JSON snapshot for its game. */
  @Transactional
  public void save(final GameEngine game) {
    save(game.getId(), game.getState());
  }

  private void save(final UUID gameId, final GameState state) {
    try {
      final String json = objectMapper.writeValueAsString(state);
      khGameStateRepository.save(new KhGameStateEntity(gameId, json));
      log.debug("Persisted KH state snapshot for game {}", gameId);
    } catch (final Exception e) {
      log.error("Failed to persist KH state for game {}: {}", gameId, e.getMessage(), e);
    }
  }

  /**
   * Reconstructs a {@link GameState} from its persisted JSON snapshot.
   *
   * @param gameId The game to restore.
   * @return The restored state, or {@link Optional#empty()} if no snapshot has been persisted yet.
   */
  public Optional<GameState> reconstructState(final UUID gameId) {
    final Optional<KhGameStateEntity> persisted = khGameStateRepository.findById(gameId);
    if (persisted.isEmpty()) {
      return Optional.empty();
    }
    try {
      final GameState state = objectMapper.readValue(persisted.get().getState(), GameState.class);
      log.debug("Reconstructed KH state for game {}", gameId);
      return Optional.of(state);
    } catch (final Exception e) {
      log.error("Failed to deserialize KH state for game {}: {}", gameId, e.getMessage(), e);
      return Optional.empty();
    }
  }

  /**
   * Deserializes the sealed {@link EffectTarget} hierarchy. Jackson cannot dispatch polymorphic
   * interfaces without a discriminator, so this inspects node shape instead: a plain string is a
   * {@link ResourceTarget} enum constant, anything else is a {@link CardTarget} object.
   */
  private static final class EffectTargetDeserializer extends StdDeserializer<EffectTarget> {
    private EffectTargetDeserializer() {
      super(EffectTarget.class);
    }

    @Override
    public EffectTarget deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final JsonNode node = p.getCodec().readTree(p);
      if (node.isTextual()) {
        return ResourceTarget.valueOf(node.asText());
      }
      return p.getCodec().treeToValue(node, CardTarget.class);
    }
  }
}
