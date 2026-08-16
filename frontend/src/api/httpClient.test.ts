import { ApiError, MENSAJE_ERROR_RED, request, setOnNoAutorizado } from './httpClient';
import { guardarToken } from '../lib/session';

function respuestaFetch(status: number, cuerpo: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(cuerpo),
  };
}

describe('httpClient (FR-002, BR-009, AC-010, AC-011, AC-031)', () => {
  afterEach(() => {
    setOnNoAutorizado(null);
  });

  describe('Authorization Bearer (AC-010)', () => {
    it('adjunta Authorization: Bearer <token> a /api/v1 cuando existe sesión', async () => {
      guardarToken('token-secreto');
      const fetchMock = vi.fn().mockResolvedValue(respuestaFetch(200, { id: 1 }));
      vi.stubGlobal('fetch', fetchMock);

      await request('/cuentas');

      const [url, init] = fetchMock.mock.calls[0];
      expect(url).toBe('/api/v1/cuentas');
      expect(init.headers.Authorization).toBe('Bearer token-secreto');
    });

    it('no adjunta token cuando no hay sesión', async () => {
      const fetchMock = vi.fn().mockResolvedValue(respuestaFetch(200, []));
      vi.stubGlobal('fetch', fetchMock);

      await request('/cuentas');

      const [, init] = fetchMock.mock.calls[0];
      expect(init.headers.Authorization).toBeUndefined();
    });

    it('la base es fija y relativa (/api/v1): el token nunca viaja a otras URLs (BR-009)', async () => {
      guardarToken('token-secreto');
      const fetchMock = vi.fn().mockResolvedValue(respuestaFetch(200, {}));
      vi.stubGlobal('fetch', fetchMock);

      await request('/transferencias', { method: 'POST', body: { monto: 1 } });

      const [url, init] = fetchMock.mock.calls[0];
      expect(url.startsWith('/api/v1')).toBe(true);
      expect(url).not.toMatch(/^https?:\/\//);
      expect(init.headers.Authorization).toBe('Bearer token-secreto');
    });

    it('no adjunta token al login (autenticar: false)', async () => {
      const fetchMock = vi.fn().mockResolvedValue(respuestaFetch(200, { token: 't' }));
      vi.stubGlobal('fetch', fetchMock);

      await request('/auth/login', {
        method: 'POST',
        body: { username: 'u', password: 'p' },
        autenticar: false,
      });

      const [, init] = fetchMock.mock.calls[0];
      expect(init.headers.Authorization).toBeUndefined();
    });

    it('envía Content-Type application/json y el body serializado cuando hay payload', async () => {
      const fetchMock = vi.fn().mockResolvedValue(respuestaFetch(200, {}));
      vi.stubGlobal('fetch', fetchMock);

      await request('/clientes', { method: 'POST', body: { nombre: 'Juan' } });

      const [, init] = fetchMock.mock.calls[0];
      expect(init.headers['Content-Type']).toBe('application/json');
      expect(JSON.parse(init.body)).toEqual({ nombre: 'Juan' });
    });
  });

  describe('manejo de 401 (FR-007, AC-011)', () => {
    it('401 en una request autenticada invoca el callback onNoAutorizado y lanza ApiError', async () => {
      guardarToken('token-viejo');
      const onNoAutorizado = vi.fn();
      setOnNoAutorizado(onNoAutorizado);
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(respuestaFetch(401, { code: 'NO_AUTENTICADO', message: 'Token ausente o inválido' })),
      );

      await expect(request('/cuentas')).rejects.toBeInstanceOf(ApiError);
      expect(onNoAutorizado).toHaveBeenCalledTimes(1);
    });

    it('401 del login (autenticar: false) NO invoca el callback (ERR-001 vs ERR-002)', async () => {
      const onNoAutorizado = vi.fn();
      setOnNoAutorizado(onNoAutorizado);
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(respuestaFetch(401, { code: 'NO_AUTENTICADO', message: 'Credenciales inválidas' })),
      );

      const err = await request('/auth/login', {
        method: 'POST',
        body: { username: 'u', password: 'p' },
        autenticar: false,
      }).catch((e: unknown) => e);

      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).code).toBe('NO_AUTENTICADO');
      expect(onNoAutorizado).not.toHaveBeenCalled();
    });
  });

  describe('envelope de errores', () => {
    it('parsea el envelope con details en un ApiError tipado', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          respuestaFetch(400, {
            code: 'DATOS_INVALIDOS',
            message: 'Datos inválidos',
            details: [{ campo: 'dni', mensaje: 'El DNI debe tener 7-8 dígitos' }],
          }),
        ),
      );

      const err = await request('/clientes', { method: 'POST', body: {} }).catch((e: unknown) => e);

      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).status).toBe(400);
      expect((err as ApiError).code).toBe('DATOS_INVALIDOS');
      expect((err as ApiError).details).toEqual([{ campo: 'dni', mensaje: 'El DNI debe tener 7-8 dígitos' }]);
    });

    it('5xx con envelope → se muestra el mensaje del envelope', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(respuestaFetch(500, { code: 'ERROR_INTERNO', message: 'Error interno del servidor' })),
      );

      const err = await request('/cuentas').catch((e: unknown) => e);
      expect((err as ApiError).code).toBe('ERROR_INTERNO');
      expect((err as ApiError).message).toBe('Error interno del servidor');
    });

    it('5xx sin envelope → mensaje genérico de error interno', async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        json: () => Promise.reject(new Error('sin cuerpo')),
      });
      vi.stubGlobal('fetch', fetchMock);

      const err = await request('/cuentas').catch((e: unknown) => e);
      expect((err as ApiError).status).toBe(502);
      expect((err as ApiError).code).toBe('ERROR_INTERNO');
    });
  });

  describe('errores de red (ERR-008, AC-029)', () => {
    it('fetch rechazado → ApiError con code ERROR_RED y mensaje de conexión', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

      const err = await request('/cuentas').catch((e: unknown) => e);

      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).status).toBe(0);
      expect((err as ApiError).code).toBe('ERROR_RED');
      expect((err as ApiError).message).toBe(MENSAJE_ERROR_RED);
    });
  });

  describe('AC-031 — el token no se expone en logs', () => {
    it('el cliente HTTP no loguea el token (ni el header completo)', async () => {
      guardarToken('token-super-secreto');
      const spyLog = vi.spyOn(console, 'log').mockImplementation(() => undefined);
      const fetchMock = vi.fn().mockResolvedValue(respuestaFetch(200, []));
      vi.stubGlobal('fetch', fetchMock);

      await request('/cuentas');

      expect(spyLog).not.toHaveBeenCalledWith(expect.stringContaining('token-super-secreto'));
      expect(spyLog).not.toHaveBeenCalledWith(expect.stringContaining('Authorization'));
      spyLog.mockRestore();
    });
  });
});
