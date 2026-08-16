import { screen } from '@testing-library/react';
import { render } from '@testing-library/react';
import App from './App';
import { AuthProvider } from './store/auth-context';
import { CLAVE_TOKEN, guardarToken } from './lib/session';
import { crearToken, mockFetchRespuestas } from './test/helpers';
import type { CuentaDto } from './api/types';

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
];

function renderizarAppEn(ruta: string) {
  window.history.pushState({}, '', ruta);
  return render(
    <AuthProvider>
      <App />
    </AuthProvider>,
  );
}

describe('App (FR-006, FR-007, AC-011, AC-013, AC-021)', () => {
  afterEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('AC-013 — sin sesión en /cuentas: redirige a /login', async () => {
    renderizarAppEn('/cuentas');
    expect(await screen.findByText('Iniciar sesión')).toBeInTheDocument();
  });

  it('AC-013 — un CLIENTE en /gestion vuelve a /cuentas', async () => {
    guardarToken(crearToken({ role: 'CLIENTE', clienteId: 10 }));
    mockFetchRespuestas([{ url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS }]);

    renderizarAppEn('/gestion');

    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
  });

  it('AC-013 — un ADMIN en /cuentas vuelve a /gestion', async () => {
    guardarToken(crearToken({ role: 'ADMIN' }));
    mockFetchRespuestas([{ url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: [] }]);

    renderizarAppEn('/cuentas');

    expect(await screen.findByText('Clientes')).toBeInTheDocument();
  });

  it('AC-013 — con sesión CLIENTE y visitando /login redirige a /cuentas', async () => {
    guardarToken(crearToken({ role: 'CLIENTE', clienteId: 10 }));
    mockFetchRespuestas([{ url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS }]);

    renderizarAppEn('/login');

    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
  });

  it('AC-013 — con sesión ADMIN y visitando /login redirige a /gestion', async () => {
    guardarToken(crearToken({ role: 'ADMIN' }));
    mockFetchRespuestas([{ url: '/api/v1/clientes', method: 'GET', status: 200, cuerpo: [] }]);

    renderizarAppEn('/login');

    expect(await screen.findByText('Clientes')).toBeInTheDocument();
  });

  it('AC-011 — 401 en una request autenticada limpia la sesión y redirige a /login (AF-002)', async () => {
    guardarToken(crearToken({ role: 'CLIENTE', clienteId: 10 }));
    mockFetchRespuestas([
      {
        url: '/api/v1/cuentas',
        method: 'GET',
        status: 401,
        cuerpo: { code: 'NO_AUTENTICADO', message: 'Token ausente o inválido' },
      },
    ]);

    renderizarAppEn('/cuentas');

    expect(await screen.findByText('Iniciar sesión')).toBeInTheDocument();
    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
  });

  it('AC-021 — la UI del CLIENTE en /cuentas no expone acciones de ADMIN', async () => {
    guardarToken(crearToken({ role: 'CLIENTE', clienteId: 10 }));
    mockFetchRespuestas([{ url: '/api/v1/cuentas', method: 'GET', status: 200, cuerpo: CUENTAS }]);

    renderizarAppEn('/cuentas');

    expect(await screen.findByText('CBU: 0000003100000000000001')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Gestión' })).not.toBeInTheDocument();
    expect(screen.queryByText('Nuevo cliente')).not.toBeInTheDocument();
    expect(screen.queryByText('Abrir cuenta')).not.toBeInTheDocument();
  });
});
