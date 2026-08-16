package com.banco.infrastructure.security;

import com.banco.infrastructure.adapter.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * RBAC de /api/v1/clientes. Orden de matchers IMPORTANTE (SPEC-001 §8.5): el
 * listado EXACTO (GET /api/v1/clientes) va antes que el comodín /{id}.
 * Sin CORS (no hay frontend en Sprint 1), sin PasswordEncoder (llega con
 * SPEC-003).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/clientes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/clientes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clientes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clientes/**").hasAnyRole("ADMIN", "CLIENTE")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                escribirEnvelope(response, HttpStatus.UNAUTHORIZED,
                                        "NO_AUTENTICADO", "Token ausente o inválido"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                escribirEnvelope(response, HttpStatus.FORBIDDEN,
                                        "ACCESO_DENEGADO", "No tiene permisos para realizar esta operación")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void escribirEnvelope(HttpServletResponse response, HttpStatus status,
                                  String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(code, message, null));
    }
}
