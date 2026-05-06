package com.codenzasoft.games.spring.entities.xando;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.UUID;

@Entity(name = "XO_GAME_MOVES")
@IdClass(XoGameMoveId.class)
public class XoGameMoveEntity {
  @Id
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Id
  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Id
  @Column(nullable = false, name = "move_order")
  int moveOrder;

  @Column(nullable = false, name = "move_row")
  int moveRow;

  @Column(nullable = false, name = "move_column")
  int moveColumn;

  @OneToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  UserEntity user;

  public XoGameMoveEntity() {}

  public XoGameMoveEntity(
      final UUID gameId,
      final UUID userId,
      final int moveOrder,
      final int moveRow,
      final int moveColumn) {
    this.gameId = gameId;
    this.userId = userId;
    this.moveOrder = moveOrder;
    this.moveRow = moveRow;
    this.moveColumn = moveColumn;
  }

  @JsonProperty("gameId")
  public UUID getGameId() {
    return gameId;
  }

  @JsonProperty("userId")
  public UUID getUserId() {
    return userId;
  }

  @JsonProperty("moveOrder")
  public int getMoveOrder() {
    return moveOrder;
  }

  @JsonProperty("moveRow")
  public int getMoveRow() {
    return moveRow;
  }

  @JsonProperty("moveColumn")
  public int getMoveColumn() {
    return moveColumn;
  }

  @JsonIgnore
  public UserEntity getUser() {
    return user;
  }
}
