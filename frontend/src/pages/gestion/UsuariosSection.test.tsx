import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import UsuariosSection from './UsuariosSection';
import { mockFetchErrorRed, mockFetchRespuestas } from '../../test/helpers';
import type { ClienteDto, UsuarioDto } from '../../api/types';

const CLIENTES: ClienteDto[] = [
  {
    id: 1,
    nombre: 'Juan',
    apellido: 'Pérez',
    dni: '30111222',
    email: 'juan@test.com',
    fechaAlta: '2026-01-10T10:00:00',
  },
];

const USUARIO_CLIENTE: UsuarioDto = { id: 5, username: 'jperez', rol: 'CLIENTE' };
const USUARIO_ADMIN: UsuarioDto = { id: 6, username: 'admin1', rol: 'ADMIN' };

function renderizarUsuarios() {
  render(<UsuariosSection />);
}

/** Espera a que el selector de clientes (rol CLIENTE) tenga opciones cargadas. */
async function esperarOpcionesDeClientes() {
  await screen.findByRole('option', { name: /Pérez, Juan/ });
}

/** Busca la llamada a POST /api/v1/auth/register en el mock de fetch. */
function llamadaRegistro(fetchMock: ReturnType<typeof vi.fn>) {
  return fetchMock.mock.calls.find(
    ([url, init]) => String(url).endsWith('/auth/register') && init?.method === 'POST',
  );
}

describe('UsuariosSection (SPEC-008 FR-001..FR-005, BR-001..BR-004, AC-001..AC-014)', () => {
  it('AC-001/AC-002 — renderiza la sección con el formulario y el selector de cliente aparece solo con rol CLIENTE', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();

    // AC-001: la sección y el formulario se renderizan al montar.
    expect(screen.getByRole('heading', { name: 'Usuarios' })).toBeInTheDocument();
    expect(screen.getByLabelText('Usuario')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña')).toBeInTheDocument();
    expect(screen.getByLabelText('Rol')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Registrar usuario' })).toBeInTheDocument();

    // El listado de clientes se consume para el selector (FR-002).
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/clientes',
      expect.objectContaining({ method: 'GET' }),
    );

    // AC-002: rol default ADMIN → el selector de cliente NO se muestra (A-003).
    expect(screen.queryByLabelText('Cliente')).not.toBeInTheDocument();

    // AC-002: al elegir rol CLIENTE aparece el selector poblado por GET /api/v1/clientes.
    await usuario.selectOptions(screen.getByLabelText('Rol'), 'CLIENTE');
    await esperarOpcionesDeClientes();
    expect(screen.getByLabelText('Cliente')).toBeInTheDocument();

    // A-003: al elegir un cliente y volver a ADMIN, el selector se oculta y la
    // selección se limpia (clienteId null).
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    await usuario.selectOptions(screen.getByLabelText('Rol'), 'ADMIN');
    expect(screen.queryByLabelText('Cliente')).not.toBeInTheDocument();
    await usuario.selectOptions(screen.getByLabelText('Rol'), 'CLIENTE');
    expect(screen.getByLabelText('Cliente')).toHaveValue('');
  });

  it('AC-003 — registro CLIENTE exitoso: payload exacto sin Authorization, confirmación con username+rol y reset', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/auth/register', method: 'POST', status: 201, cuerpo: USUARIO_CLIENTE },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.selectOptions(screen.getByLabelText('Rol'), 'CLIENTE');
    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    // Payload exacto y sin Authorization (endpoint público — autenticar: false, A-002).
    const llamada = llamadaRegistro(fetchMock);
    expect(llamada).toBeDefined();
    const init = llamada![1] as RequestInit;
    expect(init.headers).not.toHaveProperty('Authorization');
    expect(JSON.parse(init.body as string)).toEqual({
      username: 'jperez',
      password: '12345678',
      rol: 'CLIENTE',
      clienteId: 1,
    });

    // 201 → panel de confirmación con username + rol (sin password — A-005).
    expect(await screen.findByText('Usuario creado: jperez (CLIENTE)')).toBeInTheDocument();
    expect(screen.queryByText(/12345678/)).not.toBeInTheDocument();

    // "Registrar otro usuario" → formulario reseteado (FR-004).
    await usuario.click(screen.getByRole('button', { name: 'Registrar otro usuario' }));
    expect(screen.getByLabelText('Usuario')).toHaveValue('');
    expect(screen.getByLabelText('Contraseña')).toHaveValue('');
  });

  it('AC-004 — registro ADMIN exitoso: payload exacto con clienteId null y confirmación (AF-001, A-003)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/auth/register', method: 'POST', status: 201, cuerpo: USUARIO_ADMIN },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();

    // Rol default ADMIN: el selector de cliente no se muestra (A-003).
    expect(screen.queryByLabelText('Cliente')).not.toBeInTheDocument();

    await usuario.type(screen.getByLabelText('Usuario'), 'admin1');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    const llamada = llamadaRegistro(fetchMock);
    expect(llamada).toBeDefined();
    const init = llamada![1] as RequestInit;
    expect(JSON.parse(init.body as string)).toEqual({
      username: 'admin1',
      password: '12345678',
      rol: 'ADMIN',
      clienteId: null,
    });

    expect(await screen.findByText('Usuario creado: admin1 (ADMIN)')).toBeInTheDocument();
  });

  it('AC-005 — pre-validación de username: vacío o >50 caracteres → error por campo y sin request (BR-001)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');

    // username vacío → error por campo y sin request.
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(screen.getByRole('alert')).toHaveTextContent('El usuario es obligatorio');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ method: 'POST' }),
    );

    // username de 51 caracteres (fireEvent.change para saltar el maxLength=50
    // de la UI: la pre-validación sigue protegiendo, BR-001) → error por campo.
    fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: 'a'.repeat(51) } });
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(screen.getByRole('alert')).toHaveTextContent(
      'El usuario no puede superar los 50 caracteres',
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-006 — pre-validación de password: <8 caracteres bloquea; 8 caracteres (boundary) envía la request (BR-002)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/auth/register', method: 'POST', status: 201, cuerpo: USUARIO_ADMIN },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'admin1');
    await usuario.type(screen.getByLabelText('Contraseña'), '1234567'); // 7 chars

    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(screen.getByRole('alert')).toHaveTextContent(
      'La contraseña debe tener al menos 8 caracteres',
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ method: 'POST' }),
    );

    // Boundary BR-002: con 8 caracteres la request SÍ se envía.
    await usuario.clear(screen.getByLabelText('Contraseña'));
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(await screen.findByText('Usuario creado: admin1 (ADMIN)')).toBeInTheDocument();
  });

  it('AC-007 — rol CLIENTE sin cliente seleccionado → error por campo y sin request (BR-004)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.selectOptions(screen.getByLabelText('Rol'), 'CLIENTE');
    await esperarOpcionesDeClientes();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Seleccione un cliente a vincular');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-009 — 400 DATOS_INVALIDOS con details de password → error por campo y datos conservados (ERR-001)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/auth/register',
        method: 'POST',
        status: 400,
        cuerpo: {
          code: 'DATOS_INVALIDOS',
          message: 'Datos inválidos',
          // Mensaje que solo el servidor devuelve (no lo produce la
          // pre-validación de UX) para probar el mapeo details → por campo.
          details: [{ campo: 'password', mensaje: 'La password debe tener al menos 8 caracteres' }],
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    // La password pasa la pre-validación (BR-002), por lo que la request SÍ se
    // envía y el error llega del envelope 400 del backend (AC-009).
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    // El mensaje por campo proviene del details del envelope: se consulta el
    // alert del campo password (hay también un alert general con `message`).
    const campoPassword = screen.getByLabelText('Contraseña').closest('.campo') as HTMLElement;
    expect(await within(campoPassword).findByRole('alert')).toHaveTextContent(
      'La password debe tener al menos 8 caracteres',
    );
    // El `message` del envelope se muestra como error general (ERR-001).
    expect(screen.getByText('Datos inválidos')).toBeInTheDocument();
    // Los datos ingresados se conservan (ERR-001, FR-004).
    expect(screen.getByLabelText('Usuario')).toHaveValue('jperez');
    expect(screen.getByLabelText('Contraseña')).toHaveValue('12345678');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-010 — 409 CONFLICTO_UNICIDAD con details de username → error por campo, datos conservados y reintento (ERR-002, AF-002)', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/auth/register',
        method: 'POST',
        status: 409,
        cuerpo: {
          code: 'CONFLICTO_UNICIDAD',
          message: 'Ya existe un usuario con ese username',
          details: [{ campo: 'username', mensaje: 'Ya existe un usuario con ese username' }],
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    const campoUsername = screen.getByLabelText('Usuario').closest('.campo') as HTMLElement;
    expect(await within(campoUsername).findByRole('alert')).toHaveTextContent(
      'Ya existe un usuario con ese username',
    );
    // Los datos ingresados se conservan para corregir (ERR-002, AF-002).
    expect(screen.getByLabelText('Usuario')).toHaveValue('jperez');

    // Reintento con el mock restaurado a 201 → éxito (AF-002). El username del
    // panel proviene del `UsuarioDto` de la respuesta (no del input).
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/auth/register', method: 'POST', status: 201, cuerpo: USUARIO_ADMIN },
    ]);
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(await screen.findByText('Usuario creado: admin1 (ADMIN)')).toBeInTheDocument();
  });

  it('AC-011 — 404 CLIENTE_NO_ENCONTRADO → mensaje del envelope general (ERR-003)', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/auth/register',
        method: 'POST',
        status: 404,
        cuerpo: { code: 'CLIENTE_NO_ENCONTRADO', message: 'El cliente no existe' },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    expect(await screen.findByText('El cliente no existe')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('El cliente no existe');
    // Los datos se conservan (ERR-003).
    expect(screen.getByLabelText('Usuario')).toHaveValue('jperez');
  });

  it('AC-012 — 500 ERROR_INTERNO → mensaje del envelope general y reintento posible (ERR-004, AF-003)', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/auth/register',
        method: 'POST',
        status: 500,
        cuerpo: { code: 'ERROR_INTERNO', message: 'Error interno del servidor' },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    expect(await screen.findByText('Error interno del servidor')).toBeInTheDocument();
    // Los datos se conservan y el reintento (mock restaurado a 201) tiene éxito.
    expect(screen.getByLabelText('Usuario')).toHaveValue('jperez');
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/auth/register', method: 'POST', status: 201, cuerpo: USUARIO_ADMIN },
    ]);
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(await screen.findByText('Usuario creado: admin1 (ADMIN)')).toBeInTheDocument();
  });

  it('AC-013 — error de red: MENSAJE_ERROR_RED y la operación puede reintentarse (ERR-005, AF-003)', async () => {
    mockFetchRespuestas([{ url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES }]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');

    mockFetchErrorRed();
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(
      await screen.findByText(/No se pudo conectar con el servidor/),
    ).toBeInTheDocument();

    // Reintento con mock restaurado → éxito (AF-003).
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/auth/register', method: 'POST', status: 201, cuerpo: USUARIO_ADMIN },
    ]);
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));
    expect(await screen.findByText('Usuario creado: admin1 (ADMIN)')).toBeInTheDocument();
  });

  it('AC-014 — doble envío: botón deshabilitado durante la request y una sola request (FR-004)', async () => {
    const { resolverPost, fetchMock } = mockFetchUsuariosDiferido();
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    expect(screen.getByRole('button', { name: 'Registrando...' })).toBeDisabled();
    // Intento de doble envío bloqueado (AC-014).
    await usuario.click(screen.getByRole('button', { name: 'Registrando...' })).catch(
      () => undefined,
    );
    const llamadasPost = fetchMock.mock.calls.filter(
      ([url, init]) => String(url).endsWith('/auth/register') && init?.method === 'POST',
    );
    expect(llamadasPost).toHaveLength(1);

    resolverPost(USUARIO_ADMIN);
    expect(await screen.findByText('Usuario creado: admin1 (ADMIN)')).toBeInTheDocument();
  });

  it('AC-002 — rol CLIENTE con listado vacío: estado vacío y envío bloqueado (BR-004)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: [] },
    ]);
    const usuario = userEvent.setup();

    renderizarUsuarios();
    await usuario.selectOptions(screen.getByLabelText('Rol'), 'CLIENTE');
    expect(await screen.findByText('No hay clientes registrados.')).toBeInTheDocument();

    await usuario.type(screen.getByLabelText('Usuario'), 'jperez');
    await usuario.type(screen.getByLabelText('Contraseña'), '12345678');
    await usuario.click(screen.getByRole('button', { name: 'Registrar usuario' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Seleccione un cliente a vincular');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-002 — rol CLIENTE con el listado cargando: se muestra Cargando solo cuando el selector es visible', async () => {
    const { resolverClientes } = mockFetchUsuariosDiferido();
    const usuario = userEvent.setup();

    renderizarUsuarios();

    // Rol default ADMIN: no hay Cargando del selector (está oculto — A-003).
    expect(screen.queryByText('Cargando...')).not.toBeInTheDocument();

    await usuario.selectOptions(screen.getByLabelText('Rol'), 'CLIENTE');
    expect(screen.getByText('Cargando...')).toBeInTheDocument();

    resolverClientes(CLIENTES);
    expect(await screen.findByRole('option', { name: /Pérez, Juan/ })).toBeInTheDocument();
    expect(screen.queryByText('Cargando...')).not.toBeInTheDocument();
  });
});

/**
 * Mock de `fetch` que difiere el GET de clientes y el POST del registro
 * (AC-014: doble envío; Cargando del selector mientras carga el listado).
 */
function mockFetchUsuariosDiferido() {
  let resolverClientes!: (r: unknown) => void;
  let resolverPost!: (r: unknown) => void;
  const promesaClientes = new Promise((res) => {
    resolverClientes = res;
  });
  const promesaPost = new Promise((res) => {
    resolverPost = res;
  });
  const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const metodo = init?.method ?? 'GET';
    if (String(url).includes('/api/v1/clientes') && metodo === 'GET') {
      return promesaClientes;
    }
    if (String(url).endsWith('/auth/register') && metodo === 'POST') {
      return promesaPost;
    }
    return Promise.reject(new TypeError(`No hay mock para ${metodo} ${url}`));
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    fetchMock,
    resolverClientes: (cuerpo: unknown) => {
      resolverClientes({ ok: true, status: 200, json: () => Promise.resolve(cuerpo) });
    },
    resolverPost: (cuerpo: unknown, status = 201) => {
      resolverPost({ ok: status < 400, status, json: () => Promise.resolve(cuerpo) });
    },
  };
}