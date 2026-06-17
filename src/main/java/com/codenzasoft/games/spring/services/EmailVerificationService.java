package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.EmailVerificationTokenEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.EmailVerificationTokenRepository;
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
public class EmailVerificationService {

  private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
  private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

  @Value("${mailgun.api-key:}")
  private String apiKey;

  @Value("${mailgun.domain:}")
  private String domain;

  @Value("${mailgun.from:}")
  private String from;

  @Autowired private UsersRepository usersRepository;
  @Autowired private EmailVerificationTokenRepository tokenRepository;

  private final RestClient restClient = RestClient.create();

  @Transactional
  public void sendVerification(UserEntity user, String verifyBaseUrl) {
    tokenRepository.deleteByUserId(user.getId());
    tokenRepository.deleteExpiredBefore(Instant.now());

    final UUID token = UUID.randomUUID();
    tokenRepository.save(
        new EmailVerificationTokenEntity(token, user.getId(), Instant.now().plus(TOKEN_VALIDITY)));

    final String link = verifyBaseUrl + "?token=" + token;
    sendEmail(
        user.getEmail(),
        "Verify your Codenza account",
        "Welcome to Codenza! Click the link below to verify your email address and activate your account."
            + " This link expires in 24 hours.\n\n"
            + link
            + "\n\nIf you didn't create an account, you can ignore this email.");
  }

  /** Returns the user if the token is valid and not expired; empty otherwise. */
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

  /** Marks the account as verified and deletes the token. */
  @Transactional
  public void verifyUser(UserEntity user, String token) {
    user.setEmailVerifiedAt(Instant.now());
    usersRepository.save(user);
    try {
      tokenRepository.deleteById(UUID.fromString(token));
    } catch (IllegalArgumentException ignored) {
    }
  }

  /** Returns true if a pending (unverified) verification token exists for this user. */
  public boolean hasPendingVerification(UserEntity user) {
    return tokenRepository.existsByUserId(user.getId());
  }

  private void sendEmail(String to, String subject, String text) {
    if (apiKey == null || apiKey.isBlank() || domain == null || domain.isBlank()) {
      log.warn("[EmailVerification] Mailgun not configured — would have sent to {}: {}", to, text);
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

      log.info("[EmailVerification] Verification email sent to {}", to);
    } catch (Exception e) {
      log.error(
          "[EmailVerification] Failed to send verification email to {}: {}", to, e.getMessage());
    }
  }
}
