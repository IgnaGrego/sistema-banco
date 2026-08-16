package com.banco.domain;

import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Entidad Usuario: factory de alta (id null, lo asigna la BD) y constructor de
 * reconstrucción (adapter). Refuerza AC-007 (la entidad guarda el hash, nunca
 * la password en claro — el hash llega ya calculado por PasswordHasher).
 */
class UsuarioTest {

    @Test
    void crearAsignaIdNullYConservaUsernameHashRolYClienteId() {
        Usuario usuario = Usuario.crear("juan", "hash-bcrypt", Rol.CLIENTE, 7L);

        assertNull(usuario.getId()); // la BD asigna el id
        assertEquals("juan", usuario.getUsername());
        assertEquals("hash-bcrypt", usuario.getPasswordHash());
        assertEquals(Rol.CLIENTE, usuario.getRol());
        assertEquals(7L, usuario.getClienteId());
    }

    @Test
    void crearParaADMINTieneClienteIdNull() {
        Usuario usuario = Usuario.crear("admin", "hash-bcrypt", Rol.ADMIN, null);

        assertEquals(Rol.ADMIN, usuario.getRol());
        assertNull(usuario.getClienteId());
    }

    @Test
    void constructorDeReconstruccionConservaLosDatos() {
        Usuario usuario = new Usuario(3L, "admin", "hash-bcrypt", Rol.ADMIN, null);

        assertEquals(3L, usuario.getId());
        assertEquals("admin", usuario.getUsername());
        assertEquals("hash-bcrypt", usuario.getPasswordHash());
        assertEquals(Rol.ADMIN, usuario.getRol());
        assertNull(usuario.getClienteId());
    }

    @Test
    void gettersDevuelvenLosValoresInmutables() {
        Usuario usuario = new Usuario(9L, "cliente9", "hash-bcrypt", Rol.CLIENTE, 12L);

        assertEquals(9L, usuario.getId());
        assertEquals("cliente9", usuario.getUsername());
        assertEquals("hash-bcrypt", usuario.getPasswordHash());
        assertEquals(Rol.CLIENTE, usuario.getRol());
        assertEquals(12L, usuario.getClienteId());
    }
}
