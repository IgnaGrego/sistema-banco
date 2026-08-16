package com.banco.integration;

import com.banco.support.JwtTokenFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de los tests de integración: Postgres real (Testcontainers postgres:16
 * con @ServiceConnection, el datasource lo provee el contenedor) + MockMvc.
 *
 * El bean de {@link JwtTokenFactory} lo declara cada test concreto con su
 * {@code @TestConfiguration} anidada: Spring Boot solo auto-registra las
 * {@code @TestConfiguration} anidadas en la clase de test ejecutada, no en
 * sus superclases.
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
}
