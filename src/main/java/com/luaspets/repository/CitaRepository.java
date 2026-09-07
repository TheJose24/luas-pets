package com.luaspets.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Long> {

    @Override
    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    Optional<Cita> findById(Long id);

    @EntityGraph(attributePaths = {"mascota", "doctor"})
    List<Cita> findByMascotaClienteIdOrderByFechaHoraDesc(Long clienteId);

    @EntityGraph(attributePaths = {"mascota", "doctor"})
    List<Cita> findByMascotaIdOrderByFechaHoraDesc(Long mascotaId);

    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    List<Cita> findByDoctorIdOrderByFechaHoraAsc(Long doctorId);

    List<Cita> findByDoctorIdAndEstado(Long doctorId, EstadoCita estado);

    boolean existsByDoctorIdAndMascotaId(Long doctorId, Long mascotaId);

    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    List<Cita> findAllByOrderByFechaHoraDesc();

    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    List<Cita> findByEstado(EstadoCita estado);

    boolean existsByDoctorIdAndFechaHoraAndEstadoNot(Long doctorId, LocalDateTime fechaHora, EstadoCita estado);

    List<Cita> findByDoctorIdAndFechaHoraAndEstadoNot(Long doctorId, LocalDateTime fechaHora, EstadoCita estado);

    @Query("SELECT c.mascota.id, MAX(c.fechaHora) FROM Cita c WHERE c.mascota.id IN :ids AND c.estado = :estado GROUP BY c.mascota.id")
    List<Object[]> findUltimaCitaPorMascotas(@Param("ids") List<Long> ids, @Param("estado") EstadoCita estado);

    // Usada por RecordatorioService. El @EntityGraph es obligatorio: la tarea
    // accede a cita.getMascota().getCliente() y cita.getDoctor() fuera de una
    // vista (open-in-view=false), dentro de su propia transaccion.
    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    List<Cita> findByFechaHoraBetweenAndEstadoIn(LocalDateTime desde, LocalDateTime hasta, List<EstadoCita> estados);

    // Listado paginado de /admin/citas (vista lista). Los filtros se resuelven
    // en la consulta (no con streams en memoria): cada condicion se ignora si
    // su parametro llega null. El bloque de "buscar" va entre un unico par de
    // parentesis que envuelve TODAS sus condiciones OR, para que quede AND'eado
    // como una unidad con los filtros anteriores (si el parentesis no cerrara
    // al final de ese bloque, el OR se filtraria con toda la clausula WHERE).
    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    @Query("""
        SELECT c FROM Cita c
        WHERE (:estado IS NULL OR c.estado = :estado)
          AND (:doctorId IS NULL OR c.doctor.id = :doctorId)
          AND (:especie IS NULL OR c.mascota.especie = :especie)
          AND (:buscar IS NULL OR LOWER(c.mascota.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.apellido) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.doctor.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.doctor.apellido) LIKE LOWER(CONCAT('%', :buscar, '%')))
        """)
    Page<Cita> buscarTodasConFiltros(@Param("estado") EstadoCita estado, @Param("doctorId") Long doctorId,
            @Param("especie") String especie, @Param("buscar") String buscar, Pageable pageable);

    // Version para /doctor/citas: parte siempre de las citas del doctor
    // logueado (esa condicion no es opcional), sin filtro de veterinario ni de
    // especie.
    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    @Query("""
        SELECT c FROM Cita c
        WHERE c.doctor.id = :doctorId
          AND (:estado IS NULL OR c.estado = :estado)
          AND (:buscar IS NULL OR LOWER(c.mascota.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.apellido) LIKE LOWER(CONCAT('%', :buscar, '%')))
        """)
    Page<Cita> buscarPorDoctorConFiltros(@Param("doctorId") Long doctorId, @Param("estado") EstadoCita estado,
            @Param("buscar") String buscar, Pageable pageable);

    // Usadas por la vista calendario de /admin/citas y /doctor/citas: antes
    // esa vista cargaba TODAS las citas del sistema (o del doctor) en memoria
    // y filtraba la semana ahi mismo con CalendarioService; estas consultas
    // acotan por rango de fechas (y aplican los mismos filtros) directamente
    // en la base de datos, para que el calendario deje de escalar con el
    // tamano total de la tabla de citas.
    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    @Query("""
        SELECT c FROM Cita c
        WHERE c.fechaHora BETWEEN :desde AND :hasta
          AND (:estado IS NULL OR c.estado = :estado)
          AND (:doctorId IS NULL OR c.doctor.id = :doctorId)
          AND (:especie IS NULL OR c.mascota.especie = :especie)
          AND (:buscar IS NULL OR LOWER(c.mascota.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.apellido) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.doctor.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.doctor.apellido) LIKE LOWER(CONCAT('%', :buscar, '%')))
        ORDER BY c.fechaHora
        """)
    List<Cita> buscarEnRangoConFiltros(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("estado") EstadoCita estado, @Param("doctorId") Long doctorId, @Param("especie") String especie,
            @Param("buscar") String buscar);

    @EntityGraph(attributePaths = {"mascota", "mascota.cliente", "doctor"})
    @Query("""
        SELECT c FROM Cita c
        WHERE c.doctor.id = :doctorId
          AND c.fechaHora BETWEEN :desde AND :hasta
          AND (:estado IS NULL OR c.estado = :estado)
          AND (:buscar IS NULL OR LOWER(c.mascota.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.nombre) LIKE LOWER(CONCAT('%', :buscar, '%'))
               OR LOWER(c.mascota.cliente.apellido) LIKE LOWER(CONCAT('%', :buscar, '%')))
        ORDER BY c.fechaHora
        """)
    List<Cita> buscarEnRangoPorDoctorConFiltros(@Param("doctorId") Long doctorId,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("estado") EstadoCita estado, @Param("buscar") String buscar);

    @Query("SELECT DISTINCT c.mascota.especie FROM Cita c WHERE c.mascota.especie IS NOT NULL ORDER BY c.mascota.especie")
    List<String> findEspeciesDistintasEnCitas();

    // ===== DashboardController: conteos agregados en BD (ver informe de la
    // tarea de optimizacion del dashboard) en vez de cargar tablas completas
    // y contar con streams. =====

    // citasDelMes (admin): todas las citas del mes actual, cualquier estado.
    long countByFechaHoraBetween(LocalDateTime desde, LocalDateTime hasta);

    // Distribucion de "citas de hoy" por estado (admin).
    long countByFechaHoraBetweenAndEstado(LocalDateTime desde, LocalDateTime hasta, EstadoCita estado);

    // citasPendientes/citasAtendidas (admin): conteo global por estado, sin
    // restriccion de fecha. No las pide la Parte 1 del enunciado, pero existian
    // como atributos del Model antes del refactor (aunque la plantilla de
    // admin/dashboard.html no los usa) y las restricciones exigen no cambiar
    // ni un solo valor del Model, se use o no en la vista.
    long countByEstado(EstadoCita estado);

    long countByDoctorId(Long doctorId);

    long countByDoctorIdAndEstado(Long doctorId, EstadoCita estado);

    long countByDoctorIdAndEstadoIn(Long doctorId, List<EstadoCita> estados);

    long countByMascotaClienteIdAndEstado(Long clienteId, EstadoCita estado);

    long countByMascotaClienteIdAndEstadoInAndFechaHoraAfter(Long clienteId, List<EstadoCita> estados,
            LocalDateTime ahora);

    // ===== Listas acotadas (Parte 2 y Parte 5): traen solo lo que la vista
    // muestra, nunca la tabla completa. =====

    // citasVencidas del admin: necesita mascota Y doctor (la tarjeta muestra
    // "Mascota · Dr. Doctor").
    @EntityGraph(attributePaths = {"mascota", "doctor"})
    List<Cita> findTop5ByEstadoInAndFechaHoraBeforeOrderByFechaHoraAsc(List<EstadoCita> estados,
            LocalDateTime ahora);

    // Actividad reciente del admin: cada una de las cinco fuentes trae sus 8
    // mas recientes (no menos: cualquiera de las cinco podria aportar los 8
    // elementos finales una vez combinadas y reordenadas, asi que traer menos
    // arriesgaria perder registros que si deberian aparecer en el top 8 final).
    @EntityGraph(attributePaths = {"mascota"})
    List<Cita> findTop8ByOrderByFechaCreacionDesc();

    // Reutilizada para ATENDIDA y CANCELADA: el EntityGraph cubre la union de
    // lo que necesita cada uno (ATENDIDA muestra tambien el doctor).
    @EntityGraph(attributePaths = {"mascota", "doctor"})
    List<Cita> findTop8ByEstadoOrderByFechaHoraDesc(EstadoCita estado);

    // Dashboard del cliente: top 4 citas propias por fecha descendente (igual
    // que el .limit(4) original). Solo necesita mascota, no doctor.
    @EntityGraph(attributePaths = {"mascota"})
    List<Cita> findTop4ByMascotaClienteIdOrderByFechaHoraDesc(Long clienteId);

    @EntityGraph(attributePaths = {"mascota", "doctor"})
    Optional<Cita> findFirstByMascotaClienteIdAndEstadoInAndFechaHoraAfterOrderByFechaHoraAsc(Long clienteId,
            List<EstadoCita> estados, LocalDateTime ahora);

    // Dashboard del doctor: agenda de hoy, con el dueño de la mascota (la
    // vista muestra "Dueño: {nombre}").
    @EntityGraph(attributePaths = {"mascota", "mascota.cliente"})
    List<Cita> findByDoctorIdAndFechaHoraBetweenOrderByFechaHoraAsc(Long doctorId, LocalDateTime desde,
            LocalDateTime hasta);

    @EntityGraph(attributePaths = {"mascota", "mascota.cliente"})
    Optional<Cita> findFirstByDoctorIdAndEstadoInAndFechaHoraAfterOrderByFechaHoraAsc(Long doctorId,
            List<EstadoCita> estados, LocalDateTime ahora);

    // citasVencidas del doctor: la vista solo muestra mascota y fecha, no el
    // dueño ni el doctor (el doctor ya se sabe: es quien esta logueado).
    @EntityGraph(attributePaths = {"mascota"})
    List<Cita> findTop5ByDoctorIdAndEstadoInAndFechaHoraBeforeOrderByFechaHoraAsc(Long doctorId,
            List<EstadoCita> estados, LocalDateTime ahora);

    // pacientesRecientes del doctor: no existe una forma directa en JPQL de
    // pedir "las 5 mascotas distintas mas recientes" ordenadas por la fecha de
    // su cita mas reciente. Se trae un lote acotado (las 20 citas mas
    // recientes del doctor, no toda su historia) y el distinct-por-mascota se
    // hace en memoria sobre ese lote en el controlador. 20 es un margen de
    // seguridad: si el doctor atendio varias veces a los mismos pocos
    // pacientes en sus ultimas citas, un lote mas chico podria no alcanzar a
    // reunir 5 mascotas distintas aunque existan mas en su historial.
    @EntityGraph(attributePaths = {"mascota"})
    List<Cita> findTop20ByDoctorIdOrderByFechaHoraDesc(Long doctorId);

    // Parte 3: serie de 6 meses del grafico de evolucion de citas, agregada en
    // una sola consulta en vez de recorrer todas las citas del sistema
    // agrupando por mes en memoria.
    @Query("""
        SELECT YEAR(c.fechaHora), MONTH(c.fechaHora), COUNT(c),
               SUM(CASE WHEN c.estado = :atendida THEN 1 ELSE 0 END)
        FROM Cita c
        WHERE c.fechaHora >= :desde
        GROUP BY YEAR(c.fechaHora), MONTH(c.fechaHora)
        ORDER BY YEAR(c.fechaHora), MONTH(c.fechaHora)
        """)
    List<Object[]> contarCitasPorMesDesde(@Param("desde") LocalDateTime desde, @Param("atendida") EstadoCita atendida);
}
