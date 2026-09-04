package com.luaspets.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
}
