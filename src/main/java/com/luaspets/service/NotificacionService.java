package com.luaspets.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.Notificacion;
import com.luaspets.model.Rol;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.NotificacionRepository;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository notificacionRepository;
    private final UsuarioService usuarioService;

    // UsuarioService se inyecta con @Lazy porque UsuarioService.registrarCliente
    // notifica a los admins a traves de este servicio: sin @Lazy, la inyeccion
    // por constructor en ambos sentidos forma un ciclo que Spring no puede
    // resolver al arrancar. @Lazy rompe el ciclo entregando un proxy que solo
    // resuelve el bean real la primera vez que se usa.
    public NotificacionService(NotificacionRepository notificacionRepository,
            @Lazy UsuarioService usuarioService) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioService = usuarioService;
    }

    @Transactional
    public void crear(Usuario destinatario, TipoNotificacion tipo, String titulo, String mensaje, String url) {
        if (destinatario == null) {
            log.warn("No se pudo crear la notificacion de tipo {} porque el destinatario es null", tipo);
            return;
        }
        Notificacion notificacion = Notificacion.builder()
                .destinatario(destinatario)
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .url(url)
                .leida(false)
                .fechaCreacion(LocalDateTime.now())
                .build();
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void notificarAdmins(TipoNotificacion tipo, String titulo, String mensaje, String url) {
        List<Usuario> admins = usuarioService.listarPorRol(Rol.ADMIN);
        for (Usuario admin : admins) {
            crear(admin, tipo, titulo, mensaje, url);
        }
    }

    public long contarNoLeidas(Long usuarioId) {
        return notificacionRepository.countByDestinatarioIdAndLeidaFalse(usuarioId);
    }

    public List<Notificacion> listarRecientes(Long usuarioId) {
        return notificacionRepository.findTop10ByDestinatarioIdOrderByFechaCreacionDesc(usuarioId);
    }

    public List<Notificacion> listarTodas(Long usuarioId) {
        return notificacionRepository.findByDestinatarioIdOrderByFechaCreacionDesc(usuarioId);
    }

    @Transactional
    public void marcarComoLeida(Long notificacionId, Long usuarioId) {
        Notificacion notificacion = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notificacion no encontrada con id: " + notificacionId));
        if (!notificacion.getDestinatario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("La notificación no pertenece al usuario logueado");
        }
        notificacion.setLeida(true);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarTodasComoLeidas(Long usuarioId) {
        notificacionRepository.marcarTodasComoLeidas(usuarioId);
    }
}
