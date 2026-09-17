package com.codenzasoft.games.spring.config.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;

abstract class HeartsStateMixin {

  @JsonProperty("over")
  abstract boolean isOver();

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("quitBy")
  abstract String quitBy();

  @JsonProperty("quit")
  abstract boolean isQuit();

  @JsonProperty("score")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  abstract Map<String, Integer> getScore();

  @JsonProperty("winner")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  abstract String getWinner();

  @JsonProperty("tied")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  abstract List<String> getTied();

  @JsonIgnore
  abstract boolean isComplete();

  @JsonIgnore
  abstract Optional<?> getCurrentRound();

  @JsonIgnore
  abstract Optional<String> getNextPlayer();

  @JsonIgnore
  abstract Object getRound();

  @JsonIgnore
  abstract Optional<?> getCurrentTrick();
}
