import { request } from './httpClient';
import type { DepositoConfirmacion, DepositoRequest, RetiroConfirmacion, RetiroRequest } from './types';

/**
 * POST /api/v1/depositos → 201 DepositoConfirmacion (SPEC-005 FR-001; exclusivo
 * ADMIN — la caja de SPEC-007 solo se expone en /gestion, FR-003/AC-004).
 */
export async function depositar(body: DepositoRequest): Promise<DepositoConfirmacion> {
  return request<DepositoConfirmacion>('/depositos', { method: 'POST', body });
}

/**
 * POST /api/v1/retiros → 201 RetiroConfirmacion (SPEC-005 FR-002; la SPA lo
 * llama solo con token ADMIN — retiro propio del CLIENTE fuera de alcance,
 * SPEC-007 §12, FR-004/AC-005).
 */
export async function retirar(body: RetiroRequest): Promise<RetiroConfirmacion> {
  return request<RetiroConfirmacion>('/retiros', { method: 'POST', body });
}