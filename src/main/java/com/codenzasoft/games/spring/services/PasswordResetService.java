package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.PasswordResetTokenEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.PasswordResetTokenRepository;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

  @Value("${mailgun.api-key:}")
  private String apiKey;

  @Value("${mailgun.domain:}")
  private String domain;

  @Value("${mailgun.from:}")
  private String from;

  @Autowired private UsersRepository usersRepository;
  @Autowired private PasswordResetTokenRepository tokenRepository;

  private final RestClient restClient = RestClient.create();

  @Transactional
  public void initiateReset(UserEntity user, String resetBaseUrl) {
    tokenRepository.deleteByUserId(user.getId());
    tokenRepository.deleteExpiredBefore(Instant.now());

    final UUID token = UUID.randomUUID();
    tokenRepository.save(
        new PasswordResetTokenEntity(token, user.getId(), Instant.now().plus(TOKEN_VALIDITY)));

    final String link = resetBaseUrl + "?token=" + token;
    sendEmail(
        user.getEmail(),
        "Password reset",
        "Click the link below to reset your Codenza password."
            + " This link expires in 30 minutes and can only be used once.\n\n"
            + link
            + "\n\nIf you didn't request a password reset, you can ignore this email.");
  }

  /** Returns the user associated with {@code token} if it is valid and not expired. */
  public Optional<UserEntity> validateToken(String token) {
    UUID uuid;
    try {
      uuid = UUID.fromString(token);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    return tokenRepository
        .findByTokenAndExpiresAtAfter(uuid, Instant.now())
        .flatMap(entry -> usersRepository.findById(entry.getUserId()));
  }

  @Transactional
  public void consumeToken(String token) {
    try {
      tokenRepository.deleteById(UUID.fromString(token));
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void sendEmail(String to, String subject, String text) {
    if (apiKey == null || apiKey.isBlank() || domain == null || domain.isBlank()) {
      log.warn("[PasswordReset] Mailgun not configured — would have sent to {}: {}", to, text);
      return;
    }
    try {
      final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("from", from);
      body.add("to", to);
      body.add("subject", subject);
      body.add("text", text);

      restClient
          .post()
          .uri("https://api.mailgun.net/v3/" + domain + "/messages")
          .headers(h -> h.setBasicAuth("api", apiKey))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(body)
          .retrieve()
          .toBodilessEntity();

      log.info("[PasswordReset] Reset email sent to {}", to);
    } catch (Exception e) {
      log.error("[PasswordReset] Failed to send reset email to {}: {}", to, e.getMessage());
    }
  }
}
