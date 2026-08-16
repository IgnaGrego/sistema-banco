import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import type { JwtClaims } from '../api/types';
import { guardarToken } from '../lib/session';
import { AuthProvider } from '../store/auth-context';

/**
 * Helpers compartidos de tests (diseño §5.8/§8): construcción de un JWT fake
 * (base64url, sin firma — el decodificador no la verifica, A-002), mocks de
 * `fetch` con fixtures de envelopes y render con/sin sesión dentro del
 * `AuthProvider` + `MemoryRouter`.
 */

function base64url(texto: string): string {
  const bytes = new TextEncoder().encode(texto);
  let binario = '';
  bytes.forEach((b) => {
    binario += String.fromCharCode(b);
  });
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Construye un JWT fake con el payload dado (header + payload + firma falsa). */
export function crearToken(claims: JwtClaims): string {
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64url(
    JSON.stringify({
      sub: 'usuario-test',
      role: claims.role,
      clienteId: claims.clienteId,
      iat: Math.floor(Date.now() / 1000) - 60,
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
  );
  return `${header}.${payload}.firma-falsa`;
}

export interface RespuestaMock {
  /** Substring del URL (se elige el patrón más largo para evitar colisiones). */
  url: string;
  /** Método HTTP; si se omite, responde a cualquier método. */
  method?: string;
  status?: number;
  /**
   * Cuerpo de la respuesta (envelope para errores, DTO/lista para éxito).
   * Puede ser una función evaluada en cada request (simula estado del
   * backend, p. ej. un POST que persiste y el GET posterior lo refleja).
   */
  cuerpo: unknown | (() => unknown);
}

/**
 * Stub de `fetch` que responde por URL (y opcionalmente por método HTTP).
 * Devuelve el mock para inspeccionar llamadas (URLs, headers, payloads —
 * AC-010/017/023/025).
 */
export function mockFetchRespuestas(respuestas: RespuestaMock[]): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const metodo = init?.method ?? 'GET';
    const coincidencias = respuestas.filter(
      (r) => url.includes(r.url) && (r.method === undefined || r.method === metodo),
    );
    const elegida = coincidencias.sort((a, b) => b.url.length - a.url.length)[0];
    if (elegida === undefined) {
      return Promise.reject(new TypeError(`No hay mock configurado para ${metodo} ${url}`));
    }
    const status = elegida.status ?? 200;
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () =>
        Promise.resolve(
          typeof elegida.cuerpo === 'function' ? (elegida.cuerpo as () => unknown)() : elegida.cuerpo,
        ),
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** Stub de `fetch` que responde 200 con el cuerpo dado a cualquier URL. */
export function mockFetchOk(cuerpo: unknown): ReturnType<typeof vi.fn> {
  return mockFetchRespuestas([{ url: '/api/v1', status: 200, cuerpo }]);
}

/** Stub de `fetch` que simula una falla de red (ERR-008, AC-029). */
export function mockFetchErrorRed(): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/**
 * Stub de `fetch` diferido: la request queda pendiente hasta que el test
 * resuelve (estado de carga y doble envío — AC-030).
 */
export function mockFetchDiferido(): {
  resolver: (cuerpo: unknown, status?: number) => void;
  fetchMock: ReturnType<typeof vi.fn>;
} {
  let resolver!: (respuesta: unknown) => void;
  const promesa = new Promise((res) => {
    resolver = res;
  });
  const fetchMock = vi.fn().mockReturnValue(promesa);
  vi.stubGlobal('fetch', fetchMock);
  return {
    fetchMock,
    resolver: (cuerpo: unknown, status = 200) => {
      resolver({ ok: status < 400, status, json: () => Promise.resolve(cuerpo) });
    },
  };
}

/**
 * Render con sesión: guarda el token antes de montar el `AuthProvider`
 * (FR-003 — restauración de la sesión desde localStorage).
 */
export function renderizarConSesion(ui: ReactElement, claims: JwtClaims, rutaInicial = '/') {
  guardarToken(crearToken(claims));
  return render(
    <MemoryRouter initialEntries={[rutaInicial]}>
      <AuthProvider>{ui}</AuthProvider>
    </MemoryRouter>,
  );
}

/** Render sin sesión dentro del `AuthProvider`. */
export function renderizarSinSesion(ui: ReactElement, rutaInicial = '/') {
  return render(
    <MemoryRouter initialEntries={[rutaInicial]}>
      <AuthProvider>{ui}</AuthProvider>
    </MemoryRouter>,
  );
}
