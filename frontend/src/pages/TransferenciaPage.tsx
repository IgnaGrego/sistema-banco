import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { CuentaDto, DetalleError, TransferenciaConfirmacion } from '../api/types';
import CampoFormulario from '../components/CampoFormulario';
import { useTransferencia } from '../hooks/useTransferencia';
import { formatearFecha, formatearMontoARS } from '../lib/session';
import { validarTransferencia } from '../lib/validacion';

/**
 * Formulario de transferencia del CLIENTE (FR-011). NO es una ruta (A-007):
 * sección montada dentro de /cuentas. Pre-validaciones BR-002..BR-005
 * (AC-020) bloquean el envío con errores por campo; submit → POST
 * /api/v1/transferencias con payload exacto `{cuentaOrigenId, cbuDestino,
 * monto}` (AC-017); 201 → panel de confirmación + callback
 * `onTransferenciaExitosa` (AC-018); 422 → mensaje del envelope sin perder
 * datos (ERR-006, AC-019); 400 con details → errores por campo (ERR-004).
 * Protección de doble envío (AC-030).
 */
interface TransferenciaPageProps {
  cuentasActivas: CuentaDto[];
  onTransferenciaExitosa: () => void;
}

const CAMPOS_CONOCIDOS = new Set(['cuentaOrigenId', 'cbuDestino', 'monto']);

function campoConocido(detalle: DetalleError): boolean {
  return CAMPOS_CONOCIDOS.has(detalle.campo);
}

export default function TransferenciaPage({
  cuentasActivas,
  onTransferenciaExitosa,
}: TransferenciaPageProps) {
  const { enviando, confirmacion, error, transferir } = useTransferencia();
  const [cuentaOrigenId, setCuentaOrigenId] = useState<string>('');
  const [cbuDestino, setCbuDestino] = useState('');
  const [monto, setMonto] = useState('');
  const [erroresCampo, setErroresCampo] = useState<Record<string, string>>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);

  const cuentaOrigenSeleccionada = cuentasActivas.find(
    (cuenta) => String(cuenta.id) === cuentaOrigenId,
  );

  // Mapeo del error de la API al formulario: details → errores por campo
  // (ERR-004), resto del envelope → mensaje general (ERR-006/007/008).
  useEffect(() => {
    if (error === null) {
      return;
    }
    if (error.details !== undefined && error.details.length > 0) {
      const porCampo: Record<string, string> = {};
      let mensajeGeneral = error.message;
      for (const detalle of error.details) {
        if (campoConocido(detalle)) {
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
    const idOrigen = cuentaOrigenId !== '' ? Number(cuentaOrigenId) : null;
    const erroresValidacion = validarTransferencia(
      idOrigen,
      cbuDestino,
      monto,
      cuentaOrigenSeleccionada?.cbu,
    );
    setErroresCampo(erroresValidacion);
    if (Object.keys(erroresValidacion).length > 0 || idOrigen === null) {
      return;
    }
    setErrorGeneral(null);
    const confirmacionRecibida = await transferir({
      cuentaOrigenId: idOrigen,
      cbuDestino: cbuDestino.trim(),
      monto: Number(monto.trim()),
    });
    if (confirmacionRecibida !== null) {
      onTransferenciaExitosa();
    }
  };

  const sinCuentasOrigen = cuentasActivas.length === 0;

  return (
    <section className="transferencia">
      <h2>Transferencia</h2>

      {confirmacion !== null ? (
        <ConfirmacionTransferencia confirmacion={confirmacion} />
      ) : (
        <>
          {errorGeneral !== null && (
            <div role="alert" className="error-general">
              {errorGeneral}
            </div>
          )}
          <form onSubmit={onSubmit} noValidate>
            <CampoFormulario
              id="cuenta-origen"
              label="Cuenta origen"
              error={erroresCampo.cuentaOrigenId}
            >
              <select
                id="cuenta-origen"
                value={cuentaOrigenId}
                onChange={(e) => setCuentaOrigenId(e.target.value)}
                disabled={sinCuentasOrigen}
              >
                <option value="">
                  {sinCuentasOrigen ? 'No hay cuentas disponibles' : 'Seleccione una cuenta'}
                </option>
                {cuentasActivas.map((cuenta) => (
                  <option key={cuenta.id} value={cuenta.id}>
                    {cuenta.tipo} — CBU {cuenta.cbu} — {formatearMontoARS(cuenta.saldo)}
                  </option>
                ))}
              </select>
            </CampoFormulario>
            <CampoFormulario id="cbu-destino" label="CBU destino" error={erroresCampo.cbuDestino}>
              <input
                id="cbu-destino"
                name="cbuDestino"
                value={cbuDestino}
                onChange={(e) => setCbuDestino(e.target.value)}
                inputMode="numeric"
                maxLength={22}
              />
            </CampoFormulario>
            <CampoFormulario id="monto" label="Monto" error={erroresCampo.monto}>
              <input
                id="monto"
                name="monto"
                value={monto}
                onChange={(e) => setMonto(e.target.value)}
                inputMode="decimal"
              />
            </CampoFormulario>
            <button type="submit" disabled={enviando || sinCuentasOrigen}>
              {enviando ? 'Enviando...' : 'Transferir'}
            </button>
          </form>
        </>
      )}
    </section>
  );
}

function ConfirmacionTransferencia({ confirmacion }: { confirmacion: TransferenciaConfirmacion }) {
  return (
    <div role="status" className="confirmacion">
      <h3>Transferencia realizada</h3>
      <p>ID de transferencia: {confirmacion.idTransferencia}</p>
      <p>Monto: {formatearMontoARS(confirmacion.monto)}</p>
      <p>CBU destino: {confirmacion.cbuDestino}</p>
      <p>Fecha y hora: {formatearFecha(confirmacion.fechaHora)}</p>
    </div>
  );
}
