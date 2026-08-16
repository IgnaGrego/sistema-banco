-- V4__movimientos.sql
-- Entidad Movimiento (SPEC-004, FR-003). La tabla cuentas proviene de la
-- migración V3__cuentas.sql de SPEC-002 (version ya incluida). Compatible con
-- ddl-auto: validate (lecciones de V1/V2/V3):
--   * Instant -> TIMESTAMP WITH TIME ZONE (Hibernate 6 mapea Instant a
--     "timestamp(6) with time zone"; un TIMESTAMP simple fallaría).
--   * BIGSERIAL <-> Long @Id, BIGINT <-> Long, DECIMAL(19,2)/NUMERIC(19,2)
--     <-> BigDecimal, VARCHAR(n) <-> @Column(length=n). Hibernate validate no
--     valida constraints UNIQUE/FK (los define Flyway, como en V1/V2/V3).

CREATE TABLE movimientos (
    id                     BIGSERIAL PRIMARY KEY,
    cuenta_id              BIGINT           NOT NULL REFERENCES cuentas(id),
    tipo                   VARCHAR(22)      NOT NULL,  -- TRANSFERENCIA_ENTRANTE/SALIENTE (22), DEPOSITO (8), RETIRO (6)
    monto                  DECIMAL(19,2)    NOT NULL,
    fecha                  TIMESTAMP WITH TIME ZONE NOT NULL,
    cuenta_contraparte_id  BIGINT           REFERENCES cuentas(id)  -- nullable (A-001)
);

-- Sirve al historial (FR-005) y al rango del día del límite diario
-- (BR-004, docs/architecture/SPEC-004.md §8.10).
CREATE INDEX idx_movimientos_cuenta_fecha ON movimientos (cuenta_id, fecha);
