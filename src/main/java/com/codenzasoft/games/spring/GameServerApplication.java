package com.codenzasoft.games.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
public class GameServerApplication {

  public static void main(final String[] args) {
    SpringApplication.run(GameServerApplication.class, args);
  }

  /**
   * An {@link java.util.concurrent.Executor} used to broadcast notifications from games to allow
   * game listeners to process events asynchronously.
   *
   * @return A {@link ThreadPoolTaskExecutor}
   */
  @Bean
  ThreadPoolTaskExecutor gameNotificationExecutor() {
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setCorePoolSize(5);
    taskExecutor.setMaxPoolSize(10);
    taskExecutor.setQueueCapacity(25);
    return taskExecutor;
  }
}
