import { leerToken } from '../lib/session';
import type { DetalleError, ErrorEnvelope } from './types';

/** Base relativa de la API: same-origin vía el proxy de Vite en dev (FR-008). */
const BASE_URL = '/api/v1';

/** Mensaje de conexión para errores de red (ERR-008, AC-029). */
export const MENSAJE_ERROR_RED =
  'No se pudo conectar con el servidor. Verifique su conexión e intente nuevamente.';

/**
 * Error tipado a partir del envelope estándar `{ code, message, details? }`.
 * `status` es el código HTTP (0 para errores de red).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: DetalleError[];

  constructor(status: number, code: string, message: string, details?: DetalleError[]) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

/** Callback invocado ante un 401 en una request autenticada (FR-007). */
let onNoAutorizado: (() => void) | null = null;

export function setOnNoAutorizado(callback: (() => void) | null): void {
  onNoAutorizado = callback;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  body?: unknown;
  /** false solo para el login (su 401 es el error esperado de credenciales — ERR-001 vs ERR-002). */
  autenticar?: boolean;
}

/**
 * Wrapper de `fetch` para `/api/v1` (FR-002, BR-009):
 * - Adjunta `Authorization: Bearer <token>` solo cuando `autenticar` y existe
 *   sesión; el token NUNCA se adjunta a URLs fuera de `/api/v1` (la base es
 *   fija y relativa) ni se loguea (AC-031).
 * - Respuestas no-OK → `ApiError` tipado con el envelope parseado.
 * - 401 en request autenticada → invoca el callback `onNoAutorizado`
 *   (registrado por `AuthProvider` → limpieza de sesión y redirect a /login).
 * - Error de red → `ApiError` con `code === 'ERROR_RED'`.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, autenticar = true } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (autenticar) {
    const token = leerToken();
    if (token !== null) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'ERROR_RED', MENSAJE_ERROR_RED);
  }

  if (!response.ok) {
    const error = await parsearError(response);
    if (autenticar && response.status === 401 && onNoAutorizado !== null) {
      onNoAutorizado();
    }
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function parsearError(response: Response): Promise<ApiError> {
  let envelope: Partial<ErrorEnvelope> = {};
  try {
    const cuerpo = (await response.json()) as unknown;
    if (cuerpo !== null && typeof cuerpo === 'object') {
      envelope = cuerpo as Partial<ErrorEnvelope>;
    }
  } catch {
    // Cuerpo no-JSON: se usa el fallback genérico.
  }
  if (response.status >= 500 && envelope.code === undefined) {
    return new ApiError(response.status, 'ERROR_INTERNO', 'Error interno del servidor');
  }
  return new ApiError(
    response.status,
    envelope.code ?? 'ERROR_DESCONOCIDO',
    envelope.message ?? 'Error inesperado',
    envelope.details,
  );
}
