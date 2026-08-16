import { CLAVE_TOKEN, formatearFecha, formatearMontoARS, guardarToken, leerToken, limpiarToken } from './session';

describe('helpers de sesión (FR-003, FR-014, AC-006, AC-031)', () => {
  it('guardarToken almacena el token bajo la clave banco.token', () => {
    guardarToken('token-123');
    expect(localStorage.getItem(CLAVE_TOKEN)).toBe('token-123');
  });

  it('leerToken devuelve el token guardado y null sin sesión', () => {
    expect(leerToken()).toBeNull();
    guardarToken('token-456');
    expect(leerToken()).toBe('token-456');
  });

  it('limpiarToken elimina el token de localStorage', () => {
    guardarToken('token-789');
    limpiarToken();
    expect(leerToken()).toBeNull();
    expect(localStorage.getItem(CLAVE_TOKEN)).toBeNull();
  });
});

describe('formatearMontoARS (BR-010, AC-014)', () => {
  it('formatea montos en ARS con 2 decimales y separador de miles', () => {
    const formateado = formatearMontoARS(1500);
    expect(formateado).toMatch(/1\.500,00/);
    expect(formateado).toContain('$');
  });

  it('mantiene 2 decimales en montos con una sola cifra decimal', () => {
    expect(formatearMontoARS(10.5)).toMatch(/10,50/);
  });

  it('formatea cero y montos pequeños', () => {
    expect(formatearMontoARS(0)).toMatch(/0,00/);
    expect(formatearMontoARS(0.99)).toMatch(/0,99/);
  });
});

describe('formatearFecha (display de Instant ISO-8601)', () => {
  it('formatea una fecha ISO-8601 como dd/mm/aaaa hh:mm', () => {
    const fecha = '2026-08-10T15:30:00';
    expect(formatearFecha(fecha)).toMatch(/^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);
  });

  it('devuelve la entrada sin cambios si no es una fecha válida', () => {
    expect(formatearFecha('no-es-fecha')).toBe('no-es-fecha');
  });
});
