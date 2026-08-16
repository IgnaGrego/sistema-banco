-- V3__cuentas.sql
-- Entidad Cuenta (SPEC-002): CBU único (BR-001, UNIQUE), saldo nunca negativo
-- (BR-002, NUMERIC), moneda ARS (BR-005, A-005), estado ACTIVA por defecto
-- (FR-007), version para optimistic locking (ARCHITECTURE.md §5) y FK al
-- Cliente titular (ERR-002).
--
-- NOTA created_at: la spec (docs/architecture/SPEC-002.md §6.1 y la spec
-- SPEC-002 §10) define `TIMESTAMP`. Sin embargo, Hibernate 6 mapea
-- java.time.Instant por defecto a "timestamp(6) with time zone" y
-- `ddl-auto: validate` fallaría con un TIMESTAMP simple ("found timestamp,
-- expecting timestamptz"). Se usa `TIMESTAMP WITH TIME ZONE` (timestamptz),
-- misma desviación documentada que V1 (fecha_alta) y coherente con guardar
-- Instants en UTC.

CREATE TABLE cuentas (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  BIGINT NOT NULL REFERENCES clientes(id),
    cbu         VARCHAR(22) NOT NULL,
    tipo        VARCHAR(20) NOT NULL,
    saldo       NUMERIC(19,2) NOT NULL DEFAULT 0,
    moneda      VARCHAR(3)  NOT NULL DEFAULT 'ARS',
    estado      VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE cuentas ADD CONSTRAINT uq_cuentas_cbu UNIQUE (cbu);
CREATE INDEX idx_cuentas_cliente_id ON cuentas (cliente_id);
