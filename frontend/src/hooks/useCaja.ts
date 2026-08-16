import { useCallback, useRef, useState } from 'react';
import { depositar as depositarApi, retirar as retirarApi } from '../api/caja';
import type { ApiError } from '../api/httpClient';
import type { DepositoConfirmacion, RetiroConfirmacion } from '../api/types';

/** Operación de caja de la sección (A-001: dos formularios, una sección). */
export type TipoOperacionCaja = 'deposito' | 'retiro';

/** Unión de las confirmaciones 201 (misma forma {idMovimiento, cuentaId, monto, fechaHora}). */
export type ConfirmacionCaja = DepositoConfirmacion | RetiroConfirmacion;

/**
 * Hook de la caja (SPEC-007 FR-005): envía el payload `{cuentaId, monto}` de
 * una operación y expone `{ enviando, confirmacion, error, ejecutar }`.
 * Protección de doble envío (AC-030 de SPEC-006): una request en vuelo bloquea
 * el siguiente submit (ref `enVuelo` + estado `enviando` para deshabilitar el
 * botón — AC-018). Una instancia por formulario (estado independiente).
 */
export function useCaja(tipo: TipoOperacionCaja) {
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState<ConfirmacionCaja | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const enVuelo = useRef(false);

  const ejecutar = useCallback(
    async (cuentaId: number, monto: number): Promise<ConfirmacionCaja | null> => {
      if (enVuelo.current) {
        return null;
      }
      enVuelo.current = true;
      setEnviando(true);
      setError(null);
      setConfirmacion(null);
      try {
        const resultado =
          tipo === 'deposito'
            ? await depositarApi({ cuentaId, monto })
            : await retirarApi({ cuentaId, monto });
        setConfirmacion(resultado);
        return resultado;
      } catch (e) {
        setError(e as ApiError);
        return null;
      } finally {
        enVuelo.current = false;
        setEnviando(false);
      }
    },
    [tipo],
  );

  return { enviando, confirmacion, error, ejecutar };
}