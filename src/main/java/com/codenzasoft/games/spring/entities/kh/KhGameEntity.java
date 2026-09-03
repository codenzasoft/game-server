package com.codenzasoft.games.spring.entities.kh;

import com.codenzasoft.games.spring.GameType;
import com.codenzasoft.games.spring.entities.GameEntity;
import jakarta.persistence.*;
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
