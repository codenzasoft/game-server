package com.codenzasoft.games.spring.services

import com.codenzasoft.games.spring.entities.EmailVerificationTokenEntity
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.EmailVerificationTokenRepository
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
class EmailVerificationServiceSpec extends Specification {

    @Autowired EmailVerificationService emailVerificationService
    @Autowired EmailVerificationTokenRepository tokenRepository
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        jdbcTemplate.execute("DELETE FROM email_verification_tokens")

        testUser = new UserEntity()
        testUser.setUserName("verify-test-user")
        testUser.setEmail("verify@example.com")
        testUser = usersRepository.save(testUser)
    }

    // ── sendVerification ──────────────────────────────────────────────────────

    def "sendVerification saves a token in the database for the given user"() {
        when:
        emailVerificationService.sendVerification(testUser, "https://example.com/verify-email")

        then:
        def tokens = tokenRepository.findAll()
        tokens.size() == 1
        tokens[0].userId == testUser.id
    }

    def "sendVerification token has an expiry approximately 24 hours in the future"() {
        when:
        emailVerificationService.sendVerification(testUser, "https://example.com/verify-email")

        then:
        def token = tokenRepository.findAll()[0]
        token.expiresAt.isAfter(Instant.now().plusSeconds(23 * 3600))
        token.expiresAt.isBefore(Instant.now().plusSeconds(25 * 3600))
    }

    def "sendVerification replaces an existing token for the same user"() {
        given:
        def existingToken = UUID.randomUUID()
        tokenRepository.save(new EmailVerificationTokenEntity(existingToken, testUser.id, Instant.now().plusSeconds(86400)))

        when:
        emailVerificationService.sendVerification(testUser, "https://example.com/verify-email")

        then:
        def tokens = tokenRepository.findAll()
        tokens.size() == 1
        tokens[0].token != existingToken
    }

    def "sendVerification removes expired tokens from the database"() {
        given:
        def otherUser = new UserEntity()
        otherUser.setUserName("other-verify-user")
        otherUser.setEmail("other@example.com")
        otherUser = usersRepository.save(otherUser)

        tokenRepository.save(new EmailVerificationTokenEntity(UUID.randomUUID(), otherUser.id, Instant.now().minusSeconds(1)))

        when:
        emailVerificationService.sendVerification(testUser, "https://example.com/verify-email")

        then:
        def tokens = tokenRepository.findAll()
        tokens.size() == 1
        tokens[0].userId == testUser.id
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    def "validateToken returns empty for a malformed token string"() {
        expect:
        emailVerificationService.validateToken("not-a-uuid").isEmpty()
    }

    def "validateToken returns empty when the token does not exist"() {
        expect:
        emailVerificationService.validateToken(UUID.randomUUID().toString()).isEmpty()
    }

    def "validateToken returns the user for a valid non-expired token"() {
        given:
        emailVerificationService.sendVerification(testUser, "https://example.com/verify-email")
        def token = tokenRepository.findAll()[0].token.toString()

        when:
        def result = emailVerificationService.validateToken(token)

        then:
        result.isPresent()
        result.get().id == testUser.id
    }

    def "validateToken returns empty for an expired token"() {
        given:
        def token = UUID.randomUUID()
        tokenRepository.save(new EmailVerificationTokenEntity(token, testUser.id, Instant.now().minusSeconds(1)))

        expect:
        emailVerificationService.validateToken(token.toString()).isEmpty()
    }

    // ── verifyUser ────────────────────────────────────────────────────────────

    def "verifyUser sets emailVerifiedAt on the user"() {
        given:
        def token = UUID.randomUUID()
        tokenRepository.save(new EmailVerificationTokenEntity(token, testUser.id, Instant.now().plusSeconds(86400)))

        when:
        emailVerificationService.verifyUser(testUser, token.toString())

        then:
        def reloaded = usersRepository.findById(testUser.id).get()
        reloaded.isEmailVerified()
        reloaded.emailVerifiedAt.isBefore(Instant.now().plusSeconds(1))
    }

    def "verifyUser removes the token from the database"() {
        given:
        def token = UUID.randomUUID()
        tokenRepository.save(new EmailVerificationTokenEntity(token, testUser.id, Instant.now().plusSeconds(86400)))

        when:
        emailVerificationService.verifyUser(testUser, token.toString())

        then:
        tokenRepository.findAll().isEmpty()
    }

    def "verifyUser does not throw for a malformed token string"() {
        when:
        emailVerificationService.verifyUser(testUser, "not-a-uuid")

        then:
        noExceptionThrown()
    }

    // ── hasPendingVerification ────────────────────────────────────────────────

    def "hasPendingVerification returns false when no token exists for the user"() {
        expect:
        !emailVerificationService.hasPendingVerification(testUser)
    }

    def "hasPendingVerification returns true when a token exists for the user"() {
        given:
        tokenRepository.save(new EmailVerificationTokenEntity(UUID.randomUUID(), testUser.id, Instant.now().plusSeconds(86400)))

        expect:
        emailVerificationService.hasPendingVerification(testUser)
    }

    def "hasPendingVerification returns true even for an expired token"() {
        given:
        tokenRepository.save(new EmailVerificationTokenEntity(UUID.randomUUID(), testUser.id, Instant.now().minusSeconds(1)))

        expect:
        emailVerificationService.hasPendingVerification(testUser)
    }
}
