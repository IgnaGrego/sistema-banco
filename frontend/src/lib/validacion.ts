import type { TipoCuenta } from '../api/types';

/**
 * Pre-validaciones de UX que espejan reglas ya enforced por el backend
 * (BR-001..BR-007, A-008): el backend permanece como fuente de verdad y
 * punto de enforcement (BR-008). Devuelven un mapa `campo → mensaje` vacío
 * cuando la entrada es válida.
 */
export type ErroresPorCampo = Record<string, string>;

const REGEX_CBU = /^[0-9]{22}$/;
const REGEX_EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const REGEX_TELEFONO = /^\+?[0-9]{6,15}$/;
const REGEX_MONTO = /^\d+(\.\d{1,2})?$/;

/** BR-001 — login: username y password obligatorios (tras recortar espacios). */
export function validarLogin(username: string, password: string): ErroresPorCampo {
  const errores: ErroresPorCampo = {};
  if (username.trim() === '') {
    errores.username = 'El usuario es obligatorio';
  }
  if (password.trim() === '') {
    errores.password = 'La contraseña es obligatoria';
  }
  return errores;
}

/**
 * BR-002..BR-005 — transferencia: cuenta origen obligatoria (propia y
 * ACTIVA, garantizado por el selector), CBU destino `^[0-9]{22}$`, monto > 0
 * con hasta 2 decimales y CBU destino distinto del CBU origen.
 */
export function validarTransferencia(
  cuentaOrigenId: number | null,
  cbuDestino: string,
  monto: string,
  cbuOrigenSeleccionado: string | undefined,
): ErroresPorCampo {
  const errores: ErroresPorCampo = {};
  if (cuentaOrigenId === null) {
    errores.cuentaOrigenId = 'Seleccione la cuenta origen';
  }
  const cbu = cbuDestino.trim();
  if (cbu === '') {
    errores.cbuDestino = 'El CBU destino es obligatorio';
  } else if (!REGEX_CBU.test(cbu)) {
    errores.cbuDestino = 'El CBU debe tener exactamente 22 dígitos';
  } else if (cbu === cbuOrigenSeleccionado) {
    errores.cbuDestino = 'El CBU destino debe ser distinto del CBU de la cuenta origen';
  }
  const montoRecortado = monto.trim();
  if (montoRecortado === '') {
    errores.monto = 'El monto es obligatorio';
  } else if (!REGEX_MONTO.test(montoRecortado)) {
    errores.monto = 'El monto debe ser un número con hasta 2 decimales';
  } else if (Number(montoRecortado) <= 0) {
    errores.monto = 'El monto debe ser mayor a 0';
  }
  return errores;
}

/** BR-006 — cliente (alta/edición): espejo de SPEC-001 BR-002..BR-005. */
export function validarCliente(
  nombre: string,
  apellido: string,
  dni: string,
  email: string,
  telefono: string,
): ErroresPorCampo {
  const errores: ErroresPorCampo = {};
  if (nombre.trim() === '') {
    errores.nombre = 'El nombre es obligatorio';
  } else if (nombre.trim().length > 100) {
    errores.nombre = 'El nombre no puede superar los 100 caracteres';
  }
  if (apellido.trim() === '') {
    errores.apellido = 'El apellido es obligatorio';
  } else if (apellido.trim().length > 100) {
    errores.apellido = 'El apellido no puede superar los 100 caracteres';
  }
  const dniRecortado = dni.trim();
  if (dniRecortado === '') {
    errores.dni = 'El DNI es obligatorio';
  } else if (!/^\d{7,8}$/.test(dniRecortado)) {
    errores.dni = 'El DNI debe tener entre 7 y 8 dígitos';
  }
  const emailRecortado = email.trim();
  if (emailRecortado === '') {
    errores.email = 'El email es obligatorio';
  } else if (emailRecortado.length > 254) {
    errores.email = 'El email no puede superar los 254 caracteres';
  } else if (!REGEX_EMAIL.test(emailRecortado)) {
    errores.email = 'El email no tiene un formato válido';
  }
  const telefonoRecortado = telefono.trim();
  if (telefonoRecortado !== '' && !REGEX_TELEFONO.test(telefonoRecortado)) {
    errores.telefono = 'El teléfono debe tener entre 6 y 15 dígitos, opcionalmente precedido por +';
  }
  return errores;
}

/** BR-007 — apertura de cuenta: clienteId obligatorio, tipo válido, moneda opcional (default ARS, solo ARS en el MVP). */
export function validarAperturaCuenta(
  clienteId: number | null,
  tipo: TipoCuenta | '',
  moneda: string,
): ErroresPorCampo {
  const errores: ErroresPorCampo = {};
  if (clienteId === null) {
    errores.clienteId = 'Seleccione un cliente';
  }
  if (tipo !== 'CAJA_AHORRO' && tipo !== 'CUENTA_CORRIENTE') {
    errores.tipo = 'Seleccione un tipo de cuenta';
  }
  const monedaRecortada = moneda.trim();
  if (monedaRecortada !== '' && monedaRecortada !== 'ARS') {
    errores.moneda = 'Solo se admite la moneda ARS';
  }
  return errores;
}
