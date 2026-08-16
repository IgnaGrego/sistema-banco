import { useCallback, useRef, useState } from 'react';
import { transferir as transferirApi } from '../api/transferencias';
import type { ApiError } from '../api/httpClient';
import type { TransferenciaConfirmacion, TransferirRequest } from '../api/types';

/**
 * Hook de la transferencia (FR-011): envía el payload y expone
 * `{ enviando, confirmacion, error, transferir }`. Protección de doble envío
 * (AC-030): una request en vuelo bloquea el siguiente submit (ref + estado
 * `enviando` para deshabilitar el botón).
 */
export function useTransferencia() {
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState<TransferenciaConfirmacion | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const enVuelo = useRef(false);

  const transferir = useCallback(
    async (body: TransferirRequest): Promise<TransferenciaConfirmacion | null> => {
      if (enVuelo.current) {
        return null;
      }
      enVuelo.current = true;
      setEnviando(true);
      setError(null);
      try {
        const confirmacionRecibida = await transferirApi(body);
        setConfirmacion(confirmacionRecibida);
        return confirmacionRecibida;
      } catch (e) {
        setError(e as ApiError);
        return null;
      } finally {
        enVuelo.current = false;
        setEnviando(false);
      }
    },
    [],
  );

  return { enviando, confirmacion, error, transferir };
}
