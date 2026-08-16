import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { actualizarCliente, crearCliente } from '../../api/clientes';
import type { ApiError } from '../../api/httpClient';
import type { ClienteDto, CrearClienteRequest } from '../../api/types';
import CampoFormulario from '../../components/CampoFormulario';
import Cargando from '../../components/Cargando';
import EstadoError from '../../components/EstadoError';
import EstadoVacio from '../../components/EstadoVacio';
import { useClientes } from '../../hooks/useClientes';
import { formatearFecha } from '../../lib/session';
import { validarCliente } from '../../lib/validacion';
import type { ErroresPorCampo } from '../../lib/validacion';

interface FormularioCliente {
  nombre: string;
  apellido: string;
  dni: string;
  email: string;
  telefono: string;
}

const FORMULARIO_VACIO: FormularioCliente = {
  nombre: '',
  apellido: '',
  dni: '',
  email: '',
  telefono: '',
};

/**
 * Sección de gestión de clientes del ADMIN (FR-012): listado
 * (GET /api/v1/clientes, AC-022), alta (POST → 201, se agrega a la lista,
 * AC-023) y edición (PUT /{id} → 200, forma pre-cargada, AC-024).
 * Validación BR-006; 400 por campo (AC-027); 409 CONFLICTO_UNICIDAD
 * (ERR-007). Doble envío protegido (AC-030).
 */
export default function ClientesSection() {
  const { datos, cargando, error, recargar } = useClientes();
  const [lista, setLista] = useState<ClienteDto[] | null>(null);
  const [formulario, setFormulario] = useState<FormularioCliente>(FORMULARIO_VACIO);
  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [erroresCampo, setErroresCampo] = useState<ErroresPorCampo>({});
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // Sincroniza la lista local con el hook (permite agregar/reemplazar sin
  // re-fetch — AC-023/AC-024) y refleja las recargas (Reintentar).
  useEffect(() => {
    setLista(datos);
  }, [datos]);

  const setCampo = (campo: keyof FormularioCliente, valor: string) => {
    setFormulario((actual) => ({ ...actual, [campo]: valor }));
  };

  const iniciarEdicion = (cliente: ClienteDto) => {
    setEditandoId(cliente.id);
    setFormulario({
      nombre: cliente.nombre,
      apellido: cliente.apellido,
      dni: cliente.dni,
      email: cliente.email,
      telefono: cliente.telefono ?? '',
    });
    setErroresCampo({});
    setErrorGeneral(null);
  };

  const cancelarEdicion = () => {
    setEditandoId(null);
    setFormulario(FORMULARIO_VACIO);
    setErroresCampo({});
    setErrorGeneral(null);
  };

  const aplicarError = (err: ApiError) => {
    if (err.details !== undefined && err.details.length > 0) {
      const porCampo: ErroresPorCampo = {};
      for (const detalle of err.details) {
        porCampo[detalle.campo] = detalle.mensaje;
      }
      setErroresCampo(porCampo);
      setErrorGeneral(null);
    } else {
      setErroresCampo({});
      setErrorGeneral(err.message);
    }
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const erroresValidacion = validarCliente(
      formulario.nombre,
      formulario.apellido,
      formulario.dni,
      formulario.email,
      formulario.telefono,
    );
    setErroresCampo(erroresValidacion);
    if (Object.keys(erroresValidacion).length > 0) {
      return;
    }
    setEnviando(true);
    setErrorGeneral(null);
    try {
      const payload: CrearClienteRequest = {
        nombre: formulario.nombre.trim(),
        apellido: formulario.apellido.trim(),
        dni: formulario.dni.trim(),
        email: formulario.email.trim(),
        telefono: formulario.telefono.trim() !== '' ? formulario.telefono.trim() : null,
      };
      if (editandoId === null) {
        const creado = await crearCliente(payload);
        setLista((actual) => (actual === null ? [creado] : [...actual, creado]));
      } else {
        const actualizado = await actualizarCliente(editandoId, payload);
        setLista((actual) =>
          actual === null ? [actualizado] : actual.map((c) => (c.id === editandoId ? actualizado : c)),
        );
      }
      cancelarEdicion();
    } catch (e) {
      aplicarError(e as ApiError);
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="seccion">
      <h2>Clientes</h2>

      {cargando && lista === null && <Cargando />}
      {error !== null && <EstadoError mensaje={error.message} onReintentar={recargar} />}

      {!cargando && error === null && lista !== null && lista.length === 0 && (
        <EstadoVacio mensaje="No hay clientes registrados." />
      )}

      {lista !== null && lista.length > 0 && (
        <ul className="lista-clientes">
          {lista.map((cliente) => (
            <li key={cliente.id} className="cliente">
              <span className="cliente-nombre">
                {cliente.apellido}, {cliente.nombre}
              </span>
              <span className="cliente-dni">DNI: {cliente.dni}</span>
              <span className="cliente-email">{cliente.email}</span>
              <span className="cliente-fecha">Alta: {formatearFecha(cliente.fechaAlta)}</span>
              <button type="button" onClick={() => iniciarEdicion(cliente)}>
                Editar
              </button>
            </li>
          ))}
        </ul>
      )}

      <h3>{editandoId === null ? 'Nuevo cliente' : `Editar cliente (ID ${editandoId})`}</h3>
      {errorGeneral !== null && (
        <div role="alert" className="error-general">
          {errorGeneral}
        </div>
      )}
      <form onSubmit={onSubmit} noValidate>
        <CampoFormulario id="cliente-nombre" label="Nombre" error={erroresCampo.nombre}>
          <input
            id="cliente-nombre"
            name="nombre"
            value={formulario.nombre}
            onChange={(e) => setCampo('nombre', e.target.value)}
          />
        </CampoFormulario>
        <CampoFormulario id="cliente-apellido" label="Apellido" error={erroresCampo.apellido}>
          <input
            id="cliente-apellido"
            name="apellido"
            value={formulario.apellido}
            onChange={(e) => setCampo('apellido', e.target.value)}
          />
        </CampoFormulario>
        <CampoFormulario id="cliente-dni" label="DNI" error={erroresCampo.dni}>
          <input
            id="cliente-dni"
            name="dni"
            value={formulario.dni}
            onChange={(e) => setCampo('dni', e.target.value)}
            inputMode="numeric"
            maxLength={8}
          />
        </CampoFormulario>
        <CampoFormulario id="cliente-email" label="Email" error={erroresCampo.email}>
          <input
            id="cliente-email"
            name="email"
            type="email"
            value={formulario.email}
            onChange={(e) => setCampo('email', e.target.value)}
            maxLength={254}
          />
        </CampoFormulario>
        <CampoFormulario id="cliente-telefono" label="Teléfono" error={erroresCampo.telefono}>
          <input
            id="cliente-telefono"
            name="telefono"
            value={formulario.telefono}
            onChange={(e) => setCampo('telefono', e.target.value)}
            inputMode="tel"
            maxLength={16}
          />
        </CampoFormulario>
        <div className="acciones-formulario">
          <button type="submit" disabled={enviando}>
            {enviando ? 'Guardando...' : editandoId === null ? 'Crear cliente' : 'Guardar cambios'}
          </button>
          {editandoId !== null && (
            <button type="button" onClick={cancelarEdicion}>
              Cancelar
            </button>
          )}
        </div>
      </form>
    </div>
  );
}
