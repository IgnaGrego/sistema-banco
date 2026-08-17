import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Layout from './Layout';
import { AuthProvider } from '../store/auth-context';
import { CLAVE_TOKEN, guardarToken } from '../lib/session';
import { renderizarConSesion } from '../test/helpers';

describe('Layout (FR-014, FR-015, AC-012, AC-021)', () => {
  it('AC-012 — logout limpia el token de localStorage y redirige a /login', async () => {
    const usuario = userEvent.setup();
    renderizarConSesion(
      <Routes>
        <Route
          path="/cuentas"
          element={
            <Layout>
              <div>Contenido autenticado</div>
            </Layout>
          }
        />
        <Route path="/login" element={<div>Login</div>} />
      </Routes>,
      { role: 'CLIENTE', clienteId: 10 },
      '/cuentas',
    );

    await screen.findByText('Contenido autenticado');
    expect(localStorage.getItem(CLAVE_TOKEN)).not.toBeNull();

    await usuario.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(await screen.findByText('Login')).toBeInTheDocument();
    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
  });

  it('AC-021 — la navegación del CLIENTE muestra "Mis cuentas" y no "Gestión"', async () => {
    renderizarConSesion(
      <Routes>
        <Route
          path="/cuentas"
          element={
            <Layout>
              <div>Contenido autenticado</div>
            </Layout>
          }
        />
      </Routes>,
      { role: 'CLIENTE', clienteId: 10 },
      '/cuentas',
    );

    await screen.findByText('Contenido autenticado');
    expect(screen.getByRole('link', { name: 'Mis cuentas' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Gestión' })).not.toBeInTheDocument();
  });

  it('AC-021 — la navegación del ADMIN muestra "Gestión" y no "Mis cuentas"', async () => {
    renderizarConSesion(
      <Routes>
        <Route
          path="/gestion"
          element={
            <Layout>
              <div>Contenido admin</div>
            </Layout>
          }
        />
      </Routes>,
      { role: 'ADMIN' },
      '/gestion',
    );

    await screen.findByText('Contenido admin');
    expect(screen.getByRole('link', { name: 'Gestión' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Mis cuentas' })).not.toBeInTheDocument();
  });
});

describe('Layout (SPEC-009 FR-003, AC-004 — marca, usuario actual y rol)', () => {
  it('AC-004 — el header CLIENTE muestra la marca "Banco", el username del claim sub y el rol', async () => {
    renderizarConSesion(
      <Routes>
        <Route
          path="/cuentas"
          element={
            <Layout>
              <div>Contenido autenticado</div>
            </Layout>
          }
        />
      </Routes>,
      { role: 'CLIENTE', clienteId: 10 },
      '/cuentas',
    );

    await screen.findByText('Contenido autenticado');
    expect(screen.getByText('Banco')).toBeInTheDocument();
    // `crearToken` emite sub: 'usuario-test' (helpers) → username del claim sub.
    expect(screen.getByText('usuario-test')).toBeInTheDocument();
    expect(screen.getByText('CLIENTE')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis cuentas' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
  });

  it('AC-004 — el header ADMIN muestra la marca "Banco", el username del claim sub y el rol', async () => {
    renderizarConSesion(
      <Routes>
        <Route
          path="/gestion"
          element={
            <Layout>
              <div>Contenido admin</div>
            </Layout>
          }
        />
      </Routes>,
      { role: 'ADMIN' },
      '/gestion',
    );

    await screen.findByText('Contenido admin');
    expect(screen.getByText('Banco')).toBeInTheDocument();
    expect(screen.getByText('usuario-test')).toBeInTheDocument();
    expect(screen.getByText('ADMIN')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Gestión' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
  });

  it('AC-004 — token sin claim sub: el header muestra solo el rol (fallback R2)', async () => {
    guardarToken(tokenSinSub('ADMIN'));
    render(
      <MemoryRouter initialEntries={['/gestion']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/gestion"
              element={
                <Layout>
                  <div>Contenido admin</div>
                </Layout>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );

    await screen.findByText('Contenido admin');
    expect(screen.getByText('Banco')).toBeInTheDocument();
    expect(screen.getByText('ADMIN')).toBeInTheDocument();
    expect(screen.queryByText('usuario-test')).not.toBeInTheDocument();
  });
});

/** Construye un token válido SIN el claim `sub` (fallback R2 — A-005). */
function tokenSinSub(role: 'CLIENTE' | 'ADMIN'): string {
  const payload = base64url(JSON.stringify({ role }));
  return `${base64url(JSON.stringify({ alg: 'HS256' }))}.${payload}.firma-falsa`;
}

function base64url(texto: string): string {
  const bytes = new TextEncoder().encode(texto);
  let binario = '';
  bytes.forEach((b) => {
    binario += String.fromCharCode(b);
  });
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
