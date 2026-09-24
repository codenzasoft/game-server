package com.codenzasoft.games.spring.config.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Optional;

abstract class TrickStateMixin {

  @JsonProperty("playedCards")
  abstract Object cards();

  @JsonIgnore
  abstract boolean isComplete();

  @JsonProperty("trump")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  abstract Object trump();

  @JsonIgnore
  abstract Optional<?> getTrump();

  @JsonProperty("winner")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  abstract Object winner();

  @JsonIgnore
  abstract Optional<?> getWinner();

  @JsonProperty("score")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  abstract Map<String, Integer> getScore();
}
