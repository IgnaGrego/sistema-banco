/**
 * Estado de error de una vista de datos: muestra el `message` del envelope
 * (o el mensaje de error de red) y un botón `Reintentar` (ERR-003/005/006/
 * 007/008, AC-028/029).
 */
interface EstadoErrorProps {
  mensaje: string;
  onReintentar?: () => void;
}

export default function EstadoError({ mensaje, onReintentar }: EstadoErrorProps) {
  return (
    <div role="alert" className="estado-error">
      <p>{mensaje}</p>
      {onReintentar !== undefined && (
        <button type="button" onClick={onReintentar}>
          Reintentar
        </button>
      )}
    </div>
  );
}
