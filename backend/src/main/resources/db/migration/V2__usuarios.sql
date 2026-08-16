-- V2__usuarios.sql
-- Entidad Usuario (SPEC-003): username único (BR-003), password_hash BCrypt
-- (BR-001), rol CLIENTE|ADMIN y cliente_id nullable con FK a clientes(id)
-- (FR-005; null para ADMIN).

CREATE TABLE usuarios (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    rol           VARCHAR(7)  NOT NULL,
    cliente_id    BIGINT      REFERENCES clientes(id)
);

ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_username UNIQUE (username);

-- Compatibilidad con ddl-auto: validate (lección de V1/TIMESTAMPTZ): tipos
-- mapeados: BIGSERIAL <-> Long @Id, VARCHAR(n) <-> @Column(length=n),
-- BIGINT <-> Long. No hay columnas Instant en esta tabla. Hibernate validate
-- no valida constraints UNIQUE/FK (los define Flyway, como en V1).
-- Sin created_at: la spec (SPEC-003 §10) no lo define; no se inventa.
