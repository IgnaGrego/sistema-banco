import { request } from './httpClient';
import type { TransferenciaConfirmacion, TransferirRequest } from './types';

/** POST /api/v1/transferencias → 201 TransferenciaConfirmacion (FR-011, AC-017). */
export async function transferir(body: TransferirRequest): Promise<TransferenciaConfirmacion> {
  return request<TransferenciaConfirmacion>('/transferencias', { method: 'POST', body });
}
