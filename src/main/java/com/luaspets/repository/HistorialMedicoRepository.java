package com.luaspets.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.luaspets.model.HistorialMedico;

@Repository
public interface HistorialMedicoRepository extends JpaRepository<HistorialMedico, Long> {

    Optional<HistorialMedico> findByCitaId(Long citaId);

    @EntityGraph(attributePaths = {"cita", "cita.doctor", "cita.mascota"})
    List<HistorialMedico> findByCitaMascotaIdOrderByFechaDesc(Long mascotaId);
}
