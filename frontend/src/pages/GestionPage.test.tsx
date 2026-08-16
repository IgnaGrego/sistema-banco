import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import GestionPage from './GestionPage';
import { mockFetchRespuestas, renderizarConSesion } from '../test/helpers';
import type { ClienteDto } from '../api/types';

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

function renderizarGestion() {
  return renderizarConSesion(<GestionPage />, { role: 'ADMIN' }, '/gestion');
}

describe('GestionPage (AC-028, AC-021)', () => {
  it('AC-028 — 403 ACCESO_DENEGADO en el listado: se muestra el mensaje del envelope', async () => {
    mockFetchRespuestas([
      {
        url: '/api/v1/clientes',
        method: 'GET',
        status: 403,
        cuerpo: { code: 'ACCESO_DENEGADO', message: 'No tiene permisos para realizar esta operación' },
      },
    ]);

    renderizarGestion();

    const mensajes = await screen.findAllByText('No tiene permisos para realizar esta operación');
    expect(mensajes.length).toBeGreaterThanOrEqual(1);
  });

  it('AC-028 — 409 CONFLICTO_UNICIDAD al crear un cliente: se muestra el mensaje del envelope', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      {
        url: '/api/v1/clientes',
        method: 'POST',
        status: 409,
        cuerpo: {
          code: 'CONFLICTO_UNICIDAD',
          message: 'Conflicto de unicidad de datos',
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarGestion();

    const nombres = await screen.findAllByText('Pérez, Juan');
    expect(nombres.length).toBeGreaterThanOrEqual(1);
    await usuario.type(screen.getAllByLabelText('Nombre')[0], 'María');
    await usuario.type(screen.getAllByLabelText('Apellido')[0], 'López');
    await usuario.type(screen.getAllByLabelText('DNI')[0], '33333333');
    await usuario.type(screen.getAllByLabelText('Email')[0], 'maria@test.com');
    await usuario.click(screen.getByRole('button', { name: 'Crear cliente' }));

    expect(await screen.findByText('Conflicto de unicidad de datos')).toBeInTheDocument();
  });

  it('AC-028 — 422 en la apertura de cuenta: se muestra el mensaje del envelope en la vista', async () => {
    mockFetchRespuestas([
      { url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES },
      { url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: [] },
      {
        url: '/api/v1/cuentas',
        method: 'POST',
        status: 422,
        cuerpo: { code: 'MONEDA_NO_SOPORTADA', message: 'La moneda solicitada no es soportada' },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarGestion();

    const nombres = await screen.findAllByText('Pérez, Juan');
    expect(nombres.length).toBeGreaterThanOrEqual(1);
    // El label "Cliente" ahora existe en CuentasSection y CajaSection: se acota
    // al selector de la sección Cuentas (heading único "Cuentas", §8.7).
    const seccionCuentas = within(
      screen.getByRole('heading', { name: 'Cuentas' }).closest('.seccion') as HTMLElement,
    );
    await usuario.selectOptions(seccionCuentas.getByLabelText('Cliente'), '1');
    await usuario.click(screen.getByRole('button', { name: 'Abrir cuenta' }));

    expect(await screen.findByText('La moneda solicitada no es soportada')).toBeInTheDocument();
  });

  it('AC-021 — la vista de gestión no expone acciones de CLIENTE (no hay formulario de transferencia)', async () => {
    mockFetchRespuestas([{ url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: CLIENTES }]);

    renderizarGestion();

    const nombres = await screen.findAllByText('Pérez, Juan');
    expect(nombres.length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('Transferencia')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('CBU destino')).not.toBeInTheDocument();
  });
});
