package com.codenzasoft.games.spring.entities.xo;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.entities.ids.GamePlayerId;
import com.codenzasoft.games.xo.Piece;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.UUID;

@Entity(name = "XO_GAME_PIECES")
@IdClass(GamePlayerId.class)
public class XoPieceEntity {
  @Id
  @Column(nullable = false, name = "game_id")
  UUID gameId;

  @Id
  @Column(nullable = false, name = "user_id")
  UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, name = "piece")
  Piece piece;

  @OneToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  UserEntity user;

  public XoPieceEntity() {}

  public XoPieceEntity(final UUID gameId, final UUID userId, final Piece piece) {
    this.gameId = gameId;
    this.userId = userId;
    this.piece = piece;
  }

  @JsonProperty("gameId")
  public UUID getGameId() {
    return gameId;
  }

  @JsonProperty("userId")
  public UUID getUserId() {
    return userId;
  }

  @JsonProperty("piece")
  public Piece getPiece() {
    return piece;
  }

  @JsonIgnore
  public UserEntity getUser() {
    return user;
  }
}
