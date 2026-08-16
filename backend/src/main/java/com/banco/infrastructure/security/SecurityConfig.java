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
 * RBAC. Orden de matchers IMPORTANTE: el listado EXACTO (GET /api/v1/clientes)
 * va antes que el comodín /{id} (SPEC-001 §8.5). SPEC-003 agrega al INICIO los
 * dos matchers públicos de /api/v1/auth (FR-001, FR-002; docs/architecture/SPEC-003.md
 * §8.5). SPEC-002 agrega los matchers de cuentas entre los de clientes y el
 * anyRequest (docs/architecture/SPEC-002.md §8.4): el listado EXACTO
 * (GET /api/v1/cuentas) y la sub-ruta /cbu/** van ANTES del comodín /{id}; la
 * propiedad de CLIENTE se verifica SIEMPRE en el use case (BR-006,
 * ARCHITECTURE.md §8), nunca en el matcher. El resto del chain (CSRF off,
 * stateless, entry point 401, handler 403, filtro JWT) no cambia.
 * Sin CORS (no hay frontend en este sprint).
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clientes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/clientes/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clientes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/clientes/**").hasAnyRole("ADMIN", "CLIENTE")
                        // SPEC-002 §8.4 (orden crítico): apertura solo ADMIN; el
                        // listado EXACTO y /cbu/** van antes que el comodín /{id}.
                        .requestMatchers(HttpMethod.POST, "/api/v1/cuentas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cuentas").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cuentas/cbu/**").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/cuentas/**").hasAnyRole("ADMIN", "CLIENTE")
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
