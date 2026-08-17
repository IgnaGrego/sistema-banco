import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Cargando from './Cargando';
import EstadoError from './EstadoError';
import EstadoVacio from './EstadoVacio';

/**
 * Regresión de los estados rediseñados (SPEC-009 FR-008, BR-006, AC-010):
 * roles ARIA y textos conservados tras el rediseño visual con tokens.
 */
describe('Estados (SPEC-009 FR-008, BR-006, AC-010)', () => {
  it('AC-010 — Cargando conserva role="status" y el texto "Cargando..."', () => {
    render(<Cargando />);
    expect(screen.getByRole('status')).toHaveTextContent('Cargando...');
  });

  it('AC-010 — EstadoError conserva role="alert", el mensaje del envelope y el botón "Reintentar"', async () => {
    const alReintentar = vi.fn();
    const usuario = userEvent.setup();
    render(<EstadoError mensaje="No se pudo conectar con el servidor" onReintentar={alReintentar} />);

    const alerta = screen.getByRole('alert');
    expect(alerta).toHaveTextContent('No se pudo conectar con el servidor');
    const boton = screen.getByRole('button', { name: 'Reintentar' });
    await usuario.click(boton);
    expect(alerta).toBeInTheDocument();
    expect(alReintentar).toHaveBeenCalledTimes(1);
  });

  it('AC-010 — EstadoError sin onReintentar no renderiza el botón', () => {
    render(<EstadoError mensaje="Error interno" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Error interno');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });

  it('AC-010 — EstadoVacio renderiza el mensaje recibido por prop', () => {
    render(<EstadoVacio mensaje="No tiene cuentas en este momento." />);
    expect(screen.getByText('No tiene cuentas en este momento.')).toBeInTheDocument();
  });
});