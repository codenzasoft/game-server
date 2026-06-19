package com.codenzasoft.games.spring.entities.kh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.util.Date;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Single-row-per-game JSON snapshot of a King's Hand {@code GameState} (GAME-81). Overwritten on
 * every save rather than accumulating granular per-action rows, unlike Hearts' persistence model.
 */
@Entity(name = "KH_GAME_STATE")
public class KhGameStateEntity {

  @Id
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, name = "state")
  String state;

  @Column(name = "updated_at")
  Date updatedAt;

  public KhGameStateEntity() {}

  public KhGameStateEntity(final UUID gameId, final String state) {
    this.gameId = gameId;
    this.state = state;
  }

  @PrePersist
  @PreUpdate
  public void setUpdatedAt() {
    this.updatedAt = new Date();
  }

  public UUID getGameId() {
    return gameId;
  }

  public void setGameId(final UUID gameId) {
    this.gameId = gameId;
  }

  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  public Date getUpdatedAt() {
    return updatedAt;
  }
}
