package com.codenzasoft.games.spring.entities.kh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/**
 * Persisted mid-game state for one King's Hand match: the entire immutable {@code GameState}
 * serialized to JSON (see {@code KhPersistenceService}). Standalone entity — intentionally
 * <em>not</em> part of the {@code GameEntity} single-table inheritance, so KH-only state does not
 * add a null column to every XO/Hearts row. Keyed by {@code gameId}; the backing table's FK
 * cascades from {@code games}, so completed-game cleanup removes this row automatically.
 */
@Entity
@Table(name = "kh_game_states")
public class KhGameStateEntity {

  @Id
  @Column(name = "game_id")
  private UUID gameId;

  @ColumnTransformer(write = "?::jsonb")
  @Column(name = "state", columnDefinition = "jsonb", nullable = false)
  private String state;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public KhGameStateEntity() {}

  public KhGameStateEntity(final UUID gameId, final String state) {
    this.gameId = gameId;
    this.state = state;
  }

  @PrePersist
  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now();
  }

  public UUID getGameId() {
    return gameId;
  }

  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
