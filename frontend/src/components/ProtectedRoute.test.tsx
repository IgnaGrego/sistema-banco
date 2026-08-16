import { Route, Routes } from 'react-router-dom';
import { screen } from '@testing-library/react';
import ProtectedRoute from './ProtectedRoute';
import { renderizarConSesion, renderizarSinSesion } from '../test/helpers';

function renderizarGuard(ruta: string, rolPermitido: 'CLIENTE' | 'ADMIN') {
  const contenido = rolPermitido === 'CLIENTE' ? 'Vista cuentas' : 'Vista gestion';
  return (
    <Routes>
      <Route
        path={ruta}
        element={
          <ProtectedRoute rolPermitido={rolPermitido}>
            <div>{contenido}</div>
          </ProtectedRoute>
        }
      />
      <Route path="/login" element={<div>Login</div>} />
      <Route path="/cuentas" element={<div>Vista cuentas</div>} />
      <Route path="/gestion" element={<div>Vista gestion</div>} />
    </Routes>
  );
}

describe('ProtectedRoute (FR-006, AC-013)', () => {
  it('AC-013 — sin sesión: redirige a /login', async () => {
    renderizarSinSesion(renderizarGuard('/cuentas', 'CLIENTE'), '/cuentas');
    expect(await screen.findByText('Login')).toBeInTheDocument();
  });

  it('AC-013 — un CLIENTE no accede a /gestion: vuelve a /cuentas', async () => {
    renderizarConSesion(renderizarGuard('/gestion', 'ADMIN'), { role: 'CLIENTE', clienteId: 1 }, '/gestion');
    expect(await screen.findByText('Vista cuentas')).toBeInTheDocument();
  });

  it('AC-013 — un ADMIN no accede a /cuentas: vuelve a /gestion', async () => {
    renderizarConSesion(renderizarGuard('/cuentas', 'CLIENTE'), { role: 'ADMIN' }, '/cuentas');
    expect(await screen.findByText('Vista gestion')).toBeInTheDocument();
  });

  it('AC-013 — un CLIENTE accede a su ruta /cuentas', async () => {
    renderizarConSesion(renderizarGuard('/cuentas', 'CLIENTE'), { role: 'CLIENTE', clienteId: 1 }, '/cuentas');
    expect(await screen.findByText('Vista cuentas')).toBeInTheDocument();
  });

  it('AC-013 — un ADMIN accede a su ruta /gestion', async () => {
    renderizarConSesion(renderizarGuard('/gestion', 'ADMIN'), { role: 'ADMIN' }, '/gestion');
    expect(await screen.findByText('Vista gestion')).toBeInTheDocument();
  });
});
