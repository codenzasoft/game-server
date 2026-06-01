package com.codenzasoft.games.spring.repositories

import com.codenzasoft.games.spring.entities.FeedbackEntity
import com.codenzasoft.games.spring.entities.FeedbackEntity.Status
import com.codenzasoft.games.spring.entities.FeedbackEntity.Type
import com.codenzasoft.games.spring.entities.UserEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackRepositorySpec extends Specification {

    @Autowired FeedbackRepository feedbackRepository
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        // @Transactional rolls back data between tests, so only seed data needs clearing.
        // There is no feedback seed data, but delete defensively in case any exists.
        jdbcTemplate.execute("DELETE FROM feedback")

        testUser = new UserEntity()
        testUser.setUserName("feedback-test-user")
        testUser = usersRepository.save(testUser)
    }

    // -------------------------------------------------------------------------
    // Save and retrieve
    // -------------------------------------------------------------------------

    def "saved feedback can be retrieved by id"() {
        given:
        def feedback = buildFeedback(Type.BUG, "App crashes", "Crashes on startup", Status.NEW)

        when:
        def saved = feedbackRepository.save(feedback)
        def found = feedbackRepository.findById(saved.id)

        then:
        found.isPresent()
        found.get().subject == "App crashes"
        found.get().type == Type.BUG
        found.get().status == Status.NEW
        found.get().createdAt != null
    }

    def "default status is NEW when not explicitly set"() {
        given:
        def feedback = buildFeedback(Type.FEEDBACK, "Nice game", "Loving it!", null)

        when:
        def saved = feedbackRepository.save(feedback)

        then:
        saved.status == Status.NEW
    }

    // -------------------------------------------------------------------------
    // findByStatus
    // -------------------------------------------------------------------------

    def "findByStatus returns only entries with the matching status"() {
        given:
        feedbackRepository.save(buildFeedback(Type.BUG, "Bug 1", "desc", Status.NEW))
        feedbackRepository.save(buildFeedback(Type.BUG, "Bug 2", "desc", Status.NEW))
        feedbackRepository.save(buildFeedback(Type.FEEDBACK, "Idea", "desc", Status.REVIEWED))

        when:
        def newEntries = feedbackRepository.findByStatus(Status.NEW, PageRequest.of(0, 10))
        def reviewedEntries = feedbackRepository.findByStatus(Status.REVIEWED, PageRequest.of(0, 10))

        then:
        newEntries.totalElements == 2
        reviewedEntries.totalElements == 1
    }

    def "findByStatus returns empty page when no entries match"() {
        given:
        feedbackRepository.save(buildFeedback(Type.BUG, "Bug", "desc", Status.NEW))

        when:
        def resolved = feedbackRepository.findByStatus(Status.RESOLVED, PageRequest.of(0, 10))

        then:
        resolved.totalElements == 0
    }

    // -------------------------------------------------------------------------
    // Status update
    // -------------------------------------------------------------------------

    def "status can be updated from NEW to REVIEWED"() {
        given:
        def saved = feedbackRepository.save(buildFeedback(Type.BUG, "Bug", "desc", Status.NEW))

        when:
        saved.setStatus(Status.REVIEWED)
        feedbackRepository.save(saved)

        then:
        feedbackRepository.findById(saved.id).get().status == Status.REVIEWED
    }

    // -------------------------------------------------------------------------
    // Cascade delete
    // -------------------------------------------------------------------------

    def "deleting a user cascades to their feedback"() {
        given:
        feedbackRepository.save(buildFeedback(Type.BUG, "Bug", "desc", Status.NEW))
        def userId = testUser.id

        when: "user is deleted directly via JDBC, exercising the DB-level ON DELETE CASCADE"
        jdbcTemplate.execute("DELETE FROM users WHERE id = '${userId}'")

        then: "the associated feedback row is also gone"
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM feedback WHERE user_id = '${userId}'", Long) == 0
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FeedbackEntity buildFeedback(Type type, String subject, String message, Status status) {
        def f = new FeedbackEntity()
        f.setUser(testUser)
        f.setType(type)
        f.setSubject(subject)
        f.setMessage(message)
        if (status != null) f.setStatus(status)
        return f
    }
}
