package com.horacio.ecomarket.usuarios.service;

import com.horacio.ecomarket.usuarios.client.IniciosesionClient;
import com.horacio.ecomarket.usuarios.model.Permiso;
import com.horacio.ecomarket.usuarios.model.PerfilUsuario;
import com.horacio.ecomarket.usuarios.model.Rol;
import com.horacio.ecomarket.usuarios.repository.PerfilUsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistroUsuarioServiceImpl implements RegistroUsuarioService {

    private final PerfilUsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IniciosesionClient iniciosesionClient;

    @Override
    @Transactional
    public PerfilUsuario registrarCuenta(PerfilUsuario perfilUsuario, String contrasenaInicial) {
        repository.findByCorreo(perfilUsuario.getCorreo())
                .ifPresent(u -> {
                    throw new RuntimeException("El correo ya está registrado: " + perfilUsuario.getCorreo());
                });

        String contrasenaHasheada = passwordEncoder.encode(contrasenaInicial);
        perfilUsuario.setPassword(contrasenaHasheada);
        perfilUsuario.setFechaCreacion(LocalDateTime.now());

        // 1. Guardar en usuarios_db
        PerfilUsuario creado = repository.save(perfilUsuario);

        // 2. Sincronizar credencial con iniciosesion-service
        iniciosesionClient.crearCredencial(
                creado.getId(),
                creado.getCorreo(),
                contrasenaHasheada
        );

        return creado;
    }

    @Override
    @Transactional
    public PerfilUsuario modificarDatosUsuario(Long id, PerfilUsuario datosNuevos) {
        PerfilUsuario existente = buscarPorId(id);

        existente.setNombre(datosNuevos.getNombre());
        existente.setTelefono(datosNuevos.getTelefono());

        if (!existente.getCorreo().equals(datosNuevos.getCorreo())) {
            repository.findByCorreo(datosNuevos.getCorreo())
                    .ifPresent(u -> {
                        throw new RuntimeException("El correo ya está en uso: " + datosNuevos.getCorreo());
                    });
            existente.setCorreo(datosNuevos.getCorreo());
        }

        if (datosNuevos.getRol() != null) {
            existente.setRol(datosNuevos.getRol());
        }

        if (datosNuevos.getEstadoPerfil() != null) {
            existente.setEstadoPerfil(datosNuevos.getEstadoPerfil());
        }

        return repository.save(existente);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerfilUsuario> listarUsuarios() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerfilUsuario> listarPorRol(Rol rolUsuario) {
        return repository.findByRol(rolUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilUsuario buscarPorId(Long usuarioId) {
        return repository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + usuarioId));
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilUsuario buscarPorCorreo(String correo) {
        return repository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con correo: " + correo));
    }

    @Override
    @Transactional
    public Boolean configurarPermisos(Long usuarioId, List<Permiso> nuevosPermisos) {
        PerfilUsuario usuario = buscarPorId(usuarioId);
        usuario.getPermisos().clear();
        usuario.getPermisos().addAll(nuevosPermisos);
        repository.save(usuario);
        return true;
    }

    @Override
    @Transactional
    public Boolean eliminarUsuario(Long usuarioId) {
        PerfilUsuario usuario = buscarPorId(usuarioId);
        repository.delete(usuario);
        return true;
    }
}