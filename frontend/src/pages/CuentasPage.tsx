import { useCallback, useState } from 'react';
import Cargando from '../components/Cargando';
import EstadoError from '../components/EstadoError';
import EstadoVacio from '../components/EstadoVacio';
import Monto from '../components/Monto';
import { useCuentas } from '../hooks/useCuentas';
import { useMovimientos } from '../hooks/useMovimientos';
import { formatearFecha } from '../lib/session';
import type { CuentaDto, MovimientoDto } from '../api/types';
import TransferenciaPage from './TransferenciaPage';

const ETIQUETA_TIPO: Record<CuentaDto['tipo'], string> = {
  CAJA_AHORRO: 'Caja de ahorro',
  CUENTA_CORRIENTE: 'Cuenta corriente',
};

/**
 * Vista /cuentas del CLIENTE (FR-009/FR-010/FR-011): lista de cuentas propias
 * con saldo (GET /api/v1/cuentas sin parámetros — el backend resuelve el
 * clienteId del claim), historial expandible por cuenta (FR-010, AC-016),
 * estados de carga/error/vacío (FR-015, ERR-008/009, AF-003) y la sección de
 * transferencia (BR-002..BR-005). Tras una transferencia exitosa refresca
 * cuentas y movimientos (AF-004, AC-018).
 */
export default function CuentasPage() {
  const { datos: cuentas, cargando, error, recargar } = useCuentas();
  const [cuentaExpandida, setCuentaExpandida] = useState<number | null>(null);
  const [claveMovimientos, setClaveMovimientos] = useState(0);

  const cuentasActivas = useCallback(
    () => (cuentas ?? []).filter((cuenta) => cuenta.estado === 'ACTIVA'),
    [cuentas],
  );

  const alTransferir = useCallback(() => {
    // Refresca saldos (FR-009) y vuelve a cargar el historial de la cuenta
    // expandida (FR-010, AF-004, AC-018).
    setClaveMovimientos((clave) => clave + 1);
    void recargar();
  }, [recargar]);

  const alternarExpansion = (cuentaId: number) => {
    setCuentaExpandida((actual) => (actual === cuentaId ? null : cuentaId));
  };

  return (
    <section>
      <h1>Mis cuentas</h1>

      {cargando && cuentas === null && <Cargando />}
      {error !== null && <EstadoError mensaje={error.message} onReintentar={recargar} />}

      {!cargando && error === null && cuentas !== null && cuentas.length === 0 && (
        <EstadoVacio mensaje="No tiene cuentas en este momento." />
      )}

      {cuentas !== null && cuentas.length > 0 && (
        <ul className="lista-cuentas">
          {cuentas.map((cuenta) => (
            <li key={cuenta.id} className="cuenta">
              <div className="cuenta-resumen">
                <span className="cuenta-tipo">{ETIQUETA_TIPO[cuenta.tipo]}</span>
                <span className="cuenta-cbu">CBU: {cuenta.cbu}</span>
                <span className="cuenta-saldo">
                  <Monto monto={cuenta.saldo} />
                </span>
                <span className="cuenta-moneda">{cuenta.moneda}</span>
                <span className={`cuenta-estado cuenta-estado-${cuenta.estado.toLowerCase()}`}>
                  {cuenta.estado}
                </span>
                <button type="button" onClick={() => alternarExpansion(cuenta.id)}>
                  {cuentaExpandida === cuenta.id ? 'Ocultar movimientos' : 'Ver movimientos'}
                </button>
              </div>
              {cuentaExpandida === cuenta.id && (
                <MovimientosCuenta
                  key={`${cuenta.id}-${claveMovimientos}`}
                  cuentaId={cuenta.id}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      <TransferenciaPage cuentasActivas={cuentasActivas()} onTransferenciaExitosa={alTransferir} />
    </section>
  );
}

/** Historial de movimientos de una cuenta (FR-010, AC-016), con su recarga propia. */
function MovimientosCuenta({ cuentaId }: { cuentaId: number }) {
  const { datos, cargando, error, recargar } = useMovimientos(cuentaId);

  if (cargando && datos === null) {
    return <Cargando />;
  }
  if (error !== null) {
    return <EstadoError mensaje={error.message} onReintentar={recargar} />;
  }
  if (datos === null || datos.length === 0) {
    return <EstadoVacio mensaje="La cuenta no tiene movimientos." />;
  }
  return (
    <ul className="lista-movimientos">
      {datos.map((movimiento) => (
        <MovimientoItem key={movimiento.id} movimiento={movimiento} />
      ))}
    </ul>
  );
}

function MovimientoItem({ movimiento }: { movimiento: MovimientoDto }) {
  return (
    <li className="movimiento">
      <span className={`movimiento-tipo movimiento-tipo-${movimiento.tipo.toLowerCase()}`}>
        {movimiento.tipo}
      </span>
      <Monto monto={movimiento.monto} />
      <span className="movimiento-moneda">{movimiento.moneda}</span>
      <span className="movimiento-fecha">{formatearFecha(movimiento.fecha)}</span>
    </li>
  );
}
