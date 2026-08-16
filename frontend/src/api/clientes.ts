import { request } from './httpClient';
import type { ActualizarClienteRequest, ClienteDto, CrearClienteRequest } from './types';

/** GET /api/v1/clientes (FR-012, AC-022). */
export async function listarClientes(): Promise<ClienteDto[]> {
  return request<ClienteDto[]>('/clientes');
}

/** POST /api/v1/clientes → 201 ClienteDto (FR-012, AC-023). */
export async function crearCliente(body: CrearClienteRequest): Promise<ClienteDto> {
  return request<ClienteDto>('/clientes', { method: 'POST', body });
}

/** PUT /api/v1/clientes/{id} → 200 ClienteDto actualizado (FR-012, AC-024). */
export async function actualizarCliente(
  id: number,
  body: ActualizarClienteRequest,
): Promise<ClienteDto> {
  return request<ClienteDto>(`/clientes/${id}`, { method: 'PUT', body });
}
