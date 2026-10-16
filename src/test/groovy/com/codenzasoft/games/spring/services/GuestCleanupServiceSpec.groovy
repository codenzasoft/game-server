package com.codenzasoft.games.spring.services

import com.codenzasoft.games.spring.config.Role
import com.codenzasoft.games.spring.entities.RoleEntity
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.UsersRepository
import com.codenzasoft.games.xo.Piece
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuestCleanupServiceSpec extends Specification {

    @Autowired GuestCleanupService guestCleanupService
    @Autowired GameService gameService
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate
    @PersistenceContext EntityManager entityManager

    def setup() {
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")
        jdbcTemplate.execute("DELETE FROM users WHERE user_name LIKE 'cleanup-test-%'")
    }

    def "expired guest with no active games is deleted"() {
        given:
        def guest = createGuest("cleanup-test-alice")
        makeExpired(guest)

        when:
        guestCleanupService.deleteExpiredGuests()

        then:
        usersRepository.findByUserName("cleanup-test-alice#0001") == null
    }

    def "recent guest is not deleted"() {
        given:
        createGuest("cleanup-test-bob")
        // no makeExpired — created_at is within the last hour

        when:
        guestCleanupService.deleteExpiredGuests()

        then:
        usersRepository.findByUserName("cleanup-test-bob#0001") != null
    }

    def "expired guest with an active game is not deleted"() {
        given:
        def guest = createGuest("cleanup-test-carol")
        gameService.createXoGame(guest, Piece.X, Piece.X)
        makeExpired(guest)

        when:
        guestCleanupService.deleteExpiredGuests()

        then:
        usersRepository.findByUserName("cleanup-test-carol#0001") != null
    }

    def "expired guest with only a finished game is deleted"() {
        given:
        def guest = createGuest("cleanup-test-dave")
        def game = gameService.createXoGame(guest, Piece.X, Piece.X)
        makeExpired(guest)
        // Update after makeExpired so the flush() inside doesn't overwrite this JDBC change
        jdbcTemplate.update("UPDATE games SET over_at = NOW() WHERE id = ?", game.getId())

        when:
        guestCleanupService.deleteExpiredGuests()

        then:
        usersRepository.findByUserName("cleanup-test-dave#0001") == null
    }

    def "expired guest with only a quit game is deleted"() {
        given:
        def guest = createGuest("cleanup-test-eve")
        def game = gameService.createXoGame(guest, Piece.X, Piece.X)
        makeExpired(guest)
        // Update after makeExpired so the flush() inside doesn't overwrite this JDBC change
        jdbcTemplate.update("UPDATE games SET quit_at = NOW() WHERE id = ?", game.getId())

        when:
        guestCleanupService.deleteExpiredGuests()

        then:
        usersRepository.findByUserName("cleanup-test-eve#0001") == null
    }

    def "non-guest USER account is not deleted even when old"() {
        given:
        def user = new UserEntity()
        user.setUserName("cleanup-test-regular")
        def saved = usersRepository.save(user)
        saved.getRoles().add(new RoleEntity(saved.getId(), Role.USER))
        usersRepository.save(saved)
        entityManager.flush()
        jdbcTemplate.update("UPDATE users SET created_at = NOW() - INTERVAL '2 hours' WHERE id = ?", saved.getId())
        entityManager.clear()

        when:
        guestCleanupService.deleteExpiredGuests()

        then:
        usersRepository.findByUserName("cleanup-test-regular") != null
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserEntity createGuest(String storedName) {
        def user = new UserEntity()
        user.setUserName(storedName + "#0001")
        user.setEmailVerifiedAt(Instant.now())
        def saved = usersRepository.save(user)
        saved.getRoles().add(new RoleEntity(saved.getId(), Role.GUEST))
        usersRepository.save(saved)
        entityManager.flush()
        return saved
    }

    /**
     * Moves the user's created_at 2 hours into the past and clears the JPA L1 cache.
     * Flushes first so any pending JPA writes (e.g. game_players join table rows) reach
     * the DB before the clear discards them.
     */
    private void makeExpired(UserEntity user) {
        entityManager.flush()
        jdbcTemplate.update("UPDATE users SET created_at = NOW() - INTERVAL '2 hours' WHERE id = ?", user.getId())
        entityManager.clear()
    }
}
