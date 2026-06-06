package com.codenzasoft.games.spring.entities.hearts;

import com.codenzasoft.games.common.GameType;
import com.codenzasoft.games.spring.entities.GameEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@DiscriminatorValue(HeartsGameEntity.HEARTS_GAME_TYPE)
public class HeartsGameEntity extends GameEntity {

  static final String HEARTS_GAME_TYPE = "HEARTS";

  public HeartsGameEntity() {}

  public HeartsGameEntity(final UUID id) {
    super(id, GameType.HEARTS);
  }
}
