package com.codenzasoft.games.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession
public class SessionConfig {

  @Bean
  public CookieSerializer cookieSerializer() {
    final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieMaxAge(1800);
    serializer.setSameSite("Lax");
    return serializer;
  }
}
