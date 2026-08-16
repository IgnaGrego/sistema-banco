-- V1__schema_inicial.sql
-- Esquema inicial: tabla clientes (SPEC-001).
--
-- NOTA sobre fecha_alta: la spec (docs/architecture/SPEC-001.md §6.1) define
-- `TIMESTAMP`. Sin embargo, Hibernate 6 mapea java.time.Instant por defecto a
-- "timestamp(6) with time zone" y `ddl-auto: validate` fallaría con un
-- `TIMESTAMP` simple ("found timestamp, expecting timestamptz"). Se usa
-- `TIMESTAMP WITH TIME ZONE` (timestamptz) para que la validación pase.
-- Semánticamente es consistente con el diseño: los Instant se guardan en UTC
-- (ISO-8601 UTC). Desviación documentada en el reporte de implementación.

CREATE TABLE clientes (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    apellido    VARCHAR(100) NOT NULL,
    dni         VARCHAR(8)   NOT NULL,
    email       VARCHAR(254) NOT NULL,
    telefono    VARCHAR(16),
    fecha_alta  TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE clientes ADD CONSTRAINT uq_clientes_dni   UNIQUE (dni);
ALTER TABLE clientes ADD CONSTRAINT uq_clientes_email UNIQUE (email);
