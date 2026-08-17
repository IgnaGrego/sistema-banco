import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { login as loginApi } from '../api/auth';
import { setOnNoAutorizado } from '../api/httpClient';
import type { Rol } from '../api/types';
import { decodificarJwt } from '../lib/jwt';
import { guardarToken, leerToken, limpiarToken } from '../lib/session';

/**
 * Contexto de sesión/rol (A-006): único estado compartido de la SPA.
 * Restaura la sesión desde `localStorage` (FR-003), expone `login` (FR-005),
 * `logout` (FR-014) y `sesionExpirada` (FR-007 — invocada por el callback
 * `onNoAutorizado` del httpClient ante un 401 en una request autenticada).
 */

interface EstadoSesion {
  token: string | null;
  rol: Rol | null;
  clienteId?: number;
  /** Claim `sub` del JWT (SPEC-003 §6.3: sub = username) — A-005 (SPEC-009 FR-003). */
  username?: string;
  cargando: boolean;
}

interface AuthContextValue extends EstadoSesion {
  login: (username: string, password: string) => Promise<Rol>;
  logout: () => void;
  sesionExpirada: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSesion>({
    token: null,
    rol: null,
    cargando: true,
  });

  // Restauración de la sesión al cargar la SPA (FR-003, ERR-002: un token no
  // decodificable se trata como sesión inválida y se limpia).
  useEffect(() => {
    const token = leerToken();
    if (token === null) {
      setEstado({ token: null, rol: null, cargando: false });
      return;
    }
    const claims = decodificarJwt(token);
    if (claims === null) {
      limpiarToken();
      setEstado({ token: null, rol: null, cargando: false });
      return;
    }
    setEstado({ token, rol: claims.role, clienteId: claims.clienteId, username: claims.username, cargando: false });
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<Rol> => {
    const token = await loginApi({ username, password });
    const claims = decodificarJwt(token);
    if (claims === null) {
      // El backend devuelve tokens firmados y válidos; defensivo (FR-004).
      limpiarToken();
      throw new Error('Token de sesión inválido');
    }
    guardarToken(token);
    setEstado({ token, rol: claims.role, clienteId: claims.clienteId, username: claims.username, cargando: false });
    return claims.role;
  }, []);

  const limpiarSesion = useCallback(() => {
    limpiarToken();
    setEstado({ token: null, rol: null, cargando: false });
  }, []);

  const logout = useCallback(() => {
    limpiarSesion();
  }, [limpiarSesion]);

  const sesionExpirada = useCallback(() => {
    limpiarSesion();
  }, [limpiarSesion]);

  // Registro del callback 401 del httpClient (FR-007, AF-002).
  useEffect(() => {
    setOnNoAutorizado(sesionExpirada);
    return () => {
      setOnNoAutorizado(null);
    };
  }, [sesionExpirada]);

  const value = useMemo(
    () => ({ ...estado, login, logout, sesionExpirada }),
    [estado, login, logout, sesionExpirada],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const contexto = useContext(AuthContext);
  if (contexto === undefined) {
    throw new Error('useAuth debe usarse dentro de un AuthProvider');
  }
  return contexto;
}
