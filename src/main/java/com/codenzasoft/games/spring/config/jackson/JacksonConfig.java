package com.codenzasoft.games.spring.config.jackson;

import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

  @Bean
  public JacksonModule heartsMixins() {
    return new SimpleModule() {
      @Override
      public void setupModule(final SetupContext context) {
        context.setMixIn(HeartsState.class, HeartsStateMixin.class);
        context.setMixIn(RoundState.class, RoundStateMixin.class);
        context.setMixIn(TrickState.class, TrickStateMixin.class);
      }
    };
  }
}
