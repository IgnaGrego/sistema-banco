import { useCallback, useEffect, useState } from 'react';
import { listarClientes } from '../api/clientes';
import type { ApiError } from '../api/httpClient';
import type { ClienteDto } from '../api/types';

/** Hook de datos de clientes (FR-012, AC-022). */
export function useClientes() {
  const [datos, setDatos] = useState<ClienteDto[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const recargar = useCallback(async () => {
    setCargando(true);
    setError(null);
    try {
      setDatos(await listarClientes());
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  return { datos, cargando, error, recargar };
}
