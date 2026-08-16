import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from './auth-context';
import { crearToken, mockFetchOk } from '../test/helpers';
import { CLAVE_TOKEN, guardarToken } from '../lib/session';

function ConsumidorDeSesion() {
  const { rol, clienteId, cargando, login, logout, sesionExpirada } = useAuth();
  return (
    <div>
      <span data-testid="rol">rol: {rol ?? 'ninguno'}</span>
      <span data-testid="cliente-id">clienteId: {clienteId ?? 'ninguno'}</span>
      <span data-testid="cargando">cargando: {String(cargando)}</span>
      <button type="button" onClick={() => void login('usuario', 'password')}>
        login
      </button>
      <button type="button" onClick={logout}>
        logout
      </button>
      <button type="button" onClick={sesionExpirada}>
        expirada
      </button>
    </div>
  );
}

describe('AuthProvider / useAuth (A-006, FR-003, FR-005, FR-014, AC-006)', () => {
  it('login guarda el token recibido en localStorage (banco.token) y setea el estado', async () => {
    mockFetchOk({ token: crearToken({ role: 'CLIENTE', clienteId: 7 }) });
    const usuario = userEvent.setup();

    render(
      <AuthProvider>
        <ConsumidorDeSesion />
      </AuthProvider>,
    );

    await usuario.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() => expect(localStorage.getItem(CLAVE_TOKEN)).not.toBeNull());
    expect(await screen.findByText('rol: CLIENTE')).toBeInTheDocument();
    expect(screen.getByTestId('cliente-id')).toHaveTextContent('clienteId: 7');
  });

  it('logout limpia el token de localStorage y el estado (FR-014)', async () => {
    guardarToken(crearToken({ role: 'ADMIN' }));
    const usuario = userEvent.setup();

    render(
      <AuthProvider>
        <ConsumidorDeSesion />
      </AuthProvider>,
    );

    await screen.findByText('rol: ADMIN');
    await usuario.click(screen.getByRole('button', { name: 'logout' }));

    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    expect(screen.getByTestId('rol')).toHaveTextContent('rol: ninguno');
  });

  it('restaura la sesión desde localStorage al montar (FR-003)', async () => {
    guardarToken(crearToken({ role: 'ADMIN' }));

    render(
      <AuthProvider>
        <ConsumidorDeSesion />
      </AuthProvider>,
    );

    expect(await screen.findByText('rol: ADMIN')).toBeInTheDocument();
    expect(screen.getByTestId('cargando')).toHaveTextContent('cargando: false');
  });

  it('un token guardado no decodificable se trata como sesión inválida y se limpia (ERR-002)', async () => {
    guardarToken('token-basura-no-decodificable');

    render(
      <AuthProvider>
        <ConsumidorDeSesion />
      </AuthProvider>,
    );

    expect(await screen.findByText('rol: ninguno')).toBeInTheDocument();
    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
  });

  it('sesionExpirada limpia el token y el estado (FR-007)', async () => {
    guardarToken(crearToken({ role: 'CLIENTE', clienteId: 3 }));
    const usuario = userEvent.setup();

    render(
      <AuthProvider>
        <ConsumidorDeSesion />
      </AuthProvider>,
    );

    await screen.findByText('rol: CLIENTE');
    await usuario.click(screen.getByRole('button', { name: 'expirada' }));

    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
    expect(screen.getByTestId('rol')).toHaveTextContent('rol: ninguno');
  });
});
