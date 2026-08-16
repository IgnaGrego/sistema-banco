import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../store/auth-context';
import type { Rol } from '../api/types';
import Cargando from './Cargando';

/**
 * Guard de rutas por autenticación y rol (FR-006, matriz del diseño §5.5):
 * - Sin sesión → /login.
 * - Rol con ruta ajena → home del rol (CLIENTE → /cuentas; ADMIN → /gestion).
 * - Durante la restauración inicial de la sesión muestra el estado de carga.
 * El backend permanece como punto de enforcement (BR-008).
 */
interface ProtectedRouteProps {
  rolPermitido: Rol;
  children: ReactNode;
}

export default function ProtectedRoute({ rolPermitido, children }: ProtectedRouteProps) {
  const { rol, cargando } = useAuth();

  if (cargando) {
    return <Cargando />;
  }
  if (rol === null) {
    return <Navigate to="/login" replace />;
  }
  if (rol !== rolPermitido) {
    return <Navigate to={rol === 'CLIENTE' ? '/cuentas' : '/gestion'} replace />;
  }
  return <>{children}</>;
}
