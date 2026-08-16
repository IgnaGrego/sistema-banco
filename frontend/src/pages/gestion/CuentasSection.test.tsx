import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CuentasSection from './CuentasSection';
import { mockFetchRespuestas } from '../../test/helpers';
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
  {
    id: 2,
    nombre: 'Ana',
    apellido: 'Gómez',
    dni: '28123456',
    email: 'ana@test.com',
    fechaAlta: '2026-02-01T09:00:00',
  },
];

const CUENTAS: CuentaDto[] = [
  {
    id: 1,
    clienteId: 1,
    cbu: '0000003100000000000001',
    tipo: 'CAJA_AHORRO',
    saldo: 0,
    moneda: 'ARS',
    estado: 'ACTIVA',
    createdAt: '2026-08-10T12:00:00',
  },
];

function renderizarCuentas() {
  render(<CuentasSection />);
}

/** Espera a que el selector de clientes tenga opciones cargadas (GET /api/v1/clientes). */
async function esperarOpcionesDeClientes() {
  await screen.findByRole('option', { name: /Pérez, Juan/ });
}

describe('CuentasSection (FR-013, BR-007, AC-025..AC-028, AC-029, AC-030)', () => {
  it('AC-026 — listar cuentas por cliente usa GET /api/v1/cuentas?clienteId={id} y renderiza las CuentaDto', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');

    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
    const llamadaCuentas = fetchMock.mock.calls.find(([url]) =>
      String(url).startsWith('/api/v1/cuentas?'),
    );
    expect(String(llamadaCuentas![0])).toBe('/api/v1/cuentas?clienteId=1');
  });

  it('AC-025/AF-006 — abrir cuenta envía POST /api/v1/cuentas con {clienteId, tipo, moneda} default ARS y refresca la lista', async () => {
    const nueva: CuentaDto = {
      id: 2,
      clienteId: 1,
      cbu: '0000003100000000000002',
      tipo: 'CUENTA_CORRIENTE',
      saldo: 0,
      moneda: 'ARS',
      estado: 'ACTIVA',
      createdAt: '2026-08-11T12:00:00',
    };
    // Mock con estado: el POST "persiste" la cuenta y el GET posterior (refresh)
    // devuelve la lista actualizada (AF-006).
    let cuentasBackend: CuentaDto[] = [...CUENTAS];
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: () => cuentasBackend },
      {
        url: '/api/v1/cuentas',
        method: 'POST',
        status: 201,
        cuerpo: () => {
          cuentasBackend = [...cuentasBackend, nueva];
          return nueva;
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    await screen.findByText('CBU: 0000003100000000000001');

    await usuario.selectOptions(screen.getByLabelText('Tipo de cuenta'), 'CUENTA_CORRIENTE');
    await usuario.click(screen.getByRole('button', { name: 'Abrir cuenta' }));

    // Refresh de la lista: la nueva cuenta aparece con saldo 0 y estado ACTIVA (AF-006).
    expect(await screen.findByText('CBU: 0000003100000000000002')).toBeInTheDocument();

    const llamadaPost = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/v1/cuentas' && init?.method === 'POST',
    );
    expect(llamadaPost).toBeDefined();
    expect(JSON.parse((llamadaPost![1] as RequestInit).body as string)).toEqual({
      clienteId: 1,
      tipo: 'CUENTA_CORRIENTE',
      moneda: 'ARS',
    });
  });

  it('AC-027 — 400 DATOS_INVALIDOS con details: errores por campo en la apertura', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/cuentas',
        method: 'POST',
        status: 400,
        cuerpo: {
          code: 'DATOS_INVALIDOS',
          message: 'Datos inválidos',
          details: [{ campo: 'moneda', mensaje: 'Formato de moneda inválido' }],
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    await usuario.click(screen.getByRole('button', { name: 'Abrir cuenta' }));

    expect(await screen.findByText('Formato de moneda inválido')).toBeInTheDocument();
  });

  it('BR-007 — pre-validación: sin cliente seleccionado el envío se bloquea con error de campo', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await esperarOpcionesDeClientes();
    await usuario.click(screen.getByRole('button', { name: 'Abrir cuenta' }));

    // El error de campo se muestra con role=alert (el placeholder del selector
    // tiene el mismo texto, por eso no se usa getByText exacto).
    expect(screen.getByRole('alert')).toHaveTextContent('Seleccione un cliente');
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/v1/cuentas',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('AC-028 — 404 CLIENTE_NO_ENCONTRADO al listar cuentas: mensaje del envelope', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/cuentas',
        method: 'GET',
        status: 404,
        cuerpo: { code: 'CLIENTE_NO_ENCONTRADO', message: 'Cliente no encontrado' },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');

    expect(await screen.findByText('Cliente no encontrado')).toBeInTheDocument();
  });

  it('AC-028 — 422 MONEDA_NO_SOPORTADA en la apertura: mensaje del envelope', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/cuentas',
        method: 'POST',
        status: 422,
        cuerpo: { code: 'MONEDA_NO_SOPORTADA', message: 'La moneda solicitada no es soportada' },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await esperarOpcionesDeClientes();
    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    await usuario.click(screen.getByRole('button', { name: 'Abrir cuenta' }));

    expect(await screen.findByText('La moneda solicitada no es soportada')).toBeInTheDocument();
  });

  it('AC-030 — estado de carga durante el listado de cuentas del cliente seleccionado', async () => {
    const { resolverClientes, resolverCuentas } = mockFetchDiferidoCuentas();
    const usuario = userEvent.setup();

    renderizarCuentas();

    // El selector de clientes queda cargado (request de clientes resuelta) y la
    // selección dispara la request de cuentas, que permanece en vuelo.
    resolverClientes();
    await esperarOpcionesDeClientes();

    await usuario.selectOptions(screen.getByLabelText('Cliente'), '1');
    expect(await screen.findByRole('status')).toHaveTextContent('Cargando...');

    resolverCuentas([{ ...CUENTAS[0] }]);
    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
  });
});

function mockFetchDiferidoCuentas() {
  let resolverClientes!: (r: unknown) => void;
  let resolverCuentas!: (r: unknown) => void;
  const promesaClientes = new Promise((res) => {
    resolverClientes = res;
  });
  const promesaCuentas = new Promise((res) => {
    resolverCuentas = res;
  });
  const fetchMock = vi.fn().mockImplementation((url: string) => {
    if (url.includes('/api/v1/clientes')) {
      return promesaClientes;
    }
    if (url.includes('/api/v1/cuentas')) {
      return promesaCuentas;
    }
    return Promise.reject(new TypeError(`No hay mock para ${url}`));
  });
  vi.stubGlobal('fetch', fetchMock);
  const ok = (cuerpo: unknown) => ({ ok: true, status: 200, json: () => Promise.resolve(cuerpo) });
  return {
    resolverClientes: () => {
      resolverClientes(ok(CLIENTES));
    },
    resolverCuentas: (cuerpo: unknown) => {
      resolverCuentas(ok(cuerpo));
    },
  };
}
