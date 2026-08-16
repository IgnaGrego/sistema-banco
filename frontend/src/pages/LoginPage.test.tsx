import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage from './LoginPage';
import { AuthProvider } from '../store/auth-context';
import { crearToken, mockFetchOk, mockFetchRespuestas } from '../test/helpers';
import { CLAVE_TOKEN } from '../lib/session';

function renderizarLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/cuentas" element={<div>Vista cuentas</div>} />
          <Route path="/gestion" element={<div>Vista gestion</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage (FR-005, BR-001, ERR-001, AC-008, AC-009, AC-030)', () => {
  it('AC-008 — login exitoso CLIENTE: envía POST /api/v1/auth/login y redirige a /cuentas', async () => {
    const fetchMock = mockFetchOk({ token: crearToken({ role: 'CLIENTE', clienteId: 5 }) });
    const usuario = userEvent.setup();

    renderizarLogin();

    await usuario.type(screen.getByLabelText('Usuario'), 'juan');
    await usuario.type(screen.getByLabelText('Contraseña'), 'secreta');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText('Vista cuentas')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      }),
    );
    const body = JSON.parse((fetchMock.mock.calls[0][1] as RequestInit).body as string);
    expect(body).toEqual({ username: 'juan', password: 'secreta' });
    expect(localStorage.getItem(CLAVE_TOKEN)).not.toBeNull();
  });

  it('AC-008 — login exitoso ADMIN: redirige a /gestion', async () => {
    mockFetchOk({ token: crearToken({ role: 'ADMIN' }) });
    const usuario = userEvent.setup();

    renderizarLogin();

    await usuario.type(screen.getByLabelText('Usuario'), 'admin');
    await usuario.type(screen.getByLabelText('Contraseña'), 'secreta');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText('Vista gestion')).toBeInTheDocument();
  });

  it('AC-009 — 401: mensaje genérico, no almacena token y permanece en /login', async () => {
    mockFetchRespuestas([
      {
        url: '/api/v1/auth/login',
        status: 401,
        cuerpo: { code: 'NO_AUTENTICADO', message: 'Credenciales inválidas' },
      },
    ]);
    const usuario = userEvent.setup();

    renderizarLogin();

    await usuario.type(screen.getByLabelText('Usuario'), 'juan');
    await usuario.type(screen.getByLabelText('Contraseña'), 'incorrecta');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByText('Usuario o contraseña incorrectos')).toBeInTheDocument();
    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    expect(screen.getByLabelText('Usuario')).toBeInTheDocument();
  });

  it('AC-030 — pre-validación BR-001: campos vacíos bloquean el envío con errores por campo', async () => {
    const fetchMock = mockFetchOk({ token: crearToken({ role: 'ADMIN' }) });
    const usuario = userEvent.setup();

    renderizarLogin();

    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(screen.getByText('El usuario es obligatorio')).toBeInTheDocument();
    expect(screen.getByText('La contraseña es obligatoria')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('AC-030 — durante el envío se deshabilita el botón (sin doble envío)', async () => {
    const { resolver } = mockFetchDiferidoLogin();
    const usuario = userEvent.setup();

    renderizarLogin();

    await usuario.type(screen.getByLabelText('Usuario'), 'juan');
    await usuario.type(screen.getByLabelText('Contraseña'), 'secreta');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    await waitFor(() => expect(screen.getByRole('button', { name: /Ingresando/ })).toBeDisabled());

    resolver({ token: crearToken({ role: 'CLIENTE', clienteId: 1 }) });
    expect(await screen.findByText('Vista cuentas')).toBeInTheDocument();
  });
});

function mockFetchDiferidoLogin() {
  let resolver!: (respuesta: unknown) => void;
  const promesa = new Promise((res) => {
    resolver = res;
  });
  const fetchMock = vi.fn().mockReturnValue(promesa);
  vi.stubGlobal('fetch', fetchMock);
  return {
    resolver: (cuerpo: unknown) => {
      resolver({ ok: true, status: 200, json: () => Promise.resolve(cuerpo) });
    },
    fetchMock,
  };
}
