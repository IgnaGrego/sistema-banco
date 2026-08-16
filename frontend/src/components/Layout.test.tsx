import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Layout from './Layout';
import { CLAVE_TOKEN } from '../lib/session';
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
