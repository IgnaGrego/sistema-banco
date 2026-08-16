-- V3__cuentas_y_movimientos.sql
-- Agregado Cuenta mínimo (SPEC-004, A-001) + Movimiento. Compatible con
-- ddl-auto: validate (lecciones de V1/V2):
--   * Instant -> TIMESTAMP WITH TIME ZONE (lección V1/TIMESTAMPTZ: Hibernate 6
--     mapea Instant a "timestamp(6) with time zone"; un TIMESTAMP simple
--     fallaría la validación).
--   * BIGSERIAL <-> Long @Id, BIGINT <-> Long, DECIMAL(19,2) <-> BigDecimal,
--     VARCHAR(n) <-> @Column(length=n). Hibernate validate no valida
--     constraints UNIQUE/FK (los define Flyway, como en V1/V2).

CREATE TABLE cuentas (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  BIGINT           NOT NULL REFERENCES clientes(id),
    cbu         VARCHAR(22)      NOT NULL,
    tipo        VARCHAR(15)      NOT NULL,   -- CAJA_AHORRO (11) | CUENTA_CORRIENTE (15)
    saldo       DECIMAL(19,2)    NOT NULL,
    moneda      VARCHAR(3)       NOT NULL,   -- ARS
    estado      VARCHAR(9)       NOT NULL,   -- ACTIVA (6) | BLOQUEADA (9)
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT           NOT NULL DEFAULT 0   -- @Version (BR-006)
);

ALTER TABLE cuentas ADD CONSTRAINT uq_cuentas_cbu UNIQUE (cbu);

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
