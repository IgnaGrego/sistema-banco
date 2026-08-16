import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { Rol, UsuarioDto } from '../../api/types';
import CampoFormulario from '../../components/CampoFormulario';
import Cargando from '../../components/Cargando';
import EstadoError from '../../components/EstadoError';
import EstadoVacio from '../../components/EstadoVacio';
import { useClientes } from '../../hooks/useClientes';
import { useRegistroUsuario } from '../../hooks/useRegistroUsuario';
import { validarRegistroUsuario } from '../../lib/validacion';
import type { ErroresPorCampo } from '../../lib/validacion';

/** Campos del envelope conocidos del registro (whitelist — patrón CajaSection/TransferenciaPage, §8.6). */
const CAMPOS_REGISTRO = new Set(['username', 'password', 'rol', 'clienteId']);

/**
 * Sección "Usuarios" del ADMIN (SPEC-008 FR-001..FR-004): formulario de
 * registro de usuarios (`POST /api/v1/auth/register`, SPEC-003) con username,
 * password, rol (CLIENTE/ADMIN) y, cuando `rol = CLIENTE`, un selector del
 * `Cliente` a vincular alimentado por `GET /api/v1/clientes` vía `useClientes`
 * (FR-002, A-003). Pre-valida BR-001..BR-004 (UX mirrors del backend — FR-003,
 * A-004); ante 201 muestra la confirmación con el `UsuarioDto` (`username` +
 * `rol`, nunca la password — A-005) y resetea el formulario (FR-004). Errores
 * del envelope: details → por campo, resto → general; los datos se conservan
 * (ERR-001..ERR-005).
 */
export default function UsuariosSection() {
  const {
    datos: clientes,
    cargando: cargandoClientes,
    error: errorClientes,
    recargar: recargarClientes,
  } = useClientes();
  const { enviando, confirmacion, error, ejecutar, limpiarConfirmacion } = useRegistroUsuario();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rol, setRol] = useState<Rol | ''>('ADMIN');
  const [clienteId, setClienteId] = useState<number | null>(null);
  const [erroresCampo, setErroresCampo] = useState<ErroresPorCampo>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);

  const mostrarSelectorCliente = rol === 'CLIENTE';

  // Mapeo del error de la API al formulario: details → errores por campo
  // (whitelist CAMPOS_REGISTRO), resto del envelope → mensaje general
  // (FR-004, ERR-001..ERR-005; los datos ingresados se conservan).
  useEffect(() => {
    if (error === null) {
      return;
    }
    if (error.details !== undefined && error.details.length > 0) {
      const porCampo: ErroresPorCampo = {};
      let mensajeGeneral = error.message;
      for (const detalle of error.details) {
        if (CAMPOS_REGISTRO.has(detalle.campo)) {
          porCampo[detalle.campo] = detalle.mensaje;
        } else {
          mensajeGeneral = detalle.mensaje;
        }
      }
      setErroresCampo(porCampo);
      setErrorGeneral(mensajeGeneral);
    } else {
      setErroresCampo({});
      setErrorGeneral(error.message);
    }
  }, [error]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const erroresValidacion = validarRegistroUsuario(username, password, rol, clienteId);
    setErroresCampo(erroresValidacion);
    if (Object.keys(erroresValidacion).length > 0) {
      return; // BR-001..BR-004: sin request
    }
    setErrorGeneral(null);
    const resultado = await ejecutar({
      username: username.trim(),
      password,
      rol: rol as Rol, // garantizado por la pre-validación (BR-003)
      clienteId: rol === 'CLIENTE' ? clienteId : null, // A-003: null para ADMIN
    });
    if (resultado !== null) {
      // 201: la confirmación se muestra (useRegistroUsuario.confirmacion) y se
      // resetea username/password/clienteId para otro registro (FR-004, A-005);
      // se conserva el rol elegido como conveniencia de UX (diseño §4.2).
      setUsername('');
      setPassword('');
      setClienteId(null);
      setErroresCampo({});
    }
  };

  return (
    <div className="seccion">
      <h2>Usuarios</h2>

      {confirmacion !== null ? (
        <ConfirmacionUsuario usuario={confirmacion} onNuevo={limpiarConfirmacion} />
      ) : (
        <>
          {mostrarSelectorCliente && cargandoClientes && clientes === null && <Cargando />}
          {mostrarSelectorCliente && errorClientes !== null && (
            <EstadoError mensaje={errorClientes.message} onReintentar={recargarClientes} />
          )}
          {mostrarSelectorCliente &&
            !cargandoClientes &&
            errorClientes === null &&
            clientes !== null &&
            clientes.length === 0 && <EstadoVacio mensaje="No hay clientes registrados." />}

          {errorGeneral !== null && (
            <div role="alert" className="error-general">
              {errorGeneral}
            </div>
          )}
          <form onSubmit={onSubmit} noValidate>
            <CampoFormulario id="usuario-username" label="Usuario" error={erroresCampo.username}>
              <input
                id="usuario-username"
                name="username"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setErroresCampo({});
                }}
                maxLength={50}
              />
            </CampoFormulario>
            <CampoFormulario id="usuario-password" label="Contraseña" error={erroresCampo.password}>
              <input
                id="usuario-password"
                name="password"
                type="password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setErroresCampo({});
                }}
              />
            </CampoFormulario>
            <CampoFormulario id="usuario-rol" label="Rol" error={erroresCampo.rol}>
              <select
                id="usuario-rol"
                value={rol}
                onChange={(e) => {
                  setRol(e.target.value as Rol | '');
                  // A-003/BR-004: al volver a ADMIN el selector de cliente se
                  // oculta y la selección previa se limpia (clienteId null).
                  setClienteId(null);
                  setErroresCampo({});
                }}
              >
                <option value="CLIENTE">CLIENTE</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </CampoFormulario>
            {mostrarSelectorCliente && (
              <CampoFormulario id="usuario-cliente" label="Cliente" error={erroresCampo.clienteId}>
                <select
                  id="usuario-cliente"
                  value={clienteId ?? ''}
                  onChange={(e) => {
                    setClienteId(e.target.value !== '' ? Number(e.target.value) : null);
                    setErroresCampo({});
                  }}
                >
                  <option value="">Seleccione un cliente</option>
                  {(clientes ?? []).map((cliente) => (
                    <option key={cliente.id} value={cliente.id}>
                      {cliente.apellido}, {cliente.nombre} — DNI {cliente.dni}
                    </option>
                  ))}
                </select>
              </CampoFormulario>
            )}
            <button type="submit" disabled={enviando}>
              {enviando ? 'Registrando...' : 'Registrar usuario'}
            </button>
          </form>
        </>
      )}
    </div>
  );
}

/** Panel de confirmación tras un 201: usuario creado (`username` + `rol`, NUNCA la password — A-005). */
function ConfirmacionUsuario({
  usuario,
  onNuevo,
}: {
  usuario: UsuarioDto;
  onNuevo: () => void;
}) {
  return (
    <div role="status" className="confirmacion">
      <p>
        Usuario creado: {usuario.username} ({usuario.rol})
      </p>
      <button type="button" onClick={onNuevo}>
        Registrar otro usuario
      </button>
    </div>
  );
}