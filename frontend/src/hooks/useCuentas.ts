import { useCallback, useEffect, useState } from 'react';
import { listarCuentas } from '../api/cuentas';
import type { ApiError } from '../api/httpClient';
import type { CuentaDto } from '../api/types';

/**
 * Hook de datos de cuentas (FR-009/FR-013): estado `{ datos, cargando,
 * error }` + `recargar()`. Sin `clienteId` → GET /api/v1/cuentas sin
 * parámetros (CLIENTE); con `clienteId` → `?clienteId=` (ADMIN).
 */
export function useCuentas(clienteId?: number) {
  const [datos, setDatos] = useState<CuentaDto[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const recargar = useCallback(async () => {
    setCargando(true);
    setError(null);
    try {
      setDatos(await listarCuentas(clienteId));
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setCargando(false);
    }
  }, [clienteId]);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { datos, cargando, error, recargar };
}
