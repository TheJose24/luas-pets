package com.luaspets.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.luaspets.dto.CitaCalendario;
import com.luaspets.dto.DiaCalendario;
import com.luaspets.dto.FilaHora;
import com.luaspets.dto.SemanaCalendario;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.util.ImagenUtil;

@Service
public class CalendarioService {

    private static final int HORA_INICIO = 8;
    private static final int HORA_FIN = 19;

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] NOMBRES_DIA = { "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };
    private static final String[] MESES_LARGO = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre" };

    /**
     * Construye la rejilla semanal (lunes a domingo, franjas de 08:00 a 19:00)
     * para el conjunto de citas dado, ya filtrado por semana dentro de este
     * metodo. Es reutilizable para admin y doctor: el llamador decide que citas
     * llegan (todas o solo las del doctor logueado) y como se genera la URL de
     * cada tarjeta.
     */
    public SemanaCalendario construirSemana(List<Cita> citas, LocalDate lunesDeLaSemana,
            Function<Cita, String> generadorDeUrl) {
        LocalDate lunes = lunesDeLaSemana.with(DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);

        List<Cita> citasEnSemana = citas.stream()
                .filter(c -> {
                    LocalDate fecha = c.getFechaHora().toLocalDate();
                    return !fecha.isBefore(lunes) && !fecha.isAfter(domingo);
                })
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .toList();

        List<DiaCalendario> dias = construirDias(lunes);
        List<FilaHora> filas = construirFilasVacias();

        for (Cita cita : citasEnSemana) {
            LocalDateTime fechaHora = cita.getFechaHora();
            int horaReal = fechaHora.getHour();

            // Las citas fuera del horario de atencion (antes de las 08:00 o despues
            // de las 19:59) se "aplastan" a la primera o ultima fila visible del
            // calendario en vez de exigir filas adicionales poco utiles para casos
            // excepcionales; se marcan con fueraDeRango para que la vista lo indique
            // con la hora real junto al nombre de la mascota.
            int horaClamped = Math.max(HORA_INICIO, Math.min(HORA_FIN, horaReal));
            boolean fueraDeRango = horaReal < HORA_INICIO || horaReal > HORA_FIN;

            int filaIndex = horaClamped - HORA_INICIO;
            int columnaIndex = fechaHora.getDayOfWeek().getValue() - 1;

            CitaCalendario dto = mapearCita(cita, fueraDeRango, generadorDeUrl);
            filas.get(filaIndex).getColumnas().get(columnaIndex).add(dto);
        }

        return new SemanaCalendario(lunes, domingo, construirTitulo(lunes, domingo),
                lunes.minusWeeks(1).toString(), lunes.plusWeeks(1).toString(), dias, filas, citasEnSemana.size());
    }

    private List<DiaCalendario> construirDias(LocalDate lunes) {
        List<DiaCalendario> dias = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = lunes.plusDays(i);
            dias.add(new DiaCalendario(fecha.toString(), NOMBRES_DIA[i], String.valueOf(fecha.getDayOfMonth()),
                    fecha.equals(hoy)));
        }
        return dias;
    }

    private List<FilaHora> construirFilasVacias() {
        List<FilaHora> filas = new ArrayList<>();
        for (int hora = HORA_INICIO; hora <= HORA_FIN; hora++) {
            List<List<CitaCalendario>> columnas = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                columnas.add(new ArrayList<>());
            }
            filas.add(new FilaHora(String.format("%02d:00", hora), columnas));
        }
        return filas;
    }

    private String construirTitulo(LocalDate lunes, LocalDate domingo) {
        String mesLunes = MESES_LARGO[lunes.getMonthValue() - 1];
        String mesDomingo = MESES_LARGO[domingo.getMonthValue() - 1];

        if (lunes.getMonth() == domingo.getMonth() && lunes.getYear() == domingo.getYear()) {
            return "Semana del " + lunes.getDayOfMonth() + " al " + domingo.getDayOfMonth() + " de " + mesLunes
                    + " de " + lunes.getYear();
        }
        if (lunes.getYear() == domingo.getYear()) {
            return "Semana del " + lunes.getDayOfMonth() + " de " + mesLunes + " al " + domingo.getDayOfMonth()
                    + " de " + mesDomingo + " de " + lunes.getYear();
        }
        return "Semana del " + lunes.getDayOfMonth() + " de " + mesLunes + " de " + lunes.getYear() + " al "
                + domingo.getDayOfMonth() + " de " + mesDomingo + " de " + domingo.getYear();
    }

    private CitaCalendario mapearCita(Cita cita, boolean fueraDeRango, Function<Cita, String> generadorDeUrl) {
        Mascota mascota = cita.getMascota();
        Usuario doctor = cita.getDoctor();
        Usuario cliente = mascota.getCliente();

        String nombre = mascota.getNombre();
        String inicial = (nombre != null && !nombre.isBlank()) ? nombre.substring(0, 1).toUpperCase() : "?";
        String especieRaza = (mascota.getRaza() != null && !mascota.getRaza().isBlank())
                ? mascota.getEspecie() + " · " + mascota.getRaza()
                : mascota.getEspecie();
        String motivo = (cita.getMotivo() != null && !cita.getMotivo().isBlank()) ? cita.getMotivo()
                : "Sin motivo especificado";
        String url = generadorDeUrl != null ? generadorDeUrl.apply(cita) : null;

        return new CitaCalendario(
                cita.getId(),
                cita.getFechaHora().format(FORMATO_HORA),
                nombre,
                inicial,
                resolverFoto(mascota.getEspecie(), mascota.getFotoUrl()),
                especieRaza,
                cliente.getNombre() + " " + cliente.getApellido(),
                "Dr. " + doctor.getNombre() + " " + doctor.getApellido(),
                motivo,
                textoEstado(cita.getEstado()),
                varianteEstado(cita.getEstado()),
                url,
                fueraDeRango);
    }

    private String resolverFoto(String especie, String fotoUrl) {
        return ImagenUtil.resolverFoto(fotoUrl, especie);
    }

    private String textoEstado(EstadoCita estado) {
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case CONFIRMADA -> "Confirmada";
            case ATENDIDA -> "Atendida";
            case CANCELADA -> "Cancelada";
        };
    }

    private String varianteEstado(EstadoCita estado) {
        return switch (estado) {
            case PENDIENTE -> "warning";
            case CONFIRMADA -> "info";
            case ATENDIDA -> "success";
            case CANCELADA -> "secondary";
        };
    }
}
