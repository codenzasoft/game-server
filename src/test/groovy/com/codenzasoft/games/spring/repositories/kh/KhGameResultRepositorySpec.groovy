package com.codenzasoft.games.spring.repositories.kh

import com.codenzasoft.games.kingshand.Season
import com.codenzasoft.games.spring.entities.UserEntity
import com.codenzasoft.games.spring.entities.kh.KhGameResultEntity
import com.codenzasoft.games.spring.repositories.UsersRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KhGameResultRepositorySpec extends Specification {

    @Autowired KhGameResultRepository repository
    @Autowired UsersRepository usersRepository
    @Autowired JdbcTemplate jdbcTemplate

    UserEntity testUser

    def setup() {
        jdbcTemplate.execute("DELETE FROM kh_game_results")
        jdbcTemplate.execute("DELETE FROM feedback")

        testUser = new UserEntity()
        testUser.setUserName("leaderboard-test-user")
        testUser = usersRepository.save(testUser)
    }

    // -------------------------------------------------------------------------
    // Top 5 by duration
    // -------------------------------------------------------------------------

    def "findTop5ByDuration returns results ordered by year DESC then season DESC"() {
        given:
        saveResult(1, Season.WINTER, 50.0)  // worst
        saveResult(3, Season.SPRING, 80.0)  // year 3, spring
        saveResult(3, Season.FALL,   90.0)  // year 3, fall — better than spring same year
        saveResult(5, Season.SUMMER, 60.0)  // best

        when:
        def results = repository.findTop5ByOrderByYearSurvivedDescSeasonOrderDesc()

        then:
        results.size() == 4
        results[0].yearSurvived == 5
        results[0].seasonSurvived == Season.SUMMER
        results[1].yearSurvived == 3
        results[1].seasonSurvived == Season.FALL
        results[2].yearSurvived == 3
        results[2].seasonSurvived == Season.SPRING
        results[3].yearSurvived == 1
    }

    def "findTop5ByDuration returns at most 5 results"() {
        given:
        (1..7).each { i -> saveResult(i, Season.SPRING, 20.0) }

        when:
        def results = repository.findTop5ByOrderByYearSurvivedDescSeasonOrderDesc()

        then:
        results.size() == 5
    }

    def "season ordering within the same year is WINTER > FALL > SUMMER > SPRING"() {
        given:
        saveResult(2, Season.SPRING, 20.0)
        saveResult(2, Season.SUMMER, 20.0)
        saveResult(2, Season.FALL,   20.0)
        saveResult(2, Season.WINTER, 20.0)

        when:
        def results = repository.findTop5ByOrderByYearSurvivedDescSeasonOrderDesc()

        then:
        results*.seasonSurvived == [Season.WINTER, Season.FALL, Season.SUMMER, Season.SPRING]
    }

    // -------------------------------------------------------------------------
    // Top 5 by peak population
    // -------------------------------------------------------------------------

    def "findTop5ByPopulation returns results ordered by peak population DESC"() {
        given:
        saveResult(1, Season.SPRING, 100.0)
        saveResult(1, Season.SPRING, 25.0)
        saveResult(1, Season.SPRING, 75.0)
        saveResult(1, Season.SPRING, 50.0)

        when:
        def results = repository.findTop5ByOrderByPeakPopulationDesc()

        then:
        results*.peakPopulation == [100.0, 75.0, 50.0, 25.0]
    }

    def "findTop5ByPopulation returns at most 5 results"() {
        given:
        (1..7).each { i -> saveResult(1, Season.SPRING, i * 10.0) }

        when:
        def results = repository.findTop5ByOrderByPeakPopulationDesc()

        then:
        results.size() == 5
    }

    // -------------------------------------------------------------------------
    // Empty state
    // -------------------------------------------------------------------------

    def "both queries return empty list when no results exist"() {
        expect:
        repository.findTop5ByOrderByYearSurvivedDescSeasonOrderDesc().isEmpty()
        repository.findTop5ByOrderByPeakPopulationDesc().isEmpty()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int resultCounter = 0

    private KhGameResultEntity saveResult(int year, Season season, double peakPop) {
        def gameId = UUID.randomUUID()
        // Insert a minimal games row so the FK constraint is satisfied
        jdbcTemplate.update(
            "INSERT INTO games (id, game_type, created_at, over_at) VALUES (?::uuid, 'KH', NOW(), NOW())",
            gameId.toString())

        def entity = new KhGameResultEntity()
        entity.setGameId(gameId)
        entity.setUser(testUser)
        entity.setYearSurvived(year)
        entity.setSeasonSurvived(season)
        entity.setPeakPopulation(peakPop)
        return repository.save(entity)
    }
}
