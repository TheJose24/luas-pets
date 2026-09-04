package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.luaspets.dto.FilaHora;
import com.luaspets.dto.SemanaCalendario;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.Mascota;
import com.luaspets.model.Usuario;
import com.luaspets.service.CalendarioService;

/**
 * Prueba unitaria pura (sin contexto Spring) de la construccion de la rejilla
 * semanal: verifica que una cita se ubique en la fila y columna correctas.
 */
class CalendarioServiceTest {

    @Test
    void unaCitaDeMartesA1030SeUbicaEnLaFilaDeLas10YColumnaDeIndice1() {
        CalendarioService service = new CalendarioService();

        LocalDate lunes = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate martes = lunes.plusDays(1);

        Usuario cliente = new Usuario();
        cliente.setNombre("Ana");
        cliente.setApellido("Torres");

        Mascota mascota = new Mascota();
        mascota.setNombre("Rocky");
        mascota.setEspecie("Perro");
        mascota.setRaza("Labrador");
        mascota.setCliente(cliente);

        Usuario doctor = new Usuario();
        doctor.setNombre("Carlos");
        doctor.setApellido("Mendoza");

        Cita cita = new Cita();
        cita.setId(1L);
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(LocalDateTime.of(martes, LocalTime.of(10, 30)));
        cita.setMotivo("Control");
        cita.setEstado(EstadoCita.PENDIENTE);

        SemanaCalendario semana = service.construirSemana(List.of(cita), lunes, c -> "/x/" + c.getId());

        // Filas van de 08:00 (indice 0) a 19:00 (indice 11); 10:00 es el indice 2.
        FilaHora filaDiez = semana.getFilas().get(2);
        assertThat(filaDiez.getHoraTexto()).isEqualTo("10:00");

        List<?> columnaMartes = filaDiez.getColumnas().get(1);
        assertThat(columnaMartes).hasSize(1);

        var citaColocada = filaDiez.getColumnas().get(1).get(0);
        assertThat(citaColocada.getMascotaNombre()).isEqualTo("Rocky");
        assertThat(citaColocada.isFueraDeRango()).isFalse();
        assertThat(citaColocada.getHoraTexto()).isEqualTo("10:30");

        long totalCitasEnRejilla = semana.getFilas().stream()
                .flatMap(f -> f.getColumnas().stream())
                .flatMap(List::stream)
                .count();
        assertThat(totalCitasEnRejilla).isEqualTo(1);
        assertThat(semana.getTotalCitasSemana()).isEqualTo(1);
    }

    @Test
    void unaCitaDeLunesA0700QuedaFueraDeRangoYSeUbicaEnLaPrimeraFila() {
        CalendarioService service = new CalendarioService();

        LocalDate lunes = LocalDate.now().with(DayOfWeek.MONDAY);

        Usuario cliente = new Usuario();
        cliente.setNombre("Ana");
        cliente.setApellido("Torres");

        Mascota mascota = new Mascota();
        mascota.setNombre("Rocky");
        mascota.setEspecie("Perro");
        mascota.setRaza("Labrador");
        mascota.setCliente(cliente);

        Usuario doctor = new Usuario();
        doctor.setNombre("Carlos");
        doctor.setApellido("Mendoza");

        Cita cita = new Cita();
        cita.setId(2L);
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(LocalDateTime.of(lunes, LocalTime.of(7, 0)));
        cita.setMotivo("Urgencia temprana");
        cita.setEstado(EstadoCita.PENDIENTE);

        SemanaCalendario semana = service.construirSemana(List.of(cita), lunes, c -> "/x/" + c.getId());

        // Las citas antes de las 08:00 se "aplastan" a la primera fila visible (indice 0).
        FilaHora primeraFila = semana.getFilas().get(0);
        assertThat(primeraFila.getHoraTexto()).isEqualTo("08:00");

        List<?> columnaLunes = primeraFila.getColumnas().get(0);
        assertThat(columnaLunes).hasSize(1);

        var citaColocada = primeraFila.getColumnas().get(0).get(0);
        assertThat(citaColocada.isFueraDeRango()).isTrue();
        assertThat(citaColocada.getHoraTexto()).isEqualTo("07:00");
    }
}
