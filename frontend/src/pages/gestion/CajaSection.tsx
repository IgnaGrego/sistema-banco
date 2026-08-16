import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { CuentaDto, DetalleError } from '../../api/types';
import CampoFormulario from '../../components/CampoFormulario';
import Cargando from '../../components/Cargando';
import EstadoError from '../../components/EstadoError';
import EstadoVacio from '../../components/EstadoVacio';
import Monto from '../../components/Monto';
import { useClientes } from '../../hooks/useClientes';
import { useCuentas } from '../../hooks/useCuentas';
import { useCaja } from '../../hooks/useCaja';
import type { ConfirmacionCaja, TipoOperacionCaja } from '../../hooks/useCaja';
import { formatearFecha, formatearMontoARS } from '../../lib/session';
import { validarDeposito, validarRetiro } from '../../lib/validacion';
import type { ErroresPorCampo } from '../../lib/validacion';

/**
 * Sección "Caja" del ADMIN (SPEC-007 FR-001..FR-006): selector de cliente
 * (useClientes) → listado `GET /api/v1/cuentas?clienteId={id}` (FR-002) y dos
 * formularios (depósito/retiro — A-001) que consumen
 * `POST /api/v1/depositos` y `POST /api/v1/retiros` (FR-003/004). Pre-valida
 * BR-001..BR-003 (UX); ante 201 muestra la confirmación (FR-005) y refresca el
 * listado para que el saldo provenga del `CuentaDto` refrescado (FR-006,
 * BR-004). Errores del envelope: details → por campo, resto → general (FR-005).
 */
export default function CajaSection() {
  const {
    datos: clientes,
    cargando: cargandoClientes,
    error: errorClientes,
    recargar: recargarClientes,
  } = useClientes();
  const [clienteSeleccionado, setClienteSeleccionado] = useState<number | null>(null);
  const [erroresCliente, setErroresCliente] = useState<string | undefined>(undefined);

  return (
    <div className="seccion">
      <h2>Caja</h2>

      {cargandoClientes && clientes === null && <Cargando />}
      {errorClientes !== null && (
        <EstadoError mensaje={errorClientes.message} onReintentar={recargarClientes} />
      )}

      <CampoFormulario id="caja-cliente" label="Cliente" error={erroresCliente}>
        <select
          id="caja-cliente"
          value={clienteSeleccionado ?? ''}
          onChange={(e) => {
            setClienteSeleccionado(e.target.value !== '' ? Number(e.target.value) : null);
            setErroresCliente(undefined);
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
        <EstadoVacio mensaje="Seleccione un cliente para operar la caja." />
      ) : (
        <CajaDeCliente key={clienteSeleccionado} clienteId={clienteSeleccionado} />
      )}
    </div>
  );
}

/** Caja del cliente seleccionado: listado de cuentas + los dos formularios (FR-002/006). */
function CajaDeCliente({ clienteId }: { clienteId: number }) {
  const { datos, cargando, error, recargar } = useCuentas(clienteId);
  const cuentas = datos ?? [];
  const cuentasActivas = cuentas.filter((c) => c.estado === 'ACTIVA');

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
    <>
      <ul className="lista-cuentas">
        {cuentas.map((cuenta) => (
          <CuentaItem key={cuenta.id} cuenta={cuenta} />
        ))}
      </ul>
      {cuentasActivas.length === 0 && (
        <EstadoVacio mensaje="El cliente no tiene cuentas activas." />
      )}
      <FormularioCaja tipo="deposito" cuentasActivas={cuentasActivas} onOperacionExitosa={recargar} />
      <FormularioCaja tipo="retiro" cuentasActivas={cuentasActivas} onOperacionExitosa={recargar} />
    </>
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

/** Campos del envelope conocidos de la caja (whitelist — patrón TransferenciaPage). */
const CAMPOS_CAJA = new Set(['cuentaId', 'monto']);

function campoConocidoCaja(detalle: DetalleError): boolean {
  return CAMPOS_CAJA.has(detalle.campo);
}

interface FormularioCajaProps {
  tipo: TipoOperacionCaja;
  cuentasActivas: CuentaDto[];
  onOperacionExitosa: () => void;
}

/** Formulario de depósito o retiro de la caja (FR-003/004, A-001). */
function FormularioCaja({ tipo, cuentasActivas, onOperacionExitosa }: FormularioCajaProps) {
  const { enviando, confirmacion, error, ejecutar } = useCaja(tipo);
  const [cuentaId, setCuentaId] = useState('');
  const [monto, setMonto] = useState('');
  const [erroresCampo, setErroresCampo] = useState<ErroresPorCampo>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [cuentaOperada, setCuentaOperada] = useState<CuentaDto | null>(null);

  const titulo = tipo === 'deposito' ? 'Depósito' : 'Retiro';
  const sinCuentas = cuentasActivas.length === 0;

  // Mapeo del error de la API al formulario: details → errores por campo,
  // resto del envelope → mensaje general (FR-005, ERR-001..ERR-007).
  useEffect(() => {
    if (error === null) {
      return;
    }
    if (error.details !== undefined && error.details.length > 0) {
      const porCampo: ErroresPorCampo = {};
      let mensajeGeneral = error.message;
      for (const detalle of error.details) {
        if (campoConocidoCaja(detalle)) {
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
    const idCuenta = cuentaId !== '' ? Number(cuentaId) : null;
    const saldoCuenta = cuentasActivas.find((c) => String(c.id) === cuentaId)?.saldo;
    const erroresValidacion =
      tipo === 'deposito'
        ? validarDeposito(idCuenta, monto)
        : validarRetiro(idCuenta, monto, saldoCuenta);
    setErroresCampo(erroresValidacion);
    if (Object.keys(erroresValidacion).length > 0 || idCuenta === null) {
      return; // BR-001..BR-003: sin request
    }
    const cuenta = cuentasActivas.find((c) => c.id === idCuenta);
    setCuentaOperada(cuenta ?? null);
    setErrorGeneral(null);
    const resultado = await ejecutar(idCuenta, Number(monto.trim()));
    if (resultado !== null) {
      onOperacionExitosa(); // FR-006: recargar() de useCuentas — refresh del saldo
    }
  };

  return (
    <section>
      <h3>{titulo}</h3>

      {confirmacion !== null && cuentaOperada !== null ? (
        <ConfirmacionCaja tipo={tipo} confirmacion={confirmacion} cbu={cuentaOperada.cbu} />
      ) : (
        <>
          {errorGeneral !== null && (
            <div role="alert" className="error-general">
              {errorGeneral}
            </div>
          )}
          <form onSubmit={onSubmit} noValidate>
            <CampoFormulario
              id={`caja-cuenta-${tipo}`}
              label="Cuenta destino"
              error={erroresCampo.cuentaId}
            >
              <select
                id={`caja-cuenta-${tipo}`}
                value={cuentaId}
                onChange={(e) => {
                  setCuentaId(e.target.value);
                  setErroresCampo({});
                }}
                disabled={sinCuentas}
              >
                <option value="">
                  {sinCuentas ? 'No hay cuentas disponibles' : 'Seleccione una cuenta'}
                </option>
                {cuentasActivas.map((cuenta) => (
                  <option key={cuenta.id} value={cuenta.id}>
                    {cuenta.tipo} — CBU {cuenta.cbu} — {formatearMontoARS(cuenta.saldo)}
                  </option>
                ))}
              </select>
            </CampoFormulario>
            <CampoFormulario
              id={`caja-monto-${tipo}`}
              label={tipo === 'deposito' ? 'Monto depósito' : 'Monto retiro'}
              error={erroresCampo.monto}
            >
              <input
                id={`caja-monto-${tipo}`}
                name="monto"
                value={monto}
                onChange={(e) => {
                  setMonto(e.target.value);
                  setErroresCampo({});
                }}
                inputMode="decimal"
              />
            </CampoFormulario>
            <button type="submit" disabled={enviando || sinCuentas}>
              {enviando
                ? 'Procesando...'
                : tipo === 'deposito'
                  ? 'Depositar'
                  : 'Retirar'}
            </button>
          </form>
        </>
      )}
    </section>
  );
}

/** Panel de confirmación de la operación (patrón TransferenciaPage — FR-005). */
function ConfirmacionCaja({
  tipo,
  confirmacion,
  cbu,
}: {
  tipo: TipoOperacionCaja;
  confirmacion: ConfirmacionCaja;
  cbu: string;
}) {
  return (
    <div role="status" className="confirmacion">
      <h3>{tipo === 'deposito' ? 'Depósito realizado' : 'Retiro realizado'}</h3>
      <p>ID de movimiento: {confirmacion.idMovimiento}</p>
      <p>Monto: {formatearMontoARS(confirmacion.monto)}</p>
      <p>Cuenta (CBU): {cbu}</p>
      <p>Fecha y hora: {formatearFecha(confirmacion.fechaHora)}</p>
    </div>
  );
}