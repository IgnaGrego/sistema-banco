/**
 * Clave de sesión en `localStorage` (A-003, FR-003). Dato efímero del
 * navegador: se guarda al iniciar sesión, se restaura al cargar la SPA y se
 * elimina con logout (FR-014) o ante un 401 en una request autenticada
 * (FR-007). El token nunca se adjunta a orígenes fuera de `/api/v1` (BR-009)
 * ni se loguea (AC-031).
 */
export const CLAVE_TOKEN = 'banco.token';

export function guardarToken(token: string): void {
  localStorage.setItem(CLAVE_TOKEN, token);
}

export function leerToken(): string | null {
  return localStorage.getItem(CLAVE_TOKEN);
}

export function limpiarToken(): void {
  localStorage.removeItem(CLAVE_TOKEN);
}

const formateadorARS = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * Formatea montos en ARS con 2 decimales (formato local es-AR — BR-010).
 * El frontend NO calcula montos: solo formatea los BigDecimal que devuelve
 * el backend (BR-010, ARCHITECTURE.md §6). El separador de miles/centésimos
 * que produce ICU puede incluir espacio de no separación (U+00A0); se
 * normaliza a espacio regular para consistencia del texto renderizado
 * (queries de tests y comparaciones exactas de texto).
 */
export function formatearMontoARS(monto: number): string {
  return formateadorARS.format(monto).replace(/\u00A0/g, ' ');
}

/**
 * Formatea una fecha ISO-8601 (Instant del backend, serializado por Jackson)
 * como `dd/mm/aaaa hh:mm` para display. Utilidad de presentación pura
 * (extensión menor de `src/lib/` — el frontend solo formatea, no calcula).
 */
export function formatearFecha(fecha: string): string {
  const d = new Date(fecha);
  if (Number.isNaN(d.getTime())) {
    return fecha;
  }
  const dia = String(d.getDate()).padStart(2, '0');
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const anio = d.getFullYear();
  const hora = String(d.getHours()).padStart(2, '0');
  const minuto = String(d.getMinutes()).padStart(2, '0');
  return `${dia}/${mes}/${anio} ${hora}:${minuto}`;
}
