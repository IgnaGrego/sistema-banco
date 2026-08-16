import { useState } from 'react';
import type { FormEvent } from 'react';
import { abrirCuenta } from '../../api/cuentas';
import type { ApiError } from '../../api/httpClient';
import type { CuentaDto, TipoCuenta } from '../../api/types';
import CampoFormulario from '../../components/CampoFormulario';
import Cargando from '../../components/Cargando';
import EstadoError from '../../components/EstadoError';
import EstadoVacio from '../../components/EstadoVacio';
import Monto from '../../components/Monto';
import { useClientes } from '../../hooks/useClientes';
import { useCuentas } from '../../hooks/useCuentas';
import { validarAperturaCuenta } from '../../lib/validacion';
import type { ErroresPorCampo } from '../../lib/validacion';

/**
 * Sección de gestión de cuentas del ADMIN (FR-013): selector de cliente →
 * listado `GET /api/v1/cuentas?clienteId={id}` (AC-026) y apertura
 * `POST /api/v1/cuentas {clienteId, tipo, moneda?}` con moneda default ARS →
 * 201 con refresh de la lista (AC-025, AF-006). Validación BR-007; 400 por
 * campo (AC-027); 404/422 → mensaje del envelope (ERR-005/006, AC-028).
 * Doble envío protegido (AC-030).
 */
export default function CuentasSection() {
  const {
    datos: clientes,
    cargando: cargandoClientes,
    error: errorClientes,
    recargar: recargarClientes,
  } = useClientes();
  const [clienteSeleccionado, setClienteSeleccionado] = useState<number | null>(null);
  const [claveLista, setClaveLista] = useState(0);
  const [tipo, setTipo] = useState<TipoCuenta>('CAJA_AHORRO');
  const [moneda, setMoneda] = useState('ARS');
  const [erroresCampo, setErroresCampo] = useState<ErroresPorCampo>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const alAbrirCuenta = async (event: FormEvent) => {
    event.preventDefault();
    const erroresValidacion = validarAperturaCuenta(clienteSeleccionado, tipo, moneda);
    setErroresCampo(erroresValidacion);
    if (Object.keys(erroresValidacion).length > 0 || clienteSeleccionado === null) {
      return;
    }
    setEnviando(true);
    setErrorGeneral(null);
    try {
      // moneda: string (default ARS); JSON.stringify descarta `undefined`.
      await abrirCuenta({ clienteId: clienteSeleccionado, tipo, moneda });
      setClaveLista((clave) => clave + 1); // refresca la lista del cliente (AF-006)
    } catch (e) {
      const err = e as ApiError;
      if (err.details !== undefined && err.details.length > 0) {
        const porCampo: ErroresPorCampo = {};
        for (const detalle of err.details) {
          porCampo[detalle.campo] = detalle.mensaje;
        }
        setErroresCampo(porCampo);
        setErrorGeneral(null);
      } else {
        setErroresCampo({});
        setErrorGeneral(err.message);
      }
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="seccion">
      <h2>Cuentas</h2>

      {cargandoClientes && clientes === null && <Cargando />}
      {errorClientes !== null && (
        <EstadoError mensaje={errorClientes.message} onReintentar={recargarClientes} />
      )}

      <CampoFormulario id="cliente-selector" label="Cliente" error={erroresCampo.clienteId}>
        <select
          id="cliente-selector"
          value={clienteSeleccionado ?? ''}
          onChange={(e) => {
            setClienteSeleccionado(e.target.value !== '' ? Number(e.target.value) : null);
            setErroresCampo({});
            setErrorGeneral(null);
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

      {clienteSeleccionado === null ? (
        <EstadoVacio mensaje="Seleccione un cliente para ver sus cuentas." />
      ) : (
        <CuentasDeCliente key={`${clienteSeleccionado}-${claveLista}`} clienteId={clienteSeleccionado} />
      )}

      <h3>Abrir cuenta</h3>
      {errorGeneral !== null && (
        <div role="alert" className="error-general">
          {errorGeneral}
        </div>
      )}
      <form onSubmit={alAbrirCuenta} noValidate>
        <CampoFormulario id="tipo-cuenta" label="Tipo de cuenta" error={erroresCampo.tipo}>
          <select id="tipo-cuenta" value={tipo} onChange={(e) => setTipo(e.target.value as TipoCuenta)}>
            <option value="CAJA_AHORRO">Caja de ahorro</option>
            <option value="CUENTA_CORRIENTE">Cuenta corriente</option>
          </select>
        </CampoFormulario>
        <CampoFormulario id="moneda-cuenta" label="Moneda" error={erroresCampo.moneda}>
          <select id="moneda-cuenta" value={moneda} onChange={(e) => setMoneda(e.target.value)}>
            <option value="ARS">ARS</option>
          </select>
        </CampoFormulario>
        <button type="submit" disabled={enviando}>
          {enviando ? 'Abriendo...' : 'Abrir cuenta'}
        </button>
      </form>
    </div>
  );
}

/** Lista de cuentas del cliente seleccionado (FR-013, AC-026). */
function CuentasDeCliente({ clienteId }: { clienteId: number }) {
  const { datos, cargando, error, recargar } = useCuentas(clienteId);

  if (cargando && datos === null) {
    return <Cargando />;
  }
  if (error !== null) {
    return <EstadoError mensaje={error.message} onReintentar={recargar} />;
  }
  if (datos === null || datos.length === 0) {
    return <EstadoVacio mensaje="El cliente no tiene cuentas." />;
  }
  return (
    <ul className="lista-cuentas">
      {datos.map((cuenta) => (
        <CuentaItem key={cuenta.id} cuenta={cuenta} />
      ))}
    </ul>
  );
}

function CuentaItem({ cuenta }: { cuenta: CuentaDto }) {
  return (
    <li className="cuenta">
      <span className="cuenta-cbu">CBU: {cuenta.cbu}</span>
      <span className="cuenta-tipo">{cuenta.tipo}</span>
      <Monto monto={cuenta.saldo} />
      <span className="cuenta-moneda">{cuenta.moneda}</span>
      <span className={`cuenta-estado cuenta-estado-${cuenta.estado.toLowerCase()}`}>
        {cuenta.estado}
      </span>
    </li>
  );
}
