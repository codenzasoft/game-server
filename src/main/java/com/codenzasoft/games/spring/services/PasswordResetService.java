package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.UsersRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

  private record ResetEntry(UUID userId, Instant expiry) {}

  private final ConcurrentHashMap<String, ResetEntry> tokens = new ConcurrentHashMap<>();

  @Value("${mailgun.api-key:}")
  private String apiKey;

  @Value("${mailgun.domain:}")
  private String domain;

  @Value("${mailgun.from:}")
  private String from;

  @Autowired private UsersRepository usersRepository;

  private final RestClient restClient = RestClient.create();

  public void initiateReset(UserEntity user, String resetBaseUrl) {
    // Invalidate any existing tokens for this user
    tokens.entrySet().removeIf(e -> e.getValue().userId().equals(user.getId()));

    final String token = UUID.randomUUID().toString();
    tokens.put(token, new ResetEntry(user.getId(), Instant.now().plus(TOKEN_VALIDITY)));

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
    purgeExpired();
    final ResetEntry entry = tokens.get(token);
    if (entry == null) return Optional.empty();
    if (entry.expiry().isBefore(Instant.now())) {
      tokens.remove(token);
      return Optional.empty();
    }
    return usersRepository.findById(entry.userId());
  }

  public void consumeToken(String token) {
    tokens.remove(token);
  }

  private void purgeExpired() {
    final Instant now = Instant.now();
    tokens.entrySet().removeIf(e -> e.getValue().expiry().isBefore(now));
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
