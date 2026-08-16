import { formatearMontoARS } from '../lib/session';

/** Display de montos con `formatearMontoARS` (BR-010, AC-014). */
export default function Monto({ monto }: { monto: number }) {
  return <span className="monto">{formatearMontoARS(monto)}</span>;
}
