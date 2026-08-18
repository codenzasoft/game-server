package com.codenzasoft.games.spring.services.kh;

import com.codenzasoft.games.kingshand.EffectTarget;
import com.codenzasoft.games.kingshand.GameEngine;
import com.codenzasoft.games.kingshand.GameState;
import com.codenzasoft.games.kingshand.KhObserver;
import com.codenzasoft.games.kingshand.PlayerState;
import com.codenzasoft.games.spring.entities.kh.KhGameStateEntity;
import com.codenzasoft.games.spring.repositories.kh.KhGameStateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Durable mid-game persistence for King's Hand: serializes the whole immutable {@link GameState} to
 * the {@code kh_game_states} table so a match survives cache eviction, redeploy, and scale-to-zero.
 *
 * <p>Registered on each {@link GameEngine} as a {@link KhObserver} (alongside the broadcast
 * service), so saves are engine-driven — every {@code gameStarted}/{@code gameUpdated}/{@code
 * gameOver} event persists the state snapshot the engine hands to the callback. The controller
 * never calls this directly. Note the engine dispatches these callbacks on a shared thread pool, so
 * two rapid updates to the same match may persist out of order; each save is a full-state upsert,
 * so the store self-heals on the next event, and the single-owner deployment (max-instances=1)
 * rules out cross-instance races.
 *
 * <p>Serialization uses a <em>dedicated</em> {@link ObjectMapper} — not the shared Spring/MVC one —
 * configured to serialize the record graph by its components only. The KingsHand records expose
 * convenience helper getters ({@code getBoard()}, {@code getDrawPile()}, {@code isGameOver()}, …)
 * that are <em>derived</em> from the record components; {@code
 * INFER_RECORD_GETTERS_FROM_COMPONENTS_ONLY} keeps the JSON to exactly the record components.
 * {@code EffectTarget} is a sealed interface, so a mix-in adds polymorphic type info on the
 * persistence mapper only (the client wire is read-only and needs none).
 *
 * <p><b>Transient display state is dropped.</b> Each kingdom's {@code resolutionLog} — the per-card
 * animation for the just-resolved PLAY — is <em>not</em> persisted: it is cleared before
 * serialization and comes back empty. The authoritative resource outcomes already live in each
 * kingdom's {@code ResourceState}, so a resumed match is correct; the only visible cost is that a
 * reload during the brief resolution screen won't replay the card-by-card animation. Dropping it
 * also sidesteps {@code ResolutionStep}'s client-wire Jackson mapping (its {@code delta} is
 * {@code @JsonIgnore}d in favour of six flattened getters), which does not round-trip. See {@code
 * GameStateSerializationSpec}.
 */
@Service
public class KhPersistenceService implements KhObserver {

  private static final Logger log = LoggerFactory.getLogger(KhPersistenceService.class);

  /** Record-graph mapper: components only, tolerant of unknown fields on read. */
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(MapperFeature.INFER_RECORD_GETTERS_FROM_COMPONENTS_ONLY)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .addMixIn(EffectTarget.class, EffectTargetPersistenceMixin.class)
          .build();

  private final KhGameStateRepository repository;

  public KhPersistenceService(final KhGameStateRepository repository) {
    this.repository = repository;
  }

  /**
   * Serializes a {@link GameState} to its persisted JSON form (transient display state dropped).
   */
  public static String serialize(final GameState state) {
    try {
      return MAPPER.writeValueAsString(stripTransientDisplayState(state));
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to serialize KH GameState", e);
    }
  }

  /**
   * Clears each kingdom's per-phase resolution log — display-only state we deliberately don't
   * store.
   */
  private static GameState stripTransientDisplayState(final GameState state) {
    GameState stripped = state;
    for (int seat = 0; seat < stripped.playerCount(); seat++) {
      final PlayerState player = stripped.getPlayer(seat);
      if (!player.getResolutionLog().isEmpty()) {
        stripped = stripped.withPlayer(seat, player.withResolutionLog(List.of()));
      }
    }
    return stripped;
  }

  /** Reconstructs a {@link GameState} from its persisted JSON form. */
  public static GameState deserialize(final String json) {
    try {
      return MAPPER.readValue(json, GameState.class);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to deserialize KH GameState", e);
    }
  }

  // ----- KhObserver: engine-driven durable saves -----

  @Override
  public void gameStarted(final GameEngine game, final GameState state) {
    save(game.getId(), state);
  }

  @Override
  public void gameUpdated(final GameEngine game, final GameState state) {
    save(game.getId(), state);
  }

  @Override
  public void gameOver(final GameEngine game, final GameState state) {
    save(game.getId(), state);
  }

  /** Upserts the current match state for {@code gameId}. */
  public void save(final UUID gameId, final GameState state) {
    final KhGameStateEntity entity =
        repository
            .findById(gameId)
            .map(
                existing -> {
                  existing.setState(serialize(state));
                  return existing;
                })
            .orElseGet(() -> new KhGameStateEntity(gameId, serialize(state)));
    repository.save(entity);
    log.debug("Persisted KH game state for {}", gameId);
  }

  /** Returns the persisted match state for {@code gameId}, or empty if none has been saved. */
  public Optional<GameState> reconstruct(final UUID gameId) {
    return repository.findById(gameId).map(entity -> deserialize(entity.getState()));
  }
}
