import type { ReactNode } from 'react';

/**
 * Campo de formulario: label + input (children) + mensaje de error por campo
 * (usa `DetalleError.campo` de los details del envelope — ERR-004/006,
 * AC-019/020/027). El input recibe el `id` para la asociación label↔input.
 */
interface CampoFormularioProps {
  id: string;
  label: string;
  error?: string;
  children: ReactNode;
}

export default function CampoFormulario({ id, label, error, children }: CampoFormularioProps) {
  return (
    <div className="campo">
      <label htmlFor={id}>{label}</label>
      {children}
      {error !== undefined && (
        <span className="error-campo" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
