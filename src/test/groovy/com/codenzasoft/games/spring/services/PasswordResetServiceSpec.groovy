package com.codenzasoft.games.spring.services

import com.codenzasoft.games.spring.entities.PasswordResetTokenEntity
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.PasswordResetTokenRepository
import com.codenzasoft.games.spring.repositories.UsersRepository
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
class PasswordResetServiceSpec extends Specification {

    @Autowired PasswordResetService passwordResetService
    @Autowired PasswordResetTokenRepository tokenRepository
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        jdbcTemplate.execute("DELETE FROM password_reset_tokens")

        testUser = new UserEntity()
        testUser.setUserName("reset-test-user")
        testUser.setEmail("reset@example.com")
        testUser = usersRepository.save(testUser)
    }

    // ── initiateReset ─────────────────────────────────────────────────────────

    def "initiateReset saves a token in the database for the given user"() {
        when:
        passwordResetService.initiateReset(testUser, "https://example.com/reset-password")

        then:
        def tokens = tokenRepository.findAll()
        tokens.size() == 1
        tokens[0].userId == testUser.id
    }

    def "initiateReset token has an expiry approximately 30 minutes in the future"() {
        when:
        passwordResetService.initiateReset(testUser, "https://example.com/reset-password")

        then:
        def token = tokenRepository.findAll()[0]
        token.expiresAt.isAfter(Instant.now().plusSeconds(29 * 60))
        token.expiresAt.isBefore(Instant.now().plusSeconds(31 * 60))
    }

    def "initiateReset replaces an existing token for the same user"() {
        given:
        def existingToken = UUID.randomUUID()
        tokenRepository.save(new PasswordResetTokenEntity(existingToken, testUser.id, Instant.now().plusSeconds(1800)))

        when:
        passwordResetService.initiateReset(testUser, "https://example.com/reset-password")

        then:
        def tokens = tokenRepository.findAll()
        tokens.size() == 1
        tokens[0].token != existingToken
    }

    def "initiateReset removes expired tokens from the database"() {
        given:
        def otherUser = new UserEntity()
        otherUser.setUserName("other-reset-user")
        otherUser.setEmail("other@example.com")
        otherUser = usersRepository.save(otherUser)

        tokenRepository.save(new PasswordResetTokenEntity(UUID.randomUUID(), otherUser.id, Instant.now().minusSeconds(1)))

        when:
        passwordResetService.initiateReset(testUser, "https://example.com/reset-password")

        then:
        def tokens = tokenRepository.findAll()
        tokens.size() == 1
        tokens[0].userId == testUser.id
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    def "validateToken returns empty for a malformed token string"() {
        expect:
        passwordResetService.validateToken("not-a-uuid").isEmpty()
    }

    def "validateToken returns empty when the token does not exist"() {
        expect:
        passwordResetService.validateToken(UUID.randomUUID().toString()).isEmpty()
    }

    def "validateToken returns the user for a valid non-expired token"() {
        given:
        passwordResetService.initiateReset(testUser, "https://example.com/reset-password")
        def token = tokenRepository.findAll()[0].token.toString()

        when:
        def result = passwordResetService.validateToken(token)

        then:
        result.isPresent()
        result.get().id == testUser.id
    }

    def "validateToken returns empty for an expired token"() {
        given:
        def token = UUID.randomUUID()
        tokenRepository.save(new PasswordResetTokenEntity(token, testUser.id, Instant.now().minusSeconds(1)))

        expect:
        passwordResetService.validateToken(token.toString()).isEmpty()
    }

    // ── consumeToken ──────────────────────────────────────────────────────────

    def "consumeToken removes the token from the database"() {
        given:
        def token = UUID.randomUUID()
        tokenRepository.save(new PasswordResetTokenEntity(token, testUser.id, Instant.now().plusSeconds(1800)))

        when:
        passwordResetService.consumeToken(token.toString())

        then:
        tokenRepository.findAll().isEmpty()
    }

    def "consumeToken does not throw for an unknown token"() {
        when:
        passwordResetService.consumeToken(UUID.randomUUID().toString())

        then:
        noExceptionThrown()
    }

    def "consumeToken does not throw for a malformed token string"() {
        when:
        passwordResetService.consumeToken("not-a-uuid")

        then:
        noExceptionThrown()
    }
}
