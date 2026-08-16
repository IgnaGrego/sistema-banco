import { request } from './httpClient';
import type { LoginRequest, LoginResponse, RegistrarUsuarioRequest, UsuarioDto } from './types';

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

/**
 * POST /api/v1/auth/register → 201 UsuarioDto (SPEC-003 FR-001; A-002).
 * Endpoint PÚBLICO (permitAll — SecurityConfig): se declara `autenticar: false`
 * (espejo de `login()` en el mismo módulo) para no adjuntar un header
 * Authorization innecesario. El endpoint nunca devuelve 401, así que no
 * dispara la limpieza global de sesión del httpClient.
 */
export async function registro(body: RegistrarUsuarioRequest): Promise<UsuarioDto> {
  return request<UsuarioDto>('/auth/register', {
    method: 'POST',
    body,
    autenticar: false,
  });
}
