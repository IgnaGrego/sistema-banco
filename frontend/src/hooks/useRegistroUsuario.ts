import { useCallback, useRef, useState } from 'react';
import { registro as registroApi } from '../api/auth';
import type { ApiError } from '../api/httpClient';
import type { RegistrarUsuarioRequest, UsuarioDto } from '../api/types';

/**
 * Hook del registro de usuario (SPEC-008 FR-004): envía el payload
 * `{username, password, rol, clienteId}` y expone
 * `{ enviando, confirmacion, error, ejecutar, limpiarConfirmacion }`.
 * Protección de doble envío (AC-030 de SPEC-006, FR-004): una request en
 * vuelo bloquea el siguiente submit (ref `enVuelo` + estado `enviando` para
 * deshabilitar el botón — AC-014). `limpiarConfirmacion` vuelve al
 * formulario tras un 201 (botón "Registrar otro usuario" — FR-004, A-005;
 * diseño SPEC-008 §5.5: "useRegistroUsuario expone una función").
 */
export function useRegistroUsuario() {
  const [enviando, setEnviando] = useState(false);
  const [confirmacion, setConfirmacion] = useState<UsuarioDto | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const enVuelo = useRef(false);

  const ejecutar = useCallback(
    async (body: RegistrarUsuarioRequest): Promise<UsuarioDto | null> => {
      if (enVuelo.current) {
        return null;
      }
      enVuelo.current = true;
      setEnviando(true);
      setError(null);
      setConfirmacion(null);
      try {
        const resultado = await registroApi(body);
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
    [],
  );

  const limpiarConfirmacion = useCallback(() => {
    setConfirmacion(null);
  }, []);

  return { enviando, confirmacion, error, ejecutar, limpiarConfirmacion };
}