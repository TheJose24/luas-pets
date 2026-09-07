package com.luaspets.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import com.luaspets.model.Notificacion;
import com.luaspets.model.TipoNotificacion;

@Getter
@Setter
@AllArgsConstructor
public class NotificacionResponse {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Long id;
    private String tipo;
    private String icono;
    private String variante;
    private String titulo;
    private String mensaje;
    private String url;
    private boolean leida;
    private String tiempoRelativo;

    public static NotificacionResponse from(Notificacion notificacion) {
        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTipo().name(),
                iconoPorTipo(notificacion.getTipo()),
                variantePorTipo(notificacion.getTipo()),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getUrl(),
                Boolean.TRUE.equals(notificacion.getLeida()),
                tiempoRelativo(notificacion.getFechaCreacion()));
    }

    private static String iconoPorTipo(TipoNotificacion tipo) {
        return switch (tipo) {
            case CITA_AGENDADA -> "bi-calendar-plus";
            case CITA_CONFIRMADA -> "bi-calendar-check";
            case CITA_REPROGRAMADA -> "bi-arrow-repeat";
            case CITA_CANCELADA -> "bi-calendar-x";
            case CITA_ATENDIDA -> "bi-clipboard-check";
            case PEDIDO_NUEVO -> "bi-bag-check";
            case PEDIDO_ESTADO -> "bi-bag";
            case CLIENTE_NUEVO -> "bi-person-plus";
            case STOCK_BAJO -> "bi-exclamation-triangle";
            case CITA_RECORDATORIO -> "bi-alarm";
        };
    }

    private static String variantePorTipo(TipoNotificacion tipo) {
        return switch (tipo) {
            case CITA_AGENDADA -> "primary";
            case CITA_CONFIRMADA -> "info";
            case CITA_REPROGRAMADA -> "warning";
            case CITA_CANCELADA -> "danger";
            case CITA_ATENDIDA -> "primary";
            case PEDIDO_NUEVO -> "warning";
            case PEDIDO_ESTADO -> "info";
            case CLIENTE_NUEVO -> "info";
            case STOCK_BAJO -> "danger";
            case CITA_RECORDATORIO -> "warning";
        };
    }

    private static String tiempoRelativo(LocalDateTime fechaCreacion) {
        LocalDateTime ahora = LocalDateTime.now();
        Duration duracion = Duration.between(fechaCreacion, ahora);
        if (duracion.isNegative()) {
            duracion = Duration.ZERO;
        }

        long minutos = duracion.toMinutes();
        if (minutos < 1) {
            return "Hace un momento";
        }
        if (minutos < 60) {
            return "Hace " + minutos + (minutos == 1 ? " minuto" : " minutos");
        }

        long horas = duracion.toHours();
        if (horas < 24) {
            return "Hace " + horas + (horas == 1 ? " hora" : " horas");
        }

        long dias = duracion.toDays();
        if (dias == 1) {
            return "Ayer";
        }
        if (dias <= 7) {
            return "Hace " + dias + " días";
        }

        return fechaCreacion.format(FORMATO_FECHA);
    }
}
