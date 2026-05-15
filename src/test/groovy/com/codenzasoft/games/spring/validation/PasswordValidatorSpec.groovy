package com.codenzasoft.games.spring.validation

import spock.lang.Specification

class PasswordValidatorSpec extends Specification {

    // -------------------------------------------------------------------------
    // Minimum length
    // -------------------------------------------------------------------------

    def "password shorter than MIN_LENGTH is rejected"() {
        given:
        def password = "a" * (PasswordValidator.MIN_LENGTH - 1)

        when:
        def result = PasswordValidator.validate(password)

        then:
        !result.valid
        result.errorMessage.contains("at least")
        result.errorMessage.contains(String.valueOf(PasswordValidator.MIN_LENGTH))
    }

    def "password at exactly MIN_LENGTH is accepted"() {
        given:
        def password = "a" * PasswordValidator.MIN_LENGTH

        when:
        def result = PasswordValidator.validate(password)

        then:
        result.valid
        result.errorMessage == null
    }

    // -------------------------------------------------------------------------
    // Maximum length
    // -------------------------------------------------------------------------

    def "password at exactly MAX_LENGTH is accepted"() {
        given:
        def password = "a" * PasswordValidator.MAX_LENGTH

        when:
        def result = PasswordValidator.validate(password)

        then:
        result.valid
        result.errorMessage == null
    }

    def "password longer than MAX_LENGTH is rejected"() {
        given:
        def password = "a" * (PasswordValidator.MAX_LENGTH + 1)

        when:
        def result = PasswordValidator.validate(password)

        then:
        !result.valid
        result.errorMessage.contains("no more than")
        result.errorMessage.contains(String.valueOf(PasswordValidator.MAX_LENGTH))
    }

    // -------------------------------------------------------------------------
    // Null / empty
    // -------------------------------------------------------------------------

    def "null password is rejected"() {
        when:
        def result = PasswordValidator.validate(null)

        then:
        !result.valid
        result.errorMessage != null
    }

    def "empty password is rejected"() {
        when:
        def result = PasswordValidator.validate("")

        then:
        !result.valid
    }

    // -------------------------------------------------------------------------
    // No mandatory character-type rules (NIST compliance)
    // -------------------------------------------------------------------------

    def "all-lowercase password of sufficient length is accepted"() {
        when:
        def result = PasswordValidator.validate("alllowercaseletters")

        then:
        result.valid
    }

    def "all-uppercase password of sufficient length is accepted"() {
        when:
        def result = PasswordValidator.validate("ALLUPPERCASELETTERS")

        then:
        result.valid
    }

    def "all-digit password of sufficient length is accepted"() {
        when:
        def result = PasswordValidator.validate("123456789012")

        then:
        result.valid
    }

    def "password with spaces is accepted"() {
        when:
        def result = PasswordValidator.validate("correct horse battery staple")

        then:
        result.valid
    }

    def "password with special characters is accepted"() {
        when:
        def result = PasswordValidator.validate("M!x3d@Chars!!")

        then:
        result.valid
    }
}
