package com.codenzasoft.games.spring.services;

import com.codenzasoft.games.spring.entities.EmailVerificationTokenEntity;
import com.codenzasoft.games.spring.entities.UserEntity;
import com.codenzasoft.games.spring.repositories.EmailVerificationTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService extends MailgunEmailService {

  private static final Duration TOKEN_VALIDITY = Duration.ofHours(24);

  @Autowired private EmailVerificationTokenRepository tokenRepository;

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
        .flatMap(entry -> findUserById(entry.getUserId()));
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
}
