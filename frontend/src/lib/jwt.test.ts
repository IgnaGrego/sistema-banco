import { decodificarJwt } from './jwt';
import { crearToken } from '../test/helpers';

function base64url(texto: string): string {
  const bytes = new TextEncoder().encode(texto);
  let binario = '';
  bytes.forEach((b) => {
    binario += String.fromCharCode(b);
  });
  return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function tokenConPayload(payload: unknown): string {
  return `${base64url('{"alg":"HS256"}')}.${base64url(JSON.stringify(payload))}.firma`;
}

describe('decodificarJwt (FR-004, AC-007)', () => {
  it('extrae role, clienteId y username (claim sub) de un token CLIENTE (base64url, sin firma)', () => {
    const token = crearToken({ role: 'CLIENTE', clienteId: 42 });
    expect(decodificarJwt(token)).toEqual({ role: 'CLIENTE', clienteId: 42, username: 'usuario-test' });
  });

  it('extrae role y username de un token ADMIN (sin clienteId)', () => {
    const token = crearToken({ role: 'ADMIN' });
    expect(decodificarJwt(token)).toEqual({ role: 'ADMIN', username: 'usuario-test' });
  });

  it('A-005 — token sin claim sub: no expone la clave username (fallback R2)', () => {
    const token = tokenConPayload({ role: 'ADMIN' });
    expect(decodificarJwt(token)).toEqual({ role: 'ADMIN' });
    expect(decodificarJwt(token)).not.toHaveProperty('username');
  });

  it('A-005 — claim sub no string: no expone la clave username', () => {
    expect(decodificarJwt(tokenConPayload({ role: 'ADMIN', sub: 123 }))).toEqual({ role: 'ADMIN' });
    expect(decodificarJwt(tokenConPayload({ role: 'CLIENTE', clienteId: 1, sub: 123 }))).toEqual({
      role: 'CLIENTE',
      clienteId: 1,
    });
  });

  it('devuelve null con un token de 2 segmentos (malformado)', () => {
    expect(decodificarJwt('solo.header')).toBeNull();
  });

  it('devuelve null con base64 inválido en el payload', () => {
    expect(decodificarJwt('a.b@@@.c')).toBeNull();
  });

  it('devuelve null cuando el payload no es JSON', () => {
    const token = `${base64url('{"alg":"HS256"}')}.${base64url('no-es-json')}.firma`;
    expect(decodificarJwt(token)).toBeNull();
  });

  it('devuelve null cuando el payload no es un objeto', () => {
    expect(decodificarJwt(tokenConPayload('texto'))).toBeNull();
    expect(decodificarJwt(tokenConPayload([1, 2, 3]))).toBeNull();
  });

  it('devuelve null con un rol desconocido', () => {
    expect(decodificarJwt(tokenConPayload({ role: 'OPERATOR' }))).toBeNull();
    expect(decodificarJwt(tokenConPayload({}))).toBeNull();
  });

  it('devuelve null cuando un token CLIENTE no trae clienteId', () => {
    expect(decodificarJwt(tokenConPayload({ role: 'CLIENTE' }))).toBeNull();
  });

  it('devuelve null cuando clienteId no es un número entero', () => {
    expect(decodificarJwt(tokenConPayload({ role: 'CLIENTE', clienteId: 'abc' }))).toBeNull();
    expect(decodificarJwt(tokenConPayload({ role: 'CLIENTE', clienteId: 1.5 }))).toBeNull();
  });

  it('token ADMIN sin firma (payload válido) se decodifica — la firma no se verifica (A-002)', () => {
    const token = tokenConPayload({ role: 'ADMIN' });
    expect(decodificarJwt(token)).toEqual({ role: 'ADMIN' });
  });
});
