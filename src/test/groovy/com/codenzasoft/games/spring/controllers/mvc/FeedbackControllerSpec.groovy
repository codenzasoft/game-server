package com.codenzasoft.games.spring.controllers.mvc

import com.codenzasoft.games.spring.entities.FeedbackEntity
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.FeedbackRepository
import com.codenzasoft.games.spring.services.GamesUserDetailsService
import com.codenzasoft.games.spring.views.ViewHelper
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import spock.lang.Specification

class FeedbackControllerSpec extends Specification {

    GamesUserDetailsService userDetailsService = Mock()
    FeedbackRepository feedbackRepository = Mock()
    ViewHelper viewHelper = Mock()
    RedirectAttributes redirectAttrs = Mock()
    UserDetails principal = Mock()

    FeedbackController controller

    def setup() {
        controller = new FeedbackController()
        controller.gamesUserDetailsService = userDetailsService
        controller.feedbackRepository = feedbackRepository
        controller.viewHelper = viewHelper

        principal.getUsername() >> "testuser"
        userDetailsService.getUserByUsernameOrEmail("testuser") >> Optional.of(new UserEntity())
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    def "valid bug report is saved and redirects with success"() {
        when:
        def result = controller.submitFeedback("BUG", "Game crashes on load", "It crashes every time I start a game.", principal, redirectAttrs)

        then:
        1 * feedbackRepository.save({ FeedbackEntity f ->
            f.type == FeedbackEntity.Type.BUG &&
            f.subject == "Game crashes on load" &&
            f.message == "It crashes every time I start a game."
        })
        1 * redirectAttrs.addFlashAttribute("success", _)
        result == "redirect:/feedback"
    }

    def "valid general feedback is saved and redirects with success"() {
        when:
        def result = controller.submitFeedback("FEEDBACK", "Love the game", "Really enjoying it so far!", principal, redirectAttrs)

        then:
        1 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("success", _)
        result == "redirect:/feedback"
    }

    def "type matching is case-insensitive"() {
        when:
        def result = controller.submitFeedback("bug", "subject", "message", principal, redirectAttrs)

        then:
        1 * feedbackRepository.save(_)
        result == "redirect:/feedback"
    }

    def "leading and trailing whitespace is trimmed from subject and message"() {
        when:
        controller.submitFeedback("BUG", "  spaced subject  ", "  spaced message  ", principal, redirectAttrs)

        then:
        1 * feedbackRepository.save({ FeedbackEntity f ->
            f.subject == "spaced subject" && f.message == "spaced message"
        })
    }

    // -------------------------------------------------------------------------
    // Required field validation
    // -------------------------------------------------------------------------

    def "blank subject is rejected"() {
        when:
        def result = controller.submitFeedback("BUG", "   ", "some message", principal, redirectAttrs)

        then:
        0 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("error", _)
        result == "redirect:/feedback"
    }

    def "blank message is rejected"() {
        when:
        def result = controller.submitFeedback("BUG", "some subject", "   ", principal, redirectAttrs)

        then:
        0 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("error", _)
        result == "redirect:/feedback"
    }

    // -------------------------------------------------------------------------
    // Length validation
    // -------------------------------------------------------------------------

    def "subject at exactly 255 characters is accepted"() {
        given:
        def subject = "a" * 255

        when:
        def result = controller.submitFeedback("BUG", subject, "valid message", principal, redirectAttrs)

        then:
        1 * feedbackRepository.save(_)
        result == "redirect:/feedback"
    }

    def "subject exceeding 255 characters is rejected"() {
        given:
        def subject = "a" * 256

        when:
        def result = controller.submitFeedback("BUG", subject, "valid message", principal, redirectAttrs)

        then:
        0 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("error", _)
        result == "redirect:/feedback"
    }

    def "message at exactly 5000 characters is accepted"() {
        given:
        def message = "a" * 5000

        when:
        def result = controller.submitFeedback("BUG", "valid subject", message, principal, redirectAttrs)

        then:
        1 * feedbackRepository.save(_)
        result == "redirect:/feedback"
    }

    def "message exceeding 5000 characters is rejected"() {
        given:
        def message = "a" * 5001

        when:
        def result = controller.submitFeedback("BUG", "valid subject", message, principal, redirectAttrs)

        then:
        0 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("error", _)
        result == "redirect:/feedback"
    }

    // -------------------------------------------------------------------------
    // Type validation
    // -------------------------------------------------------------------------

    def "invalid type is rejected"() {
        when:
        def result = controller.submitFeedback("INVALID_TYPE", "subject", "message", principal, redirectAttrs)

        then:
        0 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("error", _)
        result == "redirect:/feedback"
    }

    // -------------------------------------------------------------------------
    // Unknown user
    // -------------------------------------------------------------------------

    def "unresolvable principal is rejected without saving"() {
        given: "a principal whose username resolves to no user"
        def unknownPrincipal = Mock(UserDetails)
        unknownPrincipal.getUsername() >> "unknown-user"
        userDetailsService.getUserByUsernameOrEmail("unknown-user") >> Optional.empty()

        when:
        def result = controller.submitFeedback("BUG", "subject", "message", unknownPrincipal, redirectAttrs)

        then:
        0 * feedbackRepository.save(_)
        1 * redirectAttrs.addFlashAttribute("error", _)
        result == "redirect:/feedback"
    }
}
