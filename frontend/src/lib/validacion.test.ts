import {
  validarAperturaCuenta,
  validarCliente,
  validarLogin,
  validarTransferencia,
} from './validacion';

describe('validarLogin (BR-001)', () => {
  it('acepta username y password no vacíos', () => {
    expect(validarLogin('juan', 'secreta')).toEqual({});
  });

  it('rechaza campos vacíos (o solo espacios)', () => {
    expect(validarLogin('', 'secreta')).toHaveProperty('username');
    expect(validarLogin('juan', '')).toHaveProperty('password');
    expect(validarLogin('   ', '   ')).toHaveProperty('username');
    expect(validarLogin('   ', '   ')).toHaveProperty('password');
  });
});

describe('validarTransferencia (BR-002..BR-005, AC-020)', () => {
  const cbu = '0000003100000000000001';

  it('acepta una transferencia válida', () => {
    expect(validarTransferencia(1, cbu, '100.50', undefined)).toEqual({});
  });

  it('rechaza sin cuenta origen', () => {
    expect(validarTransferencia(null, cbu, '100', undefined)).toHaveProperty('cuentaOrigenId');
  });

  it('rechaza CBU vacío', () => {
    expect(validarTransferencia(1, '', '100', undefined)).toHaveProperty('cbuDestino');
  });

  it('rechaza CBU con menos de 22 dígitos', () => {
    expect(validarTransferencia(1, '123', '100', undefined)).toHaveProperty('cbuDestino');
  });

  it('rechaza CBU con caracteres no numéricos', () => {
    expect(validarTransferencia(1, '000000310000000000000a', '100', undefined)).toHaveProperty('cbuDestino');
  });

  it('rechaza CBU igual al CBU de la cuenta origen (BR-005)', () => {
    expect(validarTransferencia(1, cbu, '100', cbu)).toHaveProperty('cbuDestino');
  });

  it('rechaza monto vacío', () => {
    expect(validarTransferencia(1, cbu, '', undefined)).toHaveProperty('monto');
  });

  it('rechaza monto cero o negativo (BR-004)', () => {
    expect(validarTransferencia(1, cbu, '0', undefined)).toHaveProperty('monto');
    expect(validarTransferencia(1, cbu, '-5', undefined)).toHaveProperty('monto');
  });

  it('rechaza monto con más de 2 decimales (BR-004)', () => {
    expect(validarTransferencia(1, cbu, '10.555', undefined)).toHaveProperty('monto');
  });

  it('rechaza monto no numérico', () => {
    expect(validarTransferencia(1, cbu, 'abc', undefined)).toHaveProperty('monto');
  });

  it('acepta monto sin decimales y con hasta 2 decimales', () => {
    expect(validarTransferencia(1, cbu, '100', undefined)).toEqual({});
    expect(validarTransferencia(1, cbu, '100.5', undefined)).toEqual({});
  });
});

describe('validarCliente (BR-006)', () => {
  const valido = { nombre: 'Juan', apellido: 'Pérez', dni: '30111222', email: 'juan@test.com', telefono: '+541155667788' };

  it('acepta un cliente válido', () => {
    expect(validarCliente(valido.nombre, valido.apellido, valido.dni, valido.email, valido.telefono)).toEqual({});
  });

  it('rechaza nombre y apellido vacíos', () => {
    const errores = validarCliente('', '', valido.dni, valido.email, '');
    expect(errores).toHaveProperty('nombre');
    expect(errores).toHaveProperty('apellido');
  });

  it('rechaza nombre/apellido de más de 100 caracteres', () => {
    const largo = 'a'.repeat(101);
    const errores = validarCliente(largo, valido.apellido, valido.dni, valido.email, '');
    expect(errores).toHaveProperty('nombre');
  });

  it('rechaza DNI con menos de 7 o más de 8 dígitos y no numérico', () => {
    expect(validarCliente(valido.nombre, valido.apellido, '123456', valido.email, '')).toHaveProperty('dni');
    expect(validarCliente(valido.nombre, valido.apellido, '123456789', valido.email, '')).toHaveProperty('dni');
    expect(validarCliente(valido.nombre, valido.apellido, '12a4567', valido.email, '')).toHaveProperty('dni');
  });

  it('rechaza email vacío o malformado', () => {
    expect(validarCliente(valido.nombre, valido.apellido, valido.dni, '', '')).toHaveProperty('email');
    expect(validarCliente(valido.nombre, valido.apellido, valido.dni, 'no-es-email', '')).toHaveProperty('email');
  });

  it('rechaza telefono con formato inválido y acepta vacío', () => {
    expect(validarCliente(valido.nombre, valido.apellido, valido.dni, valido.email, 'abc')).toHaveProperty('telefono');
    expect(validarCliente(valido.nombre, valido.apellido, valido.dni, valido.email, '')).not.toHaveProperty('telefono');
  });
});

describe('validarAperturaCuenta (BR-007)', () => {
  it('acepta una apertura válida con moneda default ARS', () => {
    expect(validarAperturaCuenta(3, 'CAJA_AHORRO', 'ARS')).toEqual({});
    expect(validarAperturaCuenta(3, 'CUENTA_CORRIENTE', '')).toEqual({});
  });

  it('rechaza sin cliente seleccionado', () => {
    expect(validarAperturaCuenta(null, 'CAJA_AHORRO', 'ARS')).toHaveProperty('clienteId');
  });

  it('rechaza tipo de cuenta inválido o vacío', () => {
    expect(validarAperturaCuenta(3, '', 'ARS')).toHaveProperty('tipo');
    // `as never` solo para el test: el tipo TS restringe la entrada, la
    // validación cubre valores no contemplados (BR-007).
    expect(validarAperturaCuenta(3, 'CUENTA_INTERNACIONAL' as never, 'ARS')).toHaveProperty('tipo');
  });

  it('rechaza moneda distinta de ARS (solo ARS en el MVP)', () => {
    expect(validarAperturaCuenta(3, 'CAJA_AHORRO', 'USD')).toHaveProperty('moneda');
  });
});
