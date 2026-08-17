import type { JwtClaims, Rol } from '../api/types';

/**
 * Decodifica el payload de un JWT (segmento base64url central) SIN verificar
 * la firma (A-002): el backend verifica firma/expiración en cada request
 * (`JwtService.validar`); el frontend solo lee claims para UX (routing y
 * visibilidad — FR-004). Valida la forma del payload y devuelve `null` ante
 * cualquier payload malformado o claims inválidos → sesión inválida (ERR-002).
 */
export function decodificarJwt(token: string): JwtClaims | null {
  try {
    const segmentos = token.split('.');
    if (segmentos.length !== 3) {
      return null;
    }
    const payload = JSON.parse(decodificarBase64Url(segmentos[1]));
    if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
      return null;
    }
    const rol = payload.role as unknown;
    if (rol !== 'CLIENTE' && rol !== 'ADMIN') {
      return null;
    }
    // A-005 (SPEC-009 FR-003): el claim `sub` (= username, SPEC-003 §6.3)
    // se expone solo cuando es string; payload sin `sub` → sin clave
    // `username` (fallback R2: el header muestra solo el rol).
    const username = payload.sub as unknown;
    if (rol === 'CLIENTE') {
      // Contrato de claims (JwtService): `clienteId` (Long) solo en tokens
      // CLIENTE; un token CLIENTE sin clienteId es un payload inválido.
      const clienteId = payload.clienteId as unknown;
      if (typeof clienteId !== 'number' || !Number.isInteger(clienteId)) {
        return null;
      }
      return {
        role: rol as Rol,
        clienteId,
        ...(typeof username === 'string' ? { username } : {}),
      };
    }
    return {
      role: rol as Rol,
      ...(typeof username === 'string' ? { username } : {}),
    };
  } catch {
    return null;
  }
}

function decodificarBase64Url(segmento: string): string {
  const base64 = segmento.replace(/-/g, '+').replace(/_/g, '/');
  const relleno = '='.repeat((4 - (base64.length % 4)) % 4);
  const binario = atob(base64 + relleno);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) {
    bytes[i] = binario.charCodeAt(i);
  }
  return new TextDecoder().decode(bytes);
}
