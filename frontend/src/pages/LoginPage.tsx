import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, MENSAJE_ERROR_RED } from '../api/httpClient';
import CampoFormulario from '../components/CampoFormulario';
import { validarLogin } from '../lib/validacion';
import { useAuth } from '../store/auth-context';

/**
 * Pantalla de login (US-6.1, FR-005): formulario username/password con
 * pre-validación BR-001, estado de envío (bloquea doble submit — AC-030),
 * error genérico ante 401 (ERR-001, AF-001, sin almacenar token) y
 * redirección por rol tras éxito (CLIENTE → /cuentas; ADMIN → /gestion).
 */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const erroresValidacion = validarLogin(username, password);
    setErrores(erroresValidacion);
    if (Object.keys(erroresValidacion).length > 0) {
      return;
    }
    setEnviando(true);
    setErrorGeneral(null);
    try {
      const rol = await login(username.trim(), password);
      navigate(rol === 'CLIENTE' ? '/cuentas' : '/gestion', { replace: true });
    } catch (error) {
      // 401 → mensaje genérico sin revelar si falló el usuario o la password
      // (ERR-001, A-004); error de red → mensaje de conexión (ERR-008).
      if (error instanceof ApiError && error.code === 'ERROR_RED') {
        setErrorGeneral(MENSAJE_ERROR_RED);
      } else {
        setErrorGeneral('Usuario o contraseña incorrectos');
      }
    } finally {
      setEnviando(false);
    }
  };

  return (
    <section className="login">
      <p className="login-marca">Banco</p>
      <h1>Iniciar sesión</h1>
      {errorGeneral !== null && (
        <div role="alert" className="error-general">
          {errorGeneral}
        </div>
      )}
      <form onSubmit={onSubmit} noValidate>
        <CampoFormulario id="username" label="Usuario" error={errores.username}>
          <input
            id="username"
            name="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
          />
        </CampoFormulario>
        <CampoFormulario id="password" label="Contraseña" error={errores.password}>
          <input
            id="password"
            name="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </CampoFormulario>
        <button type="submit" disabled={enviando}>
          {enviando ? 'Ingresando...' : 'Ingresar'}
        </button>
      </form>
    </section>
  );
}
