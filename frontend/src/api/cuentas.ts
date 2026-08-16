import { request } from './httpClient';
import type { AbrirCuentaRequest, CuentaDto, MovimientoDto } from './types';

/**
 * GET /api/v1/cuentas (FR-009) sin parámetros para CLIENTE (el backend
 * resuelve el clienteId del claim) y con `?clienteId=` para ADMIN (FR-013).
 */
export async function listarCuentas(clienteId?: number): Promise<CuentaDto[]> {
  const query = clienteId !== undefined ? `?clienteId=${clienteId}` : '';
  return request<CuentaDto[]>(`/cuentas${query}`);
}

/** POST /api/v1/cuentas → 201 CuentaDto (FR-013, AC-025). */
export async function abrirCuenta(body: AbrirCuentaRequest): Promise<CuentaDto> {
  return request<CuentaDto>('/cuentas', { method: 'POST', body });
}

/** GET /api/v1/cuentas/{id}/movimientos → historial por cuenta (FR-010, AC-016). */
export async function obtenerMovimientos(cuentaId: number): Promise<MovimientoDto[]> {
  return request<MovimientoDto[]>(`/cuentas/${cuentaId}/movimientos`);
}
