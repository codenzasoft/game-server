package com.codenzasoft.games.spring.config;

import com.codenzasoft.games.spring.services.GamesUserDetailsService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Defines the role hierarchy. Higher roles inherit every privilege of the roles below them, so
   * an admin can do anything a developer / user / bot can do, and so on. With this in place,
   * users only need to be assigned their highest role rather than every role they should
   * implicitly have. Spring Security 6.3+ auto-wires this bean into both URL-based and
   * method-based authorization.
   */
  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy(
        """
        ADMIN > DEVELOPER
        DEVELOPER > USER
        USER > BOT
        """);
  }

  @Bean
  public UserDetailsService userDetailsService() {
    return new GamesUserDetailsService();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    String idForEncode = "bcrypt";
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put(idForEncode, new BCryptPasswordEncoder());
    encoders.put("noop", NoOpPasswordEncoder.getInstance());
    encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put(
        "pbkdf2@SpringSecurity_v5_8", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put(
        "scrypt@SpringSecurity_v5_8", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put(
        "argon2@SpringSecurity_v5_8", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    encoders.put("sha256", new StandardPasswordEncoder());

    return new DelegatingPasswordEncoder(idForEncode, encoders);
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, UserDetailsService userDetailsService)
      throws Exception {
    http.authorizeHttpRequests(
        auth -> {
          auth.requestMatchers("/img/**")
              .permitAll()
              .requestMatchers("/login")
              .permitAll()
              .requestMatchers("/register")
              .permitAll()
              .requestMatchers("/admin/**")
              .hasAuthority("ADMIN")
              .anyRequest()
              .authenticated();
        });
    http.userDetailsService(userDetailsService);
    http.formLogin((form) -> form.loginPage("/login").permitAll());
    return http.build();
  }
}
