/**
 * Tipos TS que espejan los DTOs del backend (verificados contra
 * `infrastructure/adapter/web` — diseño SPEC-006 §5.2). El `Instant` viaja
 * como string ISO-8601 y el `BigDecimal` como número JSON: el frontend solo
 * formatea (BR-010), nunca calcula montos.
 */

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
}

export interface ClienteDto {
  id: number;
  nombre: string;
  apellido: string;
  dni: string;
  email: string;
  telefono?: string;
  fechaAlta: string;
}

export interface CrearClienteRequest {
  nombre: string;
  apellido: string;
  dni: string;
  email: string;
  telefono?: string | null;
}

export type ActualizarClienteRequest = CrearClienteRequest;

export type TipoCuenta = 'CAJA_AHORRO' | 'CUENTA_CORRIENTE';
export type EstadoCuenta = 'ACTIVA' | 'BLOQUEADA';
export type Rol = 'CLIENTE' | 'ADMIN';

export interface CuentaDto {
  id: number;
  clienteId: number;
  cbu: string;
  tipo: TipoCuenta;
  saldo: number;
  moneda: string;
  estado: EstadoCuenta;
  createdAt: string;
}

export interface AbrirCuentaRequest {
  clienteId: number;
  tipo: TipoCuenta;
  moneda?: string;
}

export type TipoMovimiento =
  | 'DEPOSITO'
  | 'RETIRO'
  | 'TRANSFERENCIA_ENTRANTE'
  | 'TRANSFERENCIA_SALIENTE';

export interface MovimientoDto {
  id: number;
  cuentaId: number;
  tipo: TipoMovimiento;
  monto: number;
  moneda: string;
  fecha: string;
  cuentaContraparteId: number | null;
}

export interface TransferirRequest {
  cuentaOrigenId: number;
  cbuDestino: string;
  monto: number;
}

export interface TransferenciaConfirmacion {
  idTransferencia: number;
  monto: number;
  cbuDestino: string;
  fechaHora: string;
}

/** POST /api/v1/depositos (SPEC-005 FR-001). Espejo de `DepositoRequest(Long cuentaId, BigDecimal monto)`. */
export interface DepositoRequest {
  cuentaId: number;
  monto: number;
}

/** POST /api/v1/retiros (SPEC-005 FR-002). Espejo de `RetiroRequest(Long cuentaId, BigDecimal monto)`. */
export interface RetiroRequest {
  cuentaId: number;
  monto: number;
}

/** 201 de POST /api/v1/depositos. Espejo de `DepositoConfirmacion(Long idMovimiento, Long cuentaId, BigDecimal monto, Instant fechaHora)`. */
export interface DepositoConfirmacion {
  idMovimiento: number;
  cuentaId: number;
  monto: number;
  fechaHora: string;
}

/** 201 de POST /api/v1/retiros. Espejo de `RetiroConfirmacion(...)` (misma forma). */
export interface RetiroConfirmacion {
  idMovimiento: number;
  cuentaId: number;
  monto: number;
  fechaHora: string;
}

export interface DetalleError {
  campo: string;
  mensaje: string;
}

/** Envelope de error estándar `{ code, message, details? }` (ARCHITECTURE.md §7). */
export interface ErrorEnvelope {
  code: string;
  message: string;
  details?: DetalleError[];
}

/** Claims del JWT que el frontend usa para UX (role/clienteId — FR-004, A-002). */
export interface JwtClaims {
  role: Rol;
  clienteId?: number;
}
