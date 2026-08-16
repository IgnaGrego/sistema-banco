import ClientesSection from './gestion/ClientesSection';
import CuentasSection from './gestion/CuentasSection';

/**
 * Vista /gestion del ADMIN (FR-012/FR-013): dos secciones — gestión de
 * clientes (listar/crear/editar) y gestión de cuentas (listar por
 * cliente/abrir). Los errores de la API se muestran en la sección
 * correspondiente con el mensaje del envelope (ERR-003/005/006/007, AC-028).
 */
export default function GestionPage() {
  return (
    <section>
      <h1>Gestión</h1>
      <ClientesSection />
      <CuentasSection />
    </section>
  );
}
