import { useCallback, useEffect, useState } from 'react';
import { obtenerMovimientos } from '../api/cuentas';
import type { ApiError } from '../api/httpClient';
import type { MovimientoDto } from '../api/types';

/** Hook de datos del historial de una cuenta (FR-010, AC-016). */
export function useMovimientos(cuentaId: number) {
  const [datos, setDatos] = useState<MovimientoDto[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const recargar = useCallback(async () => {
    setCargando(true);
    setError(null);
    try {
      setDatos(await obtenerMovimientos(cuentaId));
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setCargando(false);
    }
  }, [cuentaId]);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { datos, cargando, error, recargar };
}
