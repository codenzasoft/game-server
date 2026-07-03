package com.codenzasoft.games.spring.controllers.api

import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.repositories.UsersRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import java.time.Instant

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class AuthApiControllerSpec extends Specification {

    @Autowired WebApplicationContext context
    @Autowired UsersRepository usersRepository
    @Autowired PasswordEncoder passwordEncoder
    @Autowired JdbcTemplate jdbcTemplate

    MockMvc mockMvc

    UserEntity testUser

    def setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build()

        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")

        testUser = new UserEntity()
        testUser.setUserName("auth-test-user")
        testUser.setEmail("auth-test@example.com")
        testUser.setPassword(passwordEncoder.encode("correct-password"))
        testUser.setEmailVerifiedAt(Instant.now())
        testUser = usersRepository.save(testUser)
    }

    def "login with valid credentials returns 200 and user JSON"() {
        when:
        def result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"username":"auth-test-user","password":"correct-password"}')
        )

        then:
        result.andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath('$.userName').value("auth-test-user"))
            .andExpect(jsonPath('$.email').value("auth-test@example.com"))
            .andExpect(jsonPath('$.password').doesNotExist())
    }

    def "login with incorrect password returns 401"() {
        when:
        def result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"username":"auth-test-user","password":"wrong-password"}')
        )

        then:
        result.andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value("Invalid credentials"))
    }

    def "login with unknown username returns 401"() {
        when:
        def result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"username":"nobody","password":"correct-password"}')
        )

        then:
        result.andExpect(status().isUnauthorized())
    }

    def "login with unverified email returns 401"() {
        given:
        def unverified = new UserEntity()
        unverified.setUserName("unverified-user")
        unverified.setEmail("unverified@example.com")
        unverified.setPassword(passwordEncoder.encode("password"))
        // emailVerifiedAt is intentionally not set
        usersRepository.save(unverified)

        when:
        def result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"username":"unverified-user","password":"password"}')
        )

        then:
        result.andExpect(status().isUnauthorized())
    }

    def "logout returns 204"() {
        when:
        def result = mockMvc.perform(
            post("/api/auth/logout")
                .with(user("auth-test-user"))
        )

        then:
        result.andExpect(status().isNoContent())
    }

    def "me returns current user info when authenticated"() {
        when:
        def result = mockMvc.perform(
            get("/api/auth/me")
                .with(user("auth-test-user"))
        )

        then:
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$.userName').value("auth-test-user"))
            .andExpect(jsonPath('$.password').doesNotExist())
    }

    def "me returns 401 when not authenticated"() {
        when:
        def result = mockMvc.perform(get("/api/auth/me"))

        then:
        result.andExpect(status().isUnauthorized())
    }
}
