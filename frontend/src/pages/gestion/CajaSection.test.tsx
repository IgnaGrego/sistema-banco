import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CajaSection from './CajaSection';
import { mockFetchErrorRed, mockFetchRespuestas } from '../../test/helpers';
import { formatearMontoARS } from '../../lib/session';
import type { ClienteDto, CuentaDto } from '../../api/types';

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

const CUENTAS: CuentaDto[] = [
  {
    id: 1,
    clienteId: 1,
    cbu: '0000003100000000000001',
    tipo: 'CAJA_AHORRO',
    saldo: 1000,
    moneda: 'ARS',
    estado: 'ACTIVA',
    createdAt: '2026-08-10T12:00:00',
  },
  {
    id: 2,
    clienteId: 1,
    cbu: '0000003100000000000002',
    tipo: 'CUENTA_CORRIENTE',
    saldo: 500,
    moneda: 'ARS',
    estado: 'BLOQUEADA',
    createdAt: '2026-08-10T12:00:00',
  },
];

function renderizarCaja() {
  render(<CajaSection />);
}

/** Acota las queries a la sección del formulario por su heading (h3). */
function seccionDe(titulo: string) {
  const heading = screen.getByRole('heading', { name: titulo });
  return within(heading.closest('section') as HTMLElement);
}

/** Espera a que el selector de clientes tenga opciones cargadas. */
async function esperarOpcionesDeClientes() {
  await screen.findByRole('option', { name: /Pérez, Juan/ });
}

async function seleccionarCliente(usuario: ReturnType<typeof userEvent.setup>) {
  await esperarOpcionesDeClientes();
  await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
  await screen.findByText('CBU: 0000003100000000000001');
}

describe('CajaSection (SPEC-007 FR-001..FR-007, BR-001..BR-003, AC-001..AC-018)', () => {
  it('AC-001/AC-002 — renderiza la sección, lista cuentas por cliente y ofrece solo cuentas ACTIVA en los selectores', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();

    // AC-001: la sección y el selector de cliente se renderizan al montar.
    expect(screen.getByRole('heading', { name: 'Caja' })).toBeInTheDocument();
    expect(screen.getByLabelText('Cliente')).toBeInTheDocument();

    await seleccionarCliente(usuario);

    // AC-001: los formularios de depósito/retiro se renderizan al elegir cliente.
    expect(screen.getByRole('heading', { name: 'Depósito' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Retiro' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Depositar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retirar' })).toBeInTheDocument();

    // AC-002: GET /api/v1/cuentas?clienteId=1.
    const llamadaCuentas = fetchMock.mock.calls.find(([url]) =>
      String(url).startsWith('/api/v1/cuentas?'),
    );
    expect(String(llamadaCuentas![0])).toBe('/api/v1/cuentas?clienteId=1');

    // La lista muestra ambas cuentas (la BLOQUEADA también — FR-002).
    expect(screen.getByText('CBU: 0000003100000000000001')).toBeInTheDocument();
    expect(screen.getByText('CBU: 0000003100000000000002')).toBeInTheDocument();
    expect(screen.getAllByText('BLOQUEADA').length).toBeGreaterThanOrEqual(1);

    // BR-003: solo la cuenta ACTIVA se ofrece como opción en AMBOS formularios.
    for (const seccion of ['Depósito', 'Retiro']) {
      const form = seccionDe(seccion);
      expect(form.getByRole('option', { name: /CBU 0000003100000000000001/ })).toBeInTheDocument();
      expect(
        form.queryByRole('option', { name: /CBU 0000003100000000000002/ }),
      ).not.toBeInTheDocument();
    }
  });

  it('AC-004/AC-006 — depósito exitoso: payload exacto, confirmación y saldo refrescado del CuentaDto', async () => {
    // Mock con estado: el POST persiste el nuevo saldo y el GET posterior
    // (refresh) lo refleja — la UI no calcula (BR-004, AC-006).
    let cuentasBackend: CuentaDto[] = [
      { ...CUENTAS[0], saldo: 1000 },
      { ...CUENTAS[1], saldo: 500 },
    ];
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: () => cuentasBackend },
      {
        url: '/api/v1/depositos',
        method: 'POST',
        status: 201,
        cuerpo: () => {
          cuentasBackend = cuentasBackend.map((c) =>
            c.id === 1 ? { ...c, saldo: c.saldo + 100 } : c,
          );
          return {
            idMovimiento: 77,
            cuentaId: 1,
            monto: 100,
            fechaHora: '2026-08-16T14:30:00',
          };
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);
    expect(screen.getByText(formatearMontoARS(1000))).toBeInTheDocument();

    const dep = seccionDe('Depósito');
    await usuario.selectOptions(dep.getByLabelText('Cuenta destino'), '1');
    await usuario.type(dep.getByLabelText('Monto depósito'), '100');
    await usuario.click(dep.getByRole('button', { name: 'Depositar' }));

    // Panel de confirmación (FR-005).
    expect(await dep.findByText('Depósito realizado')).toBeInTheDocument();
    expect(dep.getByText('ID de movimiento: 77')).toBeInTheDocument();
    expect(dep.getByText(/Monto: \$ 100,00/)).toBeInTheDocument();
    expect(dep.getByText(/Fecha y hora: 16\/08\/2026 14:30/)).toBeInTheDocument();
    expect(dep.getByText(/Cuenta \(CBU\): 0000003100000000000001/)).toBeInTheDocument();

    // Payload exacto (AC-004).
    const llamadaPost = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/v1/depositos' && init?.method === 'POST',
    );
    expect(llamadaPost).toBeDefined();
    expect(JSON.parse((llamadaPost![1] as RequestInit).body as string)).toEqual({
      cuentaId: 1,
      monto: 100,
    });

    // Refresh: el saldo mostrado proviene del CuentaDto refrescado (AC-006, BR-004).
    expect(await screen.findByText(formatearMontoARS(1100))).toBeInTheDocument();

    // Independencia de formularios: el retiro sigue mostrando su formulario.
    expect(seccionDe('Retiro').getByRole('button', { name: 'Retirar' })).toBeInTheDocument();
  });

  it('AC-005 — retiro exitoso: payload exacto, confirmación y saldo refrescado', async () => {
    let cuentasBackend: CuentaDto[] = [
      { ...CUENTAS[0], saldo: 1000 },
      { ...CUENTAS[1], saldo: 500 },
    ];
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: () => cuentasBackend },
      {
        url: '/api/v1/retiros',
        method: 'POST',
        status: 201,
        cuerpo: () => {
          cuentasBackend = cuentasBackend.map((c) =>
            c.id === 1 ? { ...c, saldo: c.saldo - 100 } : c,
          );
          return {
            idMovimiento: 88,
            cuentaId: 1,
            monto: 100,
            fechaHora: '2026-08-16T15:00:00',
          };
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);

    const ret = seccionDe('Retiro');
    await usuario.selectOptions(ret.getByLabelText('Cuenta destino'), '1');
    await usuario.type(ret.getByLabelText('Monto retiro'), '100');
    await usuario.click(ret.getByRole('button', { name: 'Retirar' }));

    expect(await ret.findByText('Retiro realizado')).toBeInTheDocument();
    expect(ret.getByText('ID de movimiento: 88')).toBeInTheDocument();

    const llamadaPost = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/v1/retiros' && init?.method === 'POST',
    );
    expect(llamadaPost).toBeDefined();
    expect(JSON.parse((llamadaPost![1] as RequestInit).body as string)).toEqual({
      cuentaId: 1,
      monto: 100,
    });

    expect(await screen.findByText(formatearMontoARS(900))).toBeInTheDocument();
  });

  it('AC-007 — pre-validación del monto en ambos formularios bloquea el envío sin request', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);

    for (const [titulo, boton, labelMonto, mensaje] of [
      ['Depósito', 'Depositar', 'Monto depósito', 'El monto debe ser un número con hasta 2 decimales'],
      ['Retiro', 'Retirar', 'Monto retiro', 'El monto debe ser un número con hasta 2 decimales'],
    ] as const) {
      const form = seccionDe(titulo);
      await usuario.selectOptions(form.getByLabelText('Cuenta destino'), '1');
      // monto vacío
      await usuario.click(form.getByRole('button', { name: boton }));
      expect(form.getByRole('alert')).toHaveTextContent('El monto es obligatorio');
      // no numérico
      await usuario.type(form.getByLabelText(labelMonto), 'abc');
      await usuario.click(form.getByRole('button', { name: boton }));
      expect(form.getByRole('alert')).toHaveTextContent(mensaje);
      // 0 / negativo / 3 decimales
      await usuario.clear(form.getByLabelText(labelMonto));
      await usuario.type(form.getByLabelText(labelMonto), '0');
      await usuario.click(form.getByRole('button', { name: boton }));
      expect(form.getByRole('alert')).toHaveTextContent('El monto debe ser mayor a 0');
      await usuario.clear(form.getByLabelText(labelMonto));
      await usuario.type(form.getByLabelText(labelMonto), '-5');
      await usuario.click(form.getByRole('button', { name: boton }));
      // "-5" no cumple REGEX_MONTO (no admite signo) → mensaje de número.
      expect(form.getByRole('alert')).toHaveTextContent(mensaje);
      await usuario.clear(form.getByLabelText(labelMonto));
      await usuario.type(form.getByLabelText(labelMonto), '10.555');
      await usuario.click(form.getByRole('button', { name: boton }));
      expect(form.getByRole('alert')).toHaveTextContent(mensaje);
    }

    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/depositos',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/retiros',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-008 — retiro: monto mayor al saldo → error por campo y sin request', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);

    const ret = seccionDe('Retiro');
    await usuario.selectOptions(ret.getByLabelText('Cuenta destino'), '1');
    await usuario.type(ret.getByLabelText('Monto retiro'), '1500');
    await usuario.click(ret.getByRole('button', { name: 'Retirar' }));

    expect(ret.getByRole('alert')).toHaveTextContent('El saldo no es suficiente para el retiro');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/retiros',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-009 — sin cuenta seleccionada → error por campo y sin request', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);

    const dep = seccionDe('Depósito');
    await usuario.type(dep.getByLabelText('Monto depósito'), '100');
    await usuario.click(dep.getByRole('button', { name: 'Depositar' }));

    // role=alert porque el placeholder del selector tiene el mismo texto.
    expect(dep.getByRole('alert')).toHaveTextContent('Seleccione la cuenta');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/depositos',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-011 — 400 DATOS_INVALIDOS con details de monto → error por campo y datos conservados', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
      {
        url: '/api/v1/depositos',
        method: 'POST',
        status: 400,
        cuerpo: {
          code: 'DATOS_INVALIDOS',
          message: 'Datos inválidos',
          // Mensaje que solo el servidor devuelve (no lo produce la
          // pre-validación de UX) para probar el mapeo details → por campo.
          details: [{ campo: 'monto', mensaje: 'El monto supera el límite permitido' }],
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);

    const dep = seccionDe('Depósito');
    await usuario.selectOptions(dep.getByLabelText('Cuenta destino'), '1');
    // El monto pasa la pre-validación (BR-001), por lo que la request SÍ se
    // envía y el error llega del envelope 400 del backend (AC-011).
    await usuario.type(dep.getByLabelText('Monto depósito'), '100');
    await usuario.click(dep.getByRole('button', { name: 'Depositar' }));

    // El mensaje por campo proviene del details del envelope (no de la UX):
    // se consulta el alert del campo monto (hay también un alert general con
    // `message` del envelope, por eso se acota al contenedor del campo).
    const campoMonto = dep.getByLabelText('Monto depósito').closest('.campo') as HTMLElement;
    expect(await within(campoMonto).findByRole('alert')).toHaveTextContent(
      'El monto supera el límite permitido',
    );
    // Los datos ingresados se conservan (ERR-001).
    expect(dep.getByLabelText('Monto depósito')).toHaveValue('100');
    // La request se envió al backend.
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/depositos',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it.each([
    ['AC-012', 422, 'SALDO_INSUFICIENTE', 'Saldo insuficiente en la cuenta'],
    ['AC-013', 422, 'CUENTA_BLOQUEADA', 'La cuenta está bloqueada'],
    ['AC-014', 404, 'CUENTA_NO_ENCONTRADA', 'La cuenta no existe'],
    ['AC-015', 409, 'CONFLICTO_CONCURRENCIA', 'Conflicto de concurrencia'],
    ['AC-016', 403, 'ACCESO_DENEGADO', 'No tiene permisos para realizar esta operación'],
  ] as const)(
    '%s — el error %s del envelope se muestra como mensaje general',
    async (_ac, status, code, message) => {
      mockFetchRespuestas([
        { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
        { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
        { url: '/api/v1/depositos', method: 'POST', status, cuerpo: { code, message } },
      ]);
      const usuario = userEvent.setup();

      renderizarCaja();
      await seleccionarCliente(usuario);

      const dep = seccionDe('Depósito');
      await usuario.selectOptions(dep.getByLabelText('Cuenta destino'), '1');
      await usuario.type(dep.getByLabelText('Monto depósito'), '100');
      await usuario.click(dep.getByRole('button', { name: 'Depositar' }));

      expect(await dep.findByText(message)).toBeInTheDocument();
    },
  );

  it('AC-017 — error de red: MENSAJE_ERROR_RED y la operación puede reintentarse', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await seleccionarCliente(usuario);

    const dep = seccionDe('Depósito');
    await usuario.selectOptions(dep.getByLabelText('Cuenta destino'), '1');
    await usuario.type(dep.getByLabelText('Monto depósito'), '100');

    mockFetchErrorRed();
    await usuario.click(dep.getByRole('button', { name: 'Depositar' }));
    expect(
      await dep.findByText(/No se pudo conectar con el servidor/),
    ).toBeInTheDocument();

    // Reintento con mock restaurado → éxito (AF-003).
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: () => CUENTAS },
      {
        url: '/api/v1/depositos',
        method: 'POST',
        status: 201,
        cuerpo: {
          idMovimiento: 90,
          cuentaId: 1,
          monto: 100,
          fechaHora: '2026-08-16T18:00:00',
        },
      },
    ]);
    await usuario.click(dep.getByRole('button', { name: 'Depositar' }));
    expect(await dep.findByText('Depósito realizado')).toBeInTheDocument();
  });

  it('AC-018 — doble envío: botón deshabilitado y una sola request', async () => {
    const { resolver, fetchMock } = mockFetchCajaDiferido();
    const usuario = userEvent.setup();

    renderizarCaja();

    await seleccionarCliente(usuario);

    const dep = seccionDe('Depósito');
    await usuario.selectOptions(dep.getByLabelText('Cuenta destino'), '1');
    await usuario.type(dep.getByLabelText('Monto depósito'), '100');
    await usuario.click(dep.getByRole('button', { name: 'Depositar' }));

    expect(dep.getByRole('button', { name: 'Procesando...' })).toBeDisabled();
    // Intento de doble envío bloqueado (AC-018).
    await usuario.click(dep.getByRole('button', { name: 'Procesando...' })).catch(() => undefined);
    const llamadasPost = fetchMock.mock.calls.filter(
      ([url, init]) => String(url) === '/api/v1/depositos' && init?.method === 'POST',
    );
    expect(llamadasPost).toHaveLength(1);

    resolver({
      idMovimiento: 70,
      cuentaId: 1,
      monto: 100,
      fechaHora: '2026-08-16T17:00:00',
    });
    expect(await dep.findByText('Depósito realizado')).toBeInTheDocument();
  });

  it('AC-003/AF-001 — cliente sin cuentas ACTIVA: estado vacío y formularios deshabilitados', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: [CUENTAS[1]] }, // solo BLOQUEADA
    ]);
    const usuario = userEvent.setup();

    renderizarCaja();
    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    await screen.findByText('El cliente no tiene cuentas activas.');
    for (const seccion of ['Depósito', 'Retiro']) {
      const form = seccionDe(seccion);
      expect(form.getByText('No hay cuentas disponibles')).toBeInTheDocument();
      expect(form.getByRole('button', { name: seccion === 'Depósito' ? 'Depositar' : 'Retirar' })).toBeDisabled();
    }
    await usuario.click(seccionDe('Depósito').getByRole('button', { name: 'Depositar' }));
    await usuario.click(seccionDe('Retiro').getByRole('button', { name: 'Retirar' }));
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/depositos',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/retiros',
      expect.objectContaining({ method: 'POST' }),
    );
  });
});

/** Mock de `fetch` que difiere solo el POST de la caja (clientes/cuentas resueltos al vuelo). */
function mockFetchCajaDiferido() {
  let resolverPost!: (r: unknown) => void;
  const promesaPost = new Promise((res) => {
    resolverPost = res;
  });
  const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const metodo = init?.method ?? 'GET';
    if (String(url).includes('/api/v1/clientes') && metodo === 'GET') {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(CLIENTES) });
    }
    if (String(url).startsWith('/api/v1/cuentas?') && metodo === 'GET') {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(CUENTAS) });
    }
    if (String(url).endsWith('/depositos') && metodo === 'POST') {
      return promesaPost;
    }
    return Promise.reject(new TypeError(`No hay mock para ${metodo} ${url}`));
  });
  vi.stubGlobal('fetch', fetchMock);
  return {
    fetchMock,
    resolver: (cuerpo: unknown) => {
      resolverPost({ ok: true, status: 201, json: () => Promise.resolve(cuerpo) });
    },
  };
}
