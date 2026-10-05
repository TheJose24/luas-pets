package com.luaspets.service;

import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.exception.BusinessException;
import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.Rol;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    @PersistenceContext
    private EntityManager entityManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificacionService notificacionService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            NotificacionService notificacionService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public Usuario registrarCliente(Usuario usuario) {
        if (usuarioRepository.existsByEmail(usuario.getEmail())) {
            throw new BusinessException("El email ya está registrado");
        }
        usuario.setId(null);
        usuario.setTwoFactorEnabled(false);
        usuario.setTwoFactorSecret(null);
        usuario.setTwoFactorLastCounter(null);
        usuario.setSecurityVersion(0);
        usuario.setRecoveryCodeHashes(new ArrayList<>());
        usuario.setRol(Rol.CLIENTE);
        usuario.setActivo(true);
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        Usuario guardado = usuarioRepository.save(usuario);

        try {
            notificacionService.notificarAdmins(TipoNotificacion.CLIENTE_NUEVO, "Nuevo cliente registrado",
                    guardado.getNombre() + " " + guardado.getApellido() + " creó una cuenta.",
                    "/admin/mascotas");
        } catch (Exception e) {
            log.error("No se pudo notificar a los admins sobre el nuevo cliente {}", guardado.getId(), e);
        }

        return guardado;
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
    }

    public List<Usuario> listarDoctoresActivos() {
        return usuarioRepository.findByRolAndActivoTrue(Rol.DOCTOR);
    }

    public List<Usuario> listarPorRol(Rol rol) {
        return usuarioRepository.findByRol(rol);
    }

    @Transactional
    public Usuario crearDoctor(Usuario doctor) {
        if (usuarioRepository.existsByEmail(doctor.getEmail())) {
            throw new BusinessException("El email ya está registrado");
        }
        doctor.setId(null);
        doctor.setTwoFactorEnabled(false);
        doctor.setTwoFactorSecret(null);
        doctor.setTwoFactorLastCounter(null);
        doctor.setSecurityVersion(0);
        doctor.setRecoveryCodeHashes(new ArrayList<>());
        doctor.setRol(Rol.DOCTOR);
        doctor.setActivo(true);
        doctor.setFechaRegistro(LocalDateTime.now());
        doctor.setPassword(passwordEncoder.encode(doctor.getPassword()));
        return usuarioRepository.save(doctor);
    }

    @Transactional
    public void desactivarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void activarUsuario(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizarPerfil(Long usuarioId, String nombre, String apellido, String telefono) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId));

        if (nombre == null || nombre.isBlank() || apellido == null || apellido.isBlank()) {
            throw new BusinessException("El nombre y el apellido son obligatorios");
        }

        // Solo estos tres campos: email, password, rol, activo y fechaRegistro
        // nunca se tocan desde el perfil del propio usuario.
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setTelefono(telefono);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva,
            String passwordConfirmacion) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId));

        entityManager.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new BusinessException("La contraseña actual no es correcta");
        }
        if (passwordNueva == null || passwordNueva.length() < 8) {
            throw new BusinessException("La nueva contraseña debe tener al menos 8 caracteres");
        }
        if (!passwordNueva.equals(passwordConfirmacion)) {
            throw new BusinessException("Las contraseñas nuevas no coinciden");
        }
        if (passwordNueva.equals(passwordActual)) {
            throw new BusinessException("La nueva contraseña debe ser diferente a la actual");
        }

        usuario.setSecurityVersion(usuario.getSecurityVersion() + 1);
        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        // La entidad bloqueada ya esta gestionada; Hibernate persiste ambos cambios
        // al cerrar la transaccion sin volver a fusionar sus colecciones.
        return usuario;
    }
}
