package com.codenzasoft.games.spring.config.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;

abstract class RoundStateMixin {

  @JsonIgnore
  abstract Map<?, ?> deal();

  @JsonIgnore
  abstract Map<?, ?> passes();

  @JsonIgnore
  abstract Map<?, ?> hands();

  @JsonProperty("complete")
  abstract boolean isComplete();

  @JsonProperty("score")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  abstract Map<String, Integer> getScore();

  @JsonIgnore
  abstract boolean isPassReady();

  @JsonIgnore
  abstract boolean isPassComplete();

  @JsonIgnore
  abstract Optional<?> getCurrentTrick();

  @JsonIgnore
  abstract Object getTrick();

  @JsonIgnore
  abstract List<?> getPlayedCards();

  @JsonIgnore
  abstract List<?> getRemainingCards();
}
