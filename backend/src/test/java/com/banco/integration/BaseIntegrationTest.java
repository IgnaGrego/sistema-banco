package com.banco.integration;

import com.banco.support.JwtTokenFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de los tests de integración: Postgres real (Testcontainers postgres:16
 * con @ServiceConnection, el datasource lo provee el contenedor) + MockMvc +
 * bean de JwtTokenFactory.
 *
 * {@code disabledWithoutDocker = true}: sin Docker local los tests se omiten
 * (no falla el build); en CI (GitHub Actions, con Docker) se ejecutan de
 * verdad (AC-024).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtTokenFactory tokens;

    @TestConfiguration
    static class TokenConfig {

        @Bean
        JwtTokenFactory jwtTokenFactory(
                @Value("${banco.security.jwt-secret}") String secret,
                @Value("${banco.security.jwt-expiration-minutes:60}") long expirationMinutes) {
            return new JwtTokenFactory(secret, expirationMinutes);
        }
    }
}
