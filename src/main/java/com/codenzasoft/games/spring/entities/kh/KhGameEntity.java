package com.codenzasoft.games.spring.entities.kh;

import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.spring.entities.GameEntity;
import com.codenzasoft.games.spring.entities.xo.XoGameMoveEntity;
import com.codenzasoft.games.spring.entities.xo.XoPieceEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@DiscriminatorValue(KhGameEntity.KH_GAME_TYPE)
public class KhGameEntity extends GameEntity {

  static final String KH_GAME_TYPE = "KH";

  public KhGameEntity() {}

  public KhGameEntity(final UUID id) {
    super(id, GameType.KH);
  }
}
