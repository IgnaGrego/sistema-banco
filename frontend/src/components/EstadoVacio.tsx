/** Estado vacío de una lista con mensaje descriptivo (ERR-009, AF-003). */
export default function EstadoVacio({ mensaje }: { mensaje: string }) {
  return <p className="estado-vacio">{mensaje}</p>;
}
