package com.luaspets.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.luaspets.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByActivoTrue();

    // Dashboard del admin: totalProductos (cuenta solo activos, igual que
    // productoService.listarActivos().size() antes del refactor).
    long countByActivoTrue();

    // Alerta de "stock bajo" del dashboard: trae solo los 5 productos activos
    // con menos stock, en vez de cargar todos los activos y filtrar/ordenar en
    // memoria. El umbral (10) es el mismo que usaba el stream original.
    List<Producto> findTop5ByActivoTrueAndStockLessThanOrderByStockAsc(int stock);

    List<Producto> findByCategoriaAndActivoTrue(String categoria);

    List<Producto> findByNombreContainingIgnoreCaseAndActivoTrue(String nombre);

    @Query("SELECT DISTINCT p.categoria FROM Producto p WHERE p.activo = true AND p.categoria IS NOT NULL ORDER BY p.categoria")
    List<String> findCategoriasActivas();

    // Filtro de categoria del listado paginado de /admin/productos: a
    // diferencia de findCategoriasActivas, el admin debe poder filtrar tambien
    // productos inactivos por categoria, asi que aqui no se restringe por
    // "activo".
    @Query("SELECT DISTINCT p.categoria FROM Producto p WHERE p.categoria IS NOT NULL ORDER BY p.categoria")
    List<String> findCategoriasDistintas();

    @Query("""
        SELECT p FROM Producto p
        WHERE (:categoria IS NULL OR p.categoria = :categoria)
          AND (:buscar IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :buscar, '%')))
          AND (:activo IS NULL OR p.activo = :activo)
        """)
    Page<Producto> buscarConFiltros(@Param("categoria") String categoria, @Param("buscar") String buscar,
            @Param("activo") Boolean activo, Pageable pageable);
}
