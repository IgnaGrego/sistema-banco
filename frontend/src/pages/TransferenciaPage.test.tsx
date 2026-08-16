import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TransferenciaPage from './TransferenciaPage';
import { mockFetchDiferido, mockFetchErrorRed, mockFetchRespuestas } from '../test/helpers';
import type { CuentaDto } from '../api/types';

const CUENTAS_ACTIVAS: CuentaDto[] = [
  {
    id: 1,
    clienteId: 10,
    cbu: '0000003100000000000001',
    tipo: 'CAJA_AHORRO',
    saldo: 1500.5,
    moneda: 'ARS',
    estado: 'ACTIVA',
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    clienteId: 10,
    cbu: '0000003100000000000002',
    tipo: 'CUENTA_CORRIENTE',
    saldo: 800,
    moneda: 'ARS',
    estado: 'ACTIVA',
    createdAt: '2026-01-02T00:00:00Z',
  },
];

function renderizarTransferencia(onTransferenciaExitosa = vi.fn()) {
  render(
    <TransferenciaPage cuentasActivas={CUENTAS_ACTIVAS} onTransferenciaExitosa={onTransferenciaExitosa} />,
  );
  return onTransferenciaExitosa;
}

async function completarFormulario(usuario: ReturnType<typeof userEvent.setup>, monto: string) {
  await usuario.selectOptions(screen.getByLabelText('Cuenta origen'), '1');
  await usuario.type(screen.getByLabelText('CBU destino'), '0000003100000000000099');
  await usuario.type(screen.getByLabelText('Monto'), monto);
}

describe('TransferenciaPage (FR-011, BR-002..BR-005, AC-017..AC-020, AC-028, AC-029, AC-030)', () => {
  it('AC-017 — el formulario envía el payload exacto {cuentaOrigenId, cbuDestino, monto}', async () => {
    const fetchMock = mockFetchRespuestas([
      {
        url: '/api/v1/transferencias',
        status: 201,
        cuerpo: {
          idTransferencia: 55,
          monto: 100,
          cbuDestino: '0000003100000000000099',
          fechaHora: '2026-08-10T15:30:00Z',
        },
      },
    ]);
    const usuario = userEvent.setup();
    renderizarTransferencia();

    await completarFormulario(usuario, '100');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    await screen.findByText('Transferencia realizada');
    const llamada = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/v1/transferencias'));
    expect(llamada).toBeDefined();
    expect(JSON.parse((llamada![1] as RequestInit).body as string)).toEqual({
      cuentaOrigenId: 1,
      cbuDestino: '0000003100000000000099',
      monto: 100,
    });
  });

  it('AC-018 — 201: muestra la confirmación y notifica onTransferenciaExitosa', async () => {
    mockFetchRespuestas([
      {
        url: '/api/v1/transferencias',
        status: 201,
        cuerpo: {
          idTransferencia: 55,
          monto: 100,
          cbuDestino: '0000003100000000000099',
          fechaHora: '2026-08-10T15:30:00Z',
        },
      },
    ]);
    const usuario = userEvent.setup();
    const alTransferir = renderizarTransferencia();

    await completarFormulario(usuario, '100');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(await screen.findByText('Transferencia realizada')).toBeInTheDocument();
    expect(screen.getByText(/ID de transferencia: 55/)).toBeInTheDocument();
    expect(screen.getByText(/CBU destino: 0000003100000000000099/)).toBeInTheDocument();
    expect(alTransferir).toHaveBeenCalledTimes(1);
  });

  it('AC-019 — 422 SALDO_INSUFICIENTE: mensaje del envelope y datos conservados', async () => {
    mockFetchRespuestas([
      {
        url: '/api/v1/transferencias',
        status: 422,
        cuerpo: { code: 'SALDO_INSUFICIENTE', message: 'Saldo insuficiente en la cuenta origen' },
      },
    ]);
    const usuario = userEvent.setup();
    renderizarTransferencia();

    await completarFormulario(usuario, '99999');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(await screen.findByText('Saldo insuficiente en la cuenta origen')).toBeInTheDocument();
    // Los datos ingresados no se pierden (ERR-006, AC-019).
    expect(screen.getByLabelText('CBU destino')).toHaveValue('0000003100000000000099');
    expect(screen.getByLabelText('Monto')).toHaveValue('99999');
  });

  it('AC-028 — 422 LIMITE_DIARIO_EXCEDIDO: se muestra el mensaje del envelope', async () => {
    mockFetchRespuestas([
      {
        url: '/api/v1/transferencias',
        status: 422,
        cuerpo: { code: 'LIMITE_DIARIO_EXCEDIDO', message: 'Se alcanzó el límite diario de transferencias' },
      },
    ]);
    const usuario = userEvent.setup();
    renderizarTransferencia();

    await completarFormulario(usuario, '500');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(
      await screen.findByText('Se alcanzó el límite diario de transferencias'),
    ).toBeInTheDocument();
  });

  it('AC-020 — pre-validaciones BR-002..BR-005 bloquean el envío con errores por campo', async () => {
    const fetchMock = mockFetchRespuestas([
      { url: '/api/v1/transferencias', status: 201, cuerpo: {} },
    ]);
    const usuario = userEvent.setup();
    renderizarTransferencia();

    // Sin cuenta origen + CBU corto + monto 0.
    await usuario.type(screen.getByLabelText('CBU destino'), '123');
    await usuario.type(screen.getByLabelText('Monto'), '0');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(screen.getByText('Seleccione la cuenta origen')).toBeInTheDocument();
    expect(screen.getByText('El CBU debe tener exactamente 22 dígitos')).toBeInTheDocument();
    expect(screen.getByText('El monto debe ser mayor a 0')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    // CBU = CBU de la cuenta origen (BR-005) y monto con más de 2 decimales.
    await usuario.selectOptions(screen.getByLabelText('Cuenta origen'), '1');
    await usuario.clear(screen.getByLabelText('CBU destino'));
    await usuario.type(screen.getByLabelText('CBU destino'), '0000003100000000000001');
    await usuario.clear(screen.getByLabelText('Monto'));
    await usuario.type(screen.getByLabelText('Monto'), '10.555');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(screen.getByText('El CBU destino debe ser distinto del CBU de la cuenta origen')).toBeInTheDocument();
    expect(screen.getByText('El monto debe ser un número con hasta 2 decimales')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('AC-029 — error de red: mensaje de conexión y la operación puede reintentarse', async () => {
    const usuario = userEvent.setup();
    renderizarTransferencia();

    mockFetchErrorRed();
    await completarFormulario(usuario, '100');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(
      await screen.findByText(/No se pudo conectar con el servidor/),
    ).toBeInTheDocument();
    // Los datos se conservan y el reintento (resubmit) funciona.
    mockFetchRespuestas([
      {
        url: '/api/v1/transferencias',
        status: 201,
        cuerpo: {
          idTransferencia: 60,
          monto: 100,
          cbuDestino: '0000003100000000000099',
          fechaHora: '2026-08-10T16:00:00Z',
        },
      },
    ]);
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));
    expect(await screen.findByText('Transferencia realizada')).toBeInTheDocument();
  });

  it('AC-030 — durante la request se deshabilita el botón y no se duplica el envío', async () => {
    const { resolver, fetchMock } = mockFetchDiferido();
    const usuario = userEvent.setup();
    renderizarTransferencia();

    await completarFormulario(usuario, '100');
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));

    expect(screen.getByRole('button', { name: 'Enviando...' })).toBeDisabled();
    // Intento de doble envío bloqueado (AC-030).
    await usuario.click(screen.getByRole('button', { name: 'Enviando...' })).catch(() => undefined);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolver({
      idTransferencia: 70,
      monto: 100,
      cbuDestino: '0000003100000000000099',
      fechaHora: '2026-08-10T17:00:00Z',
    });
    expect(await screen.findByText('Transferencia realizada')).toBeInTheDocument();
  });

  it('AF-003/BR-002 — sin cuentas ACTIVA el formulario queda deshabilitado', async () => {
    const fetchMock = mockFetchRespuestas([{ url: '/api/v1/transferencias', status: 201, cuerpo: {} }]);
    const usuario = userEvent.setup();

    render(<TransferenciaPage cuentasActivas={[]} onTransferenciaExitosa={vi.fn()} />);

    expect(screen.getByText('No hay cuentas disponibles')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Transferir' })).toBeDisabled();
    await usuario.click(screen.getByRole('button', { name: 'Transferir' }));
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
