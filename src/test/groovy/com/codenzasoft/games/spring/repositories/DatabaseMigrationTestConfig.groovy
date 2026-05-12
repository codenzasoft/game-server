package com.codenzasoft.games.spring.repositories

import org.flywaydb.core.Flyway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.sql.DataSource

@Configuration
class DatabaseMigrationTestConfig {

    @Bean
    Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:../game-database/migrations")
            .failOnMissingLocations(true)
            .load()
        flyway.migrate()
        return flyway
    }
}
