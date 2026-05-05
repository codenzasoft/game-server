package com.codenzasoft.games.spring.repositories;

import com.codenzasoft.games.spring.entities.WhitelistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the {@code whitelist} table. The table's primary key is the (lowercased) email
 * address, so all lookups normalise via the {@code IgnoreCase} variants Spring Data exposes.
 */
@Repository
public interface WhitelistRepository extends JpaRepository<WhitelistEntity, String> {
  WhitelistEntity findByEmailIgnoreCase(final String email);

  boolean existsByEmailIgnoreCase(final String email);

  long deleteByEmailIgnoreCase(final String email);
}
