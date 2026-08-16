import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/auth-context';

/**
 * Shell autenticado (FR-014/FR-015): header con navegación por rol
 * (CLIENTE → "Mis cuentas"; ADMIN → "Gestión") y botón de logout que limpia
 * la sesión y redirige a /login (AC-012, AC-021).
 */
export default function Layout({ children }: { children: ReactNode }) {
  const { rol, logout } = useAuth();
  const navigate = useNavigate();

  const cerrarSesion = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="layout">
      <header className="layout-header">
        <nav className="layout-nav">
          {rol === 'CLIENTE' && <Link to="/cuentas">Mis cuentas</Link>}
          {rol === 'ADMIN' && <Link to="/gestion">Gestión</Link>}
        </nav>
        <button type="button" className="boton-logout" onClick={cerrarSesion}>
          Cerrar sesión
        </button>
      </header>
      <main className="layout-main">{children}</main>
    </div>
  );
}
