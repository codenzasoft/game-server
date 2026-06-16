package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.PasswordResetTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository
    extends JpaRepository<PasswordResetTokenEntity, UUID> {

  Optional<PasswordResetTokenEntity> findByTokenAndExpiresAtAfter(UUID token, Instant now);

  @Modifying
  @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.userId = :userId")
  void deleteByUserId(UUID userId);

  @Modifying
  @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.expiresAt < :now")
  void deleteExpiredBefore(Instant now);
}
