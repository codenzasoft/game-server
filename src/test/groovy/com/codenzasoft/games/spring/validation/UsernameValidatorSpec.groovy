package com.codenzasoft.games.spring.validation

import spock.lang.Specification

class UsernameValidatorSpec extends Specification {

    // -------------------------------------------------------------------------
    // Minimum length
    // -------------------------------------------------------------------------

    def "username shorter than MIN_LENGTH is rejected"() {
        given:
        def username = "a" * (UsernameValidator.MIN_LENGTH - 1)

        when:
        def result = UsernameValidator.validate(username)

        then:
        !result.valid
        result.errorMessage.contains("at least")
        result.errorMessage.contains(String.valueOf(UsernameValidator.MIN_LENGTH))
    }

    def "username at exactly MIN_LENGTH is accepted"() {
        given:
        def username = "a" * UsernameValidator.MIN_LENGTH

        when:
        def result = UsernameValidator.validate(username)

        then:
        result.valid
        result.errorMessage == null
    }

    // -------------------------------------------------------------------------
    // Maximum length
    // -------------------------------------------------------------------------

    def "username at exactly MAX_LENGTH is accepted"() {
        given:
        def username = "a" * UsernameValidator.MAX_LENGTH

        when:
        def result = UsernameValidator.validate(username)

        then:
        result.valid
        result.errorMessage == null
    }

    def "username longer than MAX_LENGTH is rejected"() {
        given:
        def username = "a" * (UsernameValidator.MAX_LENGTH + 1)

        when:
        def result = UsernameValidator.validate(username)

        then:
        !result.valid
        result.errorMessage.contains("no more than")
        result.errorMessage.contains(String.valueOf(UsernameValidator.MAX_LENGTH))
    }

    // -------------------------------------------------------------------------
    // Null / empty
    // -------------------------------------------------------------------------

    def "null username is rejected"() {
        when:
        def result = UsernameValidator.validate(null)

        then:
        !result.valid
        result.errorMessage != null
    }

    def "empty username is rejected"() {
        when:
        def result = UsernameValidator.validate("")

        then:
        !result.valid
    }

    // -------------------------------------------------------------------------
    // Allowed characters
    // -------------------------------------------------------------------------

    def "all-lowercase letters are accepted"() {
        when:
        def result = UsernameValidator.validate("lowercase")

        then:
        result.valid
    }

    def "all-uppercase letters are accepted"() {
        when:
        def result = UsernameValidator.validate("UPPERCASE")

        then:
        result.valid
    }

    def "digits are accepted"() {
        when:
        def result = UsernameValidator.validate("player123")

        then:
        result.valid
    }

    def "underscores are accepted"() {
        when:
        def result = UsernameValidator.validate("player_one")

        then:
        result.valid
    }

    def "hyphens are accepted"() {
        when:
        def result = UsernameValidator.validate("player-one")

        then:
        result.valid
    }

    def "mix of letters digits underscores and hyphens is accepted"() {
        when:
        def result = UsernameValidator.validate("Cool_Player-42")

        then:
        result.valid
    }

    // -------------------------------------------------------------------------
    // Disallowed characters
    // -------------------------------------------------------------------------

    def "username with a space is rejected"() {
        when:
        def result = UsernameValidator.validate("player one")

        then:
        !result.valid
    }

    def "username with an @ symbol is rejected"() {
        when:
        def result = UsernameValidator.validate("user@name")

        then:
        !result.valid
    }

    def "username with angle brackets is rejected"() {
        when:
        def result = UsernameValidator.validate("<script>")

        then:
        !result.valid
    }

    def "username with a single quote is rejected"() {
        when:
        def result = UsernameValidator.validate("o'brien")

        then:
        !result.valid
    }

    def "username with a semicolon is rejected"() {
        when:
        def result = UsernameValidator.validate("drop;tables")

        then:
        !result.valid
    }
}
