import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/auth-context';

/**
 * Shell autenticado (FR-014/FR-015, SPEC-009 FR-003): header rediseñado con
 * marca "Banco" (texto estilizado, no heading ni link — A-009), navegación
 * por rol (CLIENTE → "Mis cuentas"; ADMIN → "Gestión"), chip con el usuario
 * actual (`username` del claim `sub` del JWT + rol — A-005) y botón de logout
 * que limpia la sesión y redirige a /login (AC-012, AC-021 — comportamiento
 * intacto). No agrega role="status"/role="alert" (BR-006).
 */
export default function Layout({ children }: { children: ReactNode }) {
  const { rol, username, logout } = useAuth();
  const navigate = useNavigate();

  const cerrarSesion = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="layout">
      <header className="layout-header">
        <span className="marca">Banco</span>
        <nav className="layout-nav" aria-label="Navegación principal">
          {rol === 'CLIENTE' && <Link to="/cuentas">Mis cuentas</Link>}
          {rol === 'ADMIN' && <Link to="/gestion">Gestión</Link>}
        </nav>
        <div className="usuario-actual">
          {username !== undefined && <span className="usuario-actual-nombre">{username}</span>}
          <span className="usuario-actual-rol">{rol}</span>
        </div>
        <button type="button" className="boton-logout" onClick={cerrarSesion}>
          Cerrar sesión
        </button>
      </header>
      <main className="layout-main">{children}</main>
    </div>
  );
}