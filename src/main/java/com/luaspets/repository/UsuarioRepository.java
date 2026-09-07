package com.luaspets.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByRol(Rol rol);

    List<Usuario> findByRolAndActivoTrue(Rol rol);

    // Usada por DashboardController.adminDashboard para "veterinarios activos"
    // (metrica distinta al total de doctores usado en totalUsuarios/pctDoctores,
    // que cuenta TODOS los doctores, activos o no).
    long countByRolAndActivoTrue(Rol rol);

    // Reemplaza tres countByRol (cliente/doctor/admin) por una sola consulta
    // agregada: el dashboard de admin necesita el conteo de los tres roles en
    // la misma peticion, y GROUP BY sobre una tabla de usuarios (tipicamente
    // pequena) es mas barato que tres round-trips separados. OJO al leer el
    // resultado: un rol sin ningun usuario simplemente no aparece en la lista
    // (GROUP BY no genera filas con conteo cero), hay que completarlo a 0 en
    // el controlador.
    @Query("SELECT u.rol, COUNT(u) FROM Usuario u GROUP BY u.rol")
    List<Object[]> contarPorRol();

    // Actividad reciente del dashboard de admin: los usuarios recien
    // registrados son una de las cinco fuentes que se combinan alli.
    List<Usuario> findTop8ByOrderByFechaRegistroDesc();
}
