package com.luaspets.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.luaspets.model.EstadoMascota;
import com.luaspets.model.Mascota;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    List<Mascota> findByClienteId(Long clienteId);

    // Dashboard del cliente: reemplaza mascotaService.listarPorCliente(...).size().
    long countByClienteId(Long clienteId);

    // Dashboard del cliente: la vista solo muestra las 2 primeras mascotas en
    // la tarjeta de resumen (el resto se ve en /cliente/mascotas). Se ordena
    // explicitamente por id ascendente para que el resultado sea deterministico
    // (antes se usaba findByClienteId sin ORDER BY, cuyo orden no estaba
    // garantizado por JPA aunque en la practica H2/MySQL devolvian las filas en
    // orden de insercion).
    List<Mascota> findTop2ByClienteIdOrderById(Long clienteId);

    @EntityGraph(attributePaths = {"cliente"})
    Optional<Mascota> findWithClienteById(Long id);

    List<Mascota> findByEstadoIsNull();

    @EntityGraph(attributePaths = {"cliente"})
    @Query("""
        SELECT m FROM Mascota m
        WHERE (:nombre IS NULL OR LOWER(m.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:especie IS NULL OR m.especie = :especie)
          AND (:raza IS NULL OR m.raza = :raza)
          AND (:estado IS NULL OR m.estado = :estado)
        """)
    Page<Mascota> buscarConFiltros(@Param("nombre") String nombre,
            @Param("especie") String especie,
            @Param("raza") String raza,
            @Param("estado") EstadoMascota estado,
            Pageable pageable);

    @Query("SELECT DISTINCT m.especie FROM Mascota m WHERE m.especie IS NOT NULL ORDER BY m.especie")
    List<String> findEspeciesDistintas();

    @Query("SELECT DISTINCT m.raza FROM Mascota m WHERE m.raza IS NOT NULL AND m.raza <> '' ORDER BY m.raza")
    List<String> findRazasDistintas();
}
