package com.banco.application.usecase;

import com.banco.application.command.RegistrarUsuarioCommand;
import com.banco.application.validator.DatosRegistro;
import com.banco.application.validator.RegistroValidator;
import com.banco.domain.exception.ClienteNoEncontradoException;
import com.banco.domain.exception.UsernameDuplicadoException;
import com.banco.domain.model.Rol;
import com.banco.domain.model.Usuario;
import com.banco.domain.port.ClienteRepository;
import com.banco.domain.port.PasswordHasher;
import com.banco.domain.port.UsuarioRepository;

/**
 * Registro de usuario (FR-001, AF-001): normaliza (trim de username vía
 * {@link DatosRegistro}), valida (RegistroValidator → 400), verifica unicidad
 * (→ 409), para CLIENTE verifica que el clienteId referencie un Cliente
 * existente (→ 404), hashea la password con BCrypt (BR-001) y persiste.
 *
 * Java puro (sin Spring): las dependencias son puertos de dominio y
 * validadores; los beans los declara AuthBeansConfig (infrastructure).
 */
public class RegistrarUsuarioUseCase {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordHasher passwordHasher;
    private final RegistroValidator validator;

    public RegistrarUsuarioUseCase(UsuarioRepository usuarioRepository,
                                   ClienteRepository clienteRepository,
                                   PasswordHasher passwordHasher,
                                   RegistroValidator validator) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.passwordHasher = passwordHasher;
        this.validator = validator;
    }

    public Usuario ejecutar(RegistrarUsuarioCommand command) {
        DatosRegistro datos = new DatosRegistro(
                command.username(), command.password(), command.rol(), command.clienteId());

        validator.validar(datos);

        if (usuarioRepository.existsByUsername(datos.username())) {
            throw new UsernameDuplicadoException();
        }

        Rol rol = Rol.valueOf(datos.rol()); // garantizado válido por el validador
        Long clienteId = null;
        if (rol == Rol.CLIENTE) {
            clienteId = datos.clienteId(); // no null: lo garantizó el validador (ERR-007)
            if (clienteRepository.findById(clienteId).isEmpty()) {
                throw new ClienteNoEncontradoException();
            }
        }
        // Si rol == ADMIN, un clienteId informado se IGNORA: el vínculo solo se
        // define para CLIENTE (FR-005, A-003); el Usuario se persiste con null.

        String hash = passwordHasher.hash(datos.password());
        Usuario usuario = Usuario.crear(datos.username(), hash, rol, clienteId);
        return usuarioRepository.save(usuario);
    }
}
