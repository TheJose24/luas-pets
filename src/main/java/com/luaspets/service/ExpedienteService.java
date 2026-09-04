package com.luaspets.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.luaspets.dto.ConsultaExpediente;
import com.luaspets.dto.ExpedienteMascota;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.EstadoMascota;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.model.Sexo;
import com.luaspets.model.Usuario;
import com.luaspets.util.ImagenUtil;

/**
 * Construye el expediente clinico de una mascota (cabecera, tarjetas de
 * resumen y lista de consultas) con todo ya calculado y formateado, para que
 * las plantillas de la ficha del doctor y del perfil del cliente no
 * necesiten hacer ningun calculo.
 */
@Service
public class ExpedienteService {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String[] MESES_ABREV_MIN = { "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep",
            "oct", "nov", "dic" };
    private static final String[] MESES_LARGO = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre" };

    public ExpedienteMascota construir(Mascota mascota, List<Cita> citas, List<HistorialMedico> historiales) {
        EstadoMascota estado = mascota.getEstado() != null ? mascota.getEstado() : EstadoMascota.ACTIVO;
        Usuario cliente = mascota.getCliente();

        HistorialMedico masReciente = historiales.isEmpty() ? null : historiales.get(0);

        String ultimaVisitaTexto = masReciente != null ? formatearFechaCorta(masReciente.getFecha().toLocalDate())
                : "Sin visitas";
        String ultimaVisitaMotivo = (masReciente != null && masReciente.getCita() != null
                && tieneTexto(masReciente.getCita().getMotivo())) ? masReciente.getCita().getMotivo() : "—";

        String pesoActualTexto = formatearPeso(mascota.getPeso());

        String[] variacion = calcularVariacionPeso(historiales);

        String alergias = mascota.getAlergias();
        boolean tieneAlergias = tieneTexto(alergias);
        String alergiasTexto = tieneAlergias ? alergias : "Ninguna registrada";

        String ultimosMedicamentos = (masReciente != null && tieneTexto(masReciente.getMedicamentos()))
                ? masReciente.getMedicamentos()
                : "Sin recetas registradas";

        LocalDateTime ahora = LocalDateTime.now();
        Cita proxima = citas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isAfter(ahora))
                .min(Comparator.comparing(Cita::getFechaHora))
                .orElse(null);

        String proximaCitaTexto = proxima != null
                ? formatearFechaCorta(proxima.getFechaHora().toLocalDate()) + " · " + proxima.getFechaHora().format(FORMATO_HORA)
                : "Sin programar";
        String proximaCitaMotivo = (proxima != null && tieneTexto(proxima.getMotivo())) ? proxima.getMotivo() : "—";
        Long proximaCitaId = proxima != null ? proxima.getId() : null;

        String vetNombre = null;
        String vetEmail = null;
        String vetInicial = null;
        if (masReciente != null && masReciente.getCita() != null && masReciente.getCita().getDoctor() != null) {
            Usuario doctor = masReciente.getCita().getDoctor();
            vetNombre = "Dr. " + doctor.getNombre() + " " + doctor.getApellido();
            vetEmail = doctor.getEmail();
            vetInicial = tieneTexto(doctor.getNombre()) ? doctor.getNombre().substring(0, 1).toUpperCase() : "?";
        }

        return ExpedienteMascota.builder()
                .id(mascota.getId())
                .codigo(String.format("#LP-%04d", mascota.getId()))
                .nombre(mascota.getNombre())
                .fotoUrl(resolverFoto(mascota.getEspecie(), mascota.getFotoUrl()))
                .especie(mascota.getEspecie())
                .raza(mascota.getRaza())
                .sexoTexto(textoSexo(mascota.getSexo()))
                .edadTexto(calcularEdadTexto(mascota.getFechaNacimiento()))
                .fechaNacimientoTexto(
                        mascota.getFechaNacimiento() != null ? formatearFechaCorta(mascota.getFechaNacimiento()) : "—")
                .pesoTexto(pesoActualTexto)
                .estadoTexto(textoEstado(estado))
                .estadoVariante(varianteEstado(estado))
                .propietarioNombre(cliente.getNombre() + " " + cliente.getApellido())
                .propietarioEmail(cliente.getEmail())
                .propietarioTelefono(tieneTexto(cliente.getTelefono()) ? cliente.getTelefono() : "—")
                .ultimaVisitaTexto(ultimaVisitaTexto)
                .ultimaVisitaMotivo(ultimaVisitaMotivo)
                .pesoActualTexto(pesoActualTexto)
                .variacionPesoTexto(variacion[0])
                .variacionPesoVariante(variacion[1])
                .variacionPesoIcono(variacion[2])
                .alergiasTexto(alergiasTexto)
                .tieneAlergias(tieneAlergias)
                .ultimosMedicamentos(ultimosMedicamentos)
                .proximaCitaTexto(proximaCitaTexto)
                .proximaCitaMotivo(proximaCitaMotivo)
                .proximaCitaId(proximaCitaId)
                .vetNombre(vetNombre)
                .vetEmail(vetEmail)
                .vetInicial(vetInicial)
                .totalConsultas(historiales.size())
                .build();
    }

    public List<ConsultaExpediente> construirConsultas(List<HistorialMedico> historiales) {
        List<ConsultaExpediente> resultado = new ArrayList<>();
        for (int i = 0; i < historiales.size(); i++) {
            HistorialMedico h = historiales.get(i);
            Cita cita = h.getCita();
            String motivo = (cita != null && tieneTexto(cita.getMotivo())) ? cita.getMotivo() : "Consulta general";
            Usuario doctor = cita != null ? cita.getDoctor() : null;
            String doctorNombre = doctor != null ? "Dr. " + doctor.getNombre() + " " + doctor.getApellido() : "—";
            LocalDate fecha = h.getFecha().toLocalDate();

            resultado.add(new ConsultaExpediente(
                    h.getId(),
                    motivo,
                    MESES_ABREV_MIN[fecha.getMonthValue() - 1].toUpperCase(Locale.ROOT),
                    formatearFechaLarga(fecha),
                    doctorNombre,
                    h.getPesoRegistrado() != null ? "Peso: " + formatearPeso(h.getPesoRegistrado()) : null,
                    h.getDiagnostico(),
                    h.getTratamiento(),
                    h.getObservaciones(),
                    dividirLineas(h.getMedicamentos()),
                    i == 0));
        }
        return resultado;
    }

    private String[] calcularVariacionPeso(List<HistorialMedico> historiales) {
        List<BigDecimal> pesos = historiales.stream()
                .map(HistorialMedico::getPesoRegistrado)
                .filter(p -> p != null)
                .limit(2)
                .toList();

        if (pesos.size() < 2) {
            return new String[] { "Sin comparación", "muted", "bi-dash" };
        }

        BigDecimal diferencia = pesos.get(0).subtract(pesos.get(1));
        int comparacion = diferencia.compareTo(BigDecimal.ZERO);
        if (comparacion == 0) {
            return new String[] { "Estable", "success", "bi-dash" };
        }

        String magnitud = String.format(Locale.US, "%.2f kg", diferencia.abs());
        return comparacion > 0
                ? new String[] { "+" + magnitud, "success", "bi-arrow-up" }
                : new String[] { "−" + magnitud, "warning", "bi-arrow-down" };
    }

    private List<String> dividirLineas(String texto) {
        if (!tieneTexto(texto)) {
            return List.of();
        }
        return Arrays.stream(texto.split("\\r?\\n"))
                .map(String::trim)
                .filter(linea -> !linea.isBlank())
                .toList();
    }

    private String resolverFoto(String especie, String fotoUrl) {
        return ImagenUtil.resolverFoto(fotoUrl, especie);
    }

    private String textoSexo(Sexo sexo) {
        if (sexo == null) {
            return "Sin especificar";
        }
        return sexo == Sexo.MACHO ? "Macho" : "Hembra";
    }

    private String calcularEdadTexto(LocalDate fechaNacimiento) {
        if (fechaNacimiento == null) {
            return "—";
        }
        Period periodo = Period.between(fechaNacimiento, LocalDate.now());
        if (periodo.getYears() == 0) {
            if (periodo.getMonths() == 0) {
                int dias = periodo.getDays();
                return dias + (dias == 1 ? " día" : " días");
            }
            return periodo.getMonths() + (periodo.getMonths() == 1 ? " mes" : " meses");
        }
        if (periodo.getYears() == 1) {
            return "1 año";
        }
        return periodo.getYears() + " años";
    }

    private String formatearFechaCorta(LocalDate fecha) {
        if (fecha == null) {
            return "—";
        }
        return fecha.getDayOfMonth() + " " + MESES_ABREV_MIN[fecha.getMonthValue() - 1] + " " + fecha.getYear();
    }

    private String formatearFechaLarga(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES_LARGO[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    private String formatearPeso(BigDecimal peso) {
        if (peso == null) {
            return "—";
        }
        return String.format(Locale.US, "%.2f kg", peso);
    }

    private String textoEstado(EstadoMascota estado) {
        return switch (estado) {
            case ACTIVO -> "Activo";
            case EN_TRATAMIENTO -> "En tratamiento";
            case INACTIVO -> "Inactivo";
        };
    }

    private String varianteEstado(EstadoMascota estado) {
        return switch (estado) {
            case ACTIVO -> "success";
            case EN_TRATAMIENTO -> "warning";
            case INACTIVO -> "secondary";
        };
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
