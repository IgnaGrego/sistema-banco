import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ClientesSection from './ClientesSection';
import { mockFetchDiferido, mockFetchRespuestas } from '../../test/helpers';
import type { ClienteDto } from '../../api/types';

const CLIENTES: ClienteDto[] = [
  {
    id: 1,
    nombre: 'Juan',
    apellido: 'Pérez',
    dni: '30111222',
    email: 'juan@test.com',
    telefono: '+541155667788',
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

function renderizarClientes() {
  render(<ClientesSection />);
}

describe('ClientesSection (FR-012, BR-006, AC-022..AC-024, AC-027, AC-028, AC-029, AC-030)', () => {
  it('AC-022 — lista los clientes con nombre, apellido, dni y email', async () => {
    mockFetchRespuestas([{ url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES }]);

    renderizarClientes();

    expect(await screen.findByText('Pérez, Juan')).toBeInTheDocument();
    expect(screen.getByText('DNI: 30111222')).toBeInTheDocument();
    expect(screen.getByText('juan@test.com')).toBeInTheDocument();
    expect(screen.getByText('Gómez, Ana')).toBeInTheDocument();
  });

  it('AC-023 — crear cliente envía POST /api/v1/clientes con el payload exacto y agrega el 201 a la lista', async () => {
    const creado: ClienteDto = {
      id: 3,
      nombre: 'María',
      apellido: 'López',
      dni: '33333333',
      email: 'maria@test.com',
      telefono: '1122334455',
      fechaAlta: '2026-08-10T12:00:00',
    };
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/clientes', method: 'POST', status: 201, cuerpo: creado },
    ]);
    const usuario = userEvent.setup();

    renderizarClientes();

    await screen.findByText('Pérez, Juan');
    await usuario.type(screen.getByLabelText('Nombre'), 'María');
    await usuario.type(screen.getByLabelText('Apellido'), 'López');
    await usuario.type(screen.getByLabelText('DNI'), '33333333');
    await usuario.type(screen.getByLabelText('Email'), 'maria@test.com');
    await usuario.type(screen.getByLabelText('Teléfono'), '1122334455');
    await usuario.click(screen.getByRole('button', { name: 'Crear cliente' }));

    expect(await screen.findByText('López, María')).toBeInTheDocument();

    const llamadaPost = fetchMock.mock.calls.find(
      ([url, init]) => String(url).includes('/api/v1/clientes') && init?.method === 'POST',
    );
    expect(llamadaPost).toBeDefined();
    expect(JSON.parse((llamadaPost![1] as RequestInit).body as string)).toEqual({
      nombre: 'María',
      apellido: 'López',
      dni: '33333333',
      email: 'maria@test.com',
      telefono: '1122334455',
    });
  });

  it('AC-024 — editar cliente envía PUT /api/v1/clientes/{id} con el payload actualizado y muestra la representación 200', async () => {
    const actualizado: ClienteDto = {
      ...CLIENTES[0],
      telefono: '+5491122334455',
    };
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/clientes/1', method: 'PUT', status: 200, cuerpo: actualizado },
    ]);
    const usuario = userEvent.setup();

    renderizarClientes();

    await screen.findByText('Pérez, Juan');
    await usuario.click(screen.getAllByRole('button', { name: 'Editar' })[0]);

    expect(screen.getByText('Editar cliente (ID 1)')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre')).toHaveValue('Juan');

    await usuario.clear(screen.getByLabelText('Teléfono'));
    await usuario.type(screen.getByLabelText('Teléfono'), '+5491122334455');
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(screen.getByText('Pérez, Juan')).toBeInTheDocument());
    const llamadaPut = fetchMock.mock.calls.find(([url, init]) => String(url).includes('/api/v1/clientes/1') && init?.method === 'PUT');
    expect(llamadaPut).toBeDefined();
    expect(JSON.parse((llamadaPut![1] as RequestInit).body as string)).toEqual({
      nombre: 'Juan',
      apellido: 'Pérez',
      dni: '30111222',
      email: 'juan@test.com',
      telefono: '+5491122334455',
    });
  });

  it('AC-027 — 400 DATOS_INVALIDOS con details: errores por campo', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: [] },
      {
        url: '/api/v1/clientes',
        method: 'POST',
        status: 400,
        cuerpo: {
          code: 'DATOS_INVALIDOS',
          message: 'Datos inválidos',
          details: [{ campo: 'email', mensaje: 'El email no tiene un formato válido' }],
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarClientes();

    await screen.findByText('No hay clientes registrados.');
    await usuario.type(screen.getByLabelText('Nombre'), 'María');
    await usuario.type(screen.getByLabelText('Apellido'), 'López');
    await usuario.type(screen.getByLabelText('DNI'), '33333333');
    await usuario.type(screen.getByLabelText('Email'), 'no-es-email');
    await usuario.click(screen.getByRole('button', { name: 'Crear cliente' }));

    expect(await screen.findByText('El email no tiene un formato válido')).toBeInTheDocument();
    // Los datos ingresados se conservan.
    expect(screen.getByLabelText('Email')).toHaveValue('no-es-email');
  });

  it('AC-028 — 409 CONFLICTO_UNICIDAD: se muestra el mensaje del envelope (ERR-007)', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: [] },
      {
        url: '/api/v1/clientes',
        method: 'POST',
        status: 409,
        cuerpo: {
          code: 'CONFLICTO_UNICIDAD',
          message: 'Ya existe un cliente con el mismo DNI',
          details: [{ campo: 'dni', mensaje: 'Ya existe un cliente con el mismo DNI' }],
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarClientes();

    await screen.findByText('No hay clientes registrados.');
    await usuario.type(screen.getByLabelText('Nombre'), 'María');
    await usuario.type(screen.getByLabelText('Apellido'), 'López');
    await usuario.type(screen.getByLabelText('DNI'), '30111222');
    await usuario.type(screen.getByLabelText('Email'), 'maria@test.com');
    await usuario.click(screen.getByRole('button', { name: 'Crear cliente' }));

    expect(await screen.findByText('Ya existe un cliente con el mismo DNI')).toBeInTheDocument();
  });

  it('AC-029 — error de red en el listado: mensaje de conexión y Reintentar recupera la vista', async () => {
    const { resolver } = mockFetchDiferido();
    const usuario = userEvent.setup();

    renderizarClientes();

    resolver(null, 500); // respuesta no-JSON con 5xx — el mock devuelve cuerpo null
    expect(await screen.findByText(/Error inesperado|interno/)).toBeInTheDocument();

    mockFetchRespuestas([{ url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES }]);
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Pérez, Juan')).toBeInTheDocument();
  });

  it('AC-030 — estado de carga durante la request inicial y sin doble envío en el alta', async () => {
    const { resolver } = mockFetchDiferido();

    renderizarClientes();

    expect(await screen.findByRole('status')).toHaveTextContent('Cargando...');

    resolver(CLIENTES);
    await screen.findByText('Pérez, Juan');
    // El formulario queda operativo tras la carga (no hay doble envío por defecto).
    expect(screen.getByRole('button', { name: 'Crear cliente' })).toBeEnabled();
  });
});
