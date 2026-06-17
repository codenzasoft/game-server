package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.PasswordResetTokenEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.PasswordResetTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService extends MailgunEmailService {

  private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);

  @Autowired private PasswordResetTokenRepository tokenRepository;

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
        .flatMap(entry -> findUserById(entry.getUserId()));
  }

  @Transactional
  public void consumeToken(String token) {
    try {
      tokenRepository.deleteById(UUID.fromString(token));
    } catch (IllegalArgumentException ignored) {
    }
  }
}
