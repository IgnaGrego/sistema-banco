package com.banco.domain;

import com.banco.domain.model.Cliente;
import com.banco.domain.vo.DNI;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Entidad Cliente: factory crear (id null + fechaAlta dada) y actualizar
 * (cambia los 5 campos sin tocar id/fechaAlta) — AC-023.
 */
class ClienteTest {

    private static final DNI DNI_VALIDO = new DNI("12345678");
    private static final Instant FECHA_ALTA = Instant.parse("2026-08-16T10:00:00Z");

    @Test
    void crearAsignaIdNullYFechaAltaDada() {
        Cliente cliente = Cliente.crear("Juan", "Perez", DNI_VALIDO,
                "juan@example.com", "+549112345678", FECHA_ALTA);

        assertNull(cliente.getId());
        assertEquals(FECHA_ALTA, cliente.getFechaAlta());
        assertEquals("Juan", cliente.getNombre());
        assertEquals("Perez", cliente.getApellido());
        assertEquals(DNI_VALIDO, cliente.getDni());
        assertEquals("juan@example.com", cliente.getEmail());
        assertEquals("+549112345678", cliente.getTelefono());
    }

    @Test
    void actualizarCambiaLosCincoCampos() {
        Cliente cliente = Cliente.crear("Juan", "Perez", DNI_VALIDO,
                "juan@example.com", null, FECHA_ALTA);

        DNI dniNuevo = new DNI("87654321");
        cliente.actualizar("Maria", "Gomez", dniNuevo, "maria@example.com", "114567890");

        assertEquals("Maria", cliente.getNombre());
        assertEquals("Gomez", cliente.getApellido());
        assertEquals(dniNuevo, cliente.getDni());
        assertEquals("maria@example.com", cliente.getEmail());
        assertEquals("114567890", cliente.getTelefono());
    }

    @Test
    void actualizarNoTocaIdNiFechaAlta() {
        Cliente cliente = new Cliente(42L, "Juan", "Perez", DNI_VALIDO,
                "juan@example.com", null, FECHA_ALTA);

        cliente.actualizar("Maria", "Gomez", new DNI("87654321"), "maria@example.com", null);

        assertEquals(42L, cliente.getId());
        assertEquals(FECHA_ALTA, cliente.getFechaAlta());
    }
}
