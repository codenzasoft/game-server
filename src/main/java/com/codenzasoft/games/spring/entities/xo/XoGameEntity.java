package com.codenzasoft.games.spring.entities.xo;

import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.services.GameType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@DiscriminatorValue(XoGameEntity.XO_GAME_TYPE)
public class XoGameEntity extends GameEntity {

  static final String XO_GAME_TYPE = "XO";

  @OneToMany(
      mappedBy = "gameId",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      fetch = FetchType.EAGER)
  List<XoPieceEntity> gamePieces = new ArrayList<>();

  @OneToMany(
      mappedBy = "gameId",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      fetch = FetchType.EAGER)
  @OrderColumn(name = "move_order")
  List<XoGameMoveEntity> gameMoves = new ArrayList<>();

  public XoGameEntity() {}

  public XoGameEntity(final UUID id) {
    super(id, GameType.XO);
  }

  @JsonProperty("gamePieces")
  public List<XoPieceEntity> getGamePieces() {
    return gamePieces;
  }

  public void setGamePieces(final List<XoPieceEntity> gamePieces) {
    this.gamePieces = new ArrayList<>(gamePieces);
  }

  public List<XoGameMoveEntity> getGameMoves() {
    return gameMoves;
  }

  public void setGameMoves(final List<XoGameMoveEntity> gameMoves) {
    this.gameMoves = new ArrayList<>(gameMoves);
  }

  public void addGameMove(final XoGameMoveEntity gameMove) {
    gameMoves.add(gameMove);
  }
}
