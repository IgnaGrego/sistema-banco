import { Route, Routes } from 'react-router-dom';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CuentasPage from './CuentasPage';
import Layout from '../components/Layout';
import ProtectedRoute from '../components/ProtectedRoute';
import { formatearFecha, formatearMontoARS } from '../lib/session';
import {
  mockFetchDiferido,
  mockFetchErrorRed,
  mockFetchRespuestas,
  renderizarConSesion,
} from '../test/helpers';
import type { CuentaDto, MovimientoDto } from '../api/types';

const CUENTAS: CuentaDto[] = [
  {
    id: 1,
    clienteId: 10,
    cbu: '0000003100000000000001',
    tipo: 'CAJA_AHORRO',
    saldo: 1500.5,
    moneda: 'ARS',
    estado: 'ACTIVA',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    clienteId: 10,
    cbu: '0000003100000000000002',
    tipo: 'CUENTA_CORRIENTE',
    saldo: 0,
    moneda: 'ARS',
    estado: 'BLOQUEADA',
    createdAt: '2026-01-02T00:00:00Z',
  },
];

const MOVIMIENTOS: MovimientoDto[] = [
  {
    id: 11,
    cuentaId: 1,
    tipo: 'DEPOSITO',
    monto: 1000,
    moneda: 'ARS',
    fecha: '2026-08-01T10:00:00',
    cuentaContraparteId: null,
  },
  {
    id: 12,
    cuentaId: 1,
    tipo: 'TRANSFERENCIA_SALIENTE',
    monto: 250.5,
    moneda: 'ARS',
    fecha: '2026-08-02T11:30:00',
    cuentaContraparteId: 9,
  },
];

function renderizarCuentas() {
  return renderizarConSesion(
    <Routes>
      <Route
        path="/cuentas"
        element={
          <ProtectedRoute rolPermitido="CLIENTE">
            <Layout>
              <CuentasPage />
            </Layout>
          </ProtectedRoute>
        }
      />
      <Route path="/login" element={<div>Login</div>} />
      <Route path="/gestion" element={<div>Gestion</div>} />
    </Routes>,
    { role: 'CLIENTE', clienteId: 10 },
    '/cuentas',
  );
}

describe('CuentasPage (FR-009/FR-010/FR-011, AC-014..AC-016, AC-018, AC-021, AC-029, AC-030)', () => {
  it('AC-014 — llama GET /api/v1/cuentas sin parámetros y renderiza saldo ARS, cbu, tipo, moneda y estado', async () => {
    const fetchMock = mockFetchRespuestas([{ url: '/api/v1/cuentas', status: 200, cuerpo: CUENTAS }]);

    renderizarCuentas();

    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
    expect(screen.getByText('Caja de ahorro')).toBeInTheDocument();
    expect(screen.getByText(formatearMontoARS(1500.5))).toBeInTheDocument();
    // Cada cuenta renderiza su moneda (2 cuentas → 2 "ARS").
    expect(screen.getAllByText('ARS')).toHaveLength(2);
    expect(screen.getByText('ACTIVA')).toBeInTheDocument();
    expect(screen.getByText('BLOQUEADA')).toBeInTheDocument();

    const llamadaCuentas = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/v1/cuentas'));
    expect(String(llamadaCuentas![0])).toBe('/api/v1/cuentas');
    expect(String(llamadaCuentas![0])).not.toContain('?');
  });

  it('AC-015 — lista vacía: estado vacío y formulario de transferencia sin cuentas origen', async () => {
    mockFetchRespuestas([{ url: '/api/v1/cuentas', status: 200, cuerpo: [] }]);

    renderizarCuentas();

    expect(await screen.findByText('No tiene cuentas en este momento.')).toBeInTheDocument();
    expect(screen.getByText('No hay cuentas disponibles')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Transferir' })).toBeDisabled();
  });

  it('AC-016 — expandir una cuenta dispara GET /api/v1/cuentas/{id}/movimientos y renderiza el historial', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/cuentas/1/movimientos', status: 200, cuerpo: MOVIMIENTOS },
      { url: '/api/v1/cuentas', status: 200, cuerpo: CUENTAS },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await screen.findByText('CBU: 0000003100000000000001');
    await usuario.click(screen.getAllByRole('button', { name: 'Ver movimientos' })[0]);

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/cuentas/1/movimientos', expect.anything()),
    );
    expect(await screen.findByText('DEPOSITO')).toBeInTheDocument();
    expect(screen.getByText('TRANSFERENCIA_SALIENTE')).toBeInTheDocument();
    expect(screen.getByText(formatearMontoARS(1000))).toBeInTheDocument();
    expect(screen.getByText(formatearFecha('2026-08-01T10:00:00'))).toBeInTheDocument();
  });

  it('AC-018/AF-004 — tras una transferencia exitosa se refrescan cuentas (y el historial expandido)', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/cuentas/1/movimientos', status: 200, cuerpo: MOVIMIENTOS },
      { url: '/api/v1/cuentas', status: 200, cuerpo: CUENTAS },
      {
        url: '/api/v1/transferencias',
        status: 201,
        cuerpo: {
          idTransferencia: 55,
          monto: 100,
          cbuDestino: '0000003100000000000099',
          fechaHora: '2026-08-10T15:30:00Z',
        },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarCuentas();

    await screen.findByText('CBU: 0000003100000000000001');
    await usuario.click(screen.getAllByRole('button', { name: 'Ver movimientos' })[0]);
    await screen.findByText('DEPOSITO');

    await usuario.selectOptions(screen.getByLabelText('Cuenta origen'), '1');
    await usuario.type(screen.getByLabelText('CBU destino'), '0000003100000000000099');
    await usuario.type(screen.getByLabelText('Monto'), '100');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(await screen.findByText('Transferencia realizada')).toBeInTheDocument();

    // Refresh: GET /api/v1/cuentas se vuelve a llamar y el historial se recarga.
    await waitFor(() => {
      const llamadasCuentas = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/v1/cuentas');
      expect(llamadasCuentas.length).toBeGreaterThanOrEqual(2);
    });
    await waitFor(() => {
      const llamadasMovimientos = fetchMock.mock.calls.filter(([url]) =>
        String(url).includes('/api/v1/cuentas/1/movimientos'),
      );
      expect(llamadasMovimientos.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('AC-021 — la UI del CLIENTE no expone acciones de ADMIN', async () => {
    mockFetchRespuestas([{ url: '/api/v1/cuentas', status: 200, cuerpo: CUENTAS }]);

    renderizarCuentas();

    await screen.findByText('CBU: 0000003100000000000001');
    expect(screen.queryByText('Nuevo cliente')).not.toBeInTheDocument();
    expect(screen.queryByText('Abrir cuenta')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Gestión' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis cuentas' })).toBeInTheDocument();
  });

  it('AC-029 — error de red: mensaje de conexión y Reintentar recupera la vista', async () => {
    const usuario = userEvent.setup();

    mockFetchErrorRed();
    renderizarCuentas();

    expect(
      await screen.findByText(/No se pudo conectar con el servidor/),
    ).toBeInTheDocument();

    mockFetchRespuestas([{ url: '/api/v1/cuentas', status: 200, cuerpo: CUENTAS }]);
    await usuario.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
  });

  it('AC-030 — estado de carga durante la request inicial', async () => {
    const { resolver } = mockFetchDiferido();

    renderizarCuentas();

    expect(await screen.findByRole('status')).toHaveTextContent('Cargando...');

    resolver(CUENTAS);
    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
  });
});
