package com.codenzasoft.games.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("stomp-heartbeat-");
    scheduler.initialize();
    config
        .enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {25000, 25000})
        .setTaskScheduler(scheduler);
    config.setApplicationDestinationPrefixes("/games");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // TODO: all origins is probably insecure
    registry.addEndpoint("/games/connect").setAllowedOrigins("*");
  }
}
