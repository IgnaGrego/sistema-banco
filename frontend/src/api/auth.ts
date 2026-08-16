import { request } from './httpClient';
import type { LoginRequest, LoginResponse } from './types';

/**
 * POST /api/v1/auth/login → 200 {token} (FR-005). Único call-site con
 * `autenticar: false`: el 401 del login es el error esperado de credenciales
 * (ERR-001) y NO debe disparar la limpieza de sesión (ERR-002 vs ERR-001,
 * diseño §5.2).
 */
export async function login(body: LoginRequest): Promise<string> {
  const respuesta = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    body,
    autenticar: false,
  });
  return respuesta.token;
}
