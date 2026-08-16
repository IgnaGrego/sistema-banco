import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Cargando from './components/Cargando';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import { useAuth } from './store/auth-context';
import CuentasPage from './pages/CuentasPage';
import GestionPage from './pages/GestionPage';
import LoginPage from './pages/LoginPage';

/**
 * Router de la SPA (FR-006, A-007): /login público (sin sesión → formulario;
 * con sesión → home del rol), /cuentas (CLIENTE) y /gestion (ADMIN) envueltas
 * en ProtectedRoute, y `*` → home del rol (o /login sin sesión).
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginRuta />} />
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
        <Route
          path="/gestion"
          element={
            <ProtectedRoute rolPermitido="ADMIN">
              <Layout>
                <GestionPage />
              </Layout>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<RutaDefault />} />
      </Routes>
    </BrowserRouter>
  );
}

/** /login con sesión válida → redirige a la ruta del rol (AC-013). */
function LoginRuta() {
  const { rol, cargando } = useAuth();
  if (cargando) {
    return <Cargando />;
  }
  if (rol !== null) {
    return <Navigate to={rol === 'CLIENTE' ? '/cuentas' : '/gestion'} replace />;
  }
  return <LoginPage />;
}

/** Ruta no declarada → home del rol (o /login sin sesión). */
function RutaDefault() {
  const { rol, cargando } = useAuth();
  if (cargando) {
    return <Cargando />;
  }
  if (rol === null) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={rol === 'CLIENTE' ? '/cuentas' : '/gestion'} replace />;
}
