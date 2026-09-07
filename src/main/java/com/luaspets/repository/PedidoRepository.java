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

import com.luaspets.model.EstadoPedido;
import com.luaspets.model.Pedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = {"detalles"})
    List<Pedido> findByClienteIdOrderByFechaDesc(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "detalles"})
    List<Pedido> findAllByOrderByFechaDesc();

    @EntityGraph(attributePaths = {"cliente", "detalles"})
    List<Pedido> findByEstado(EstadoPedido estado);

    @Override
    @EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
    Optional<Pedido> findById(Long id);

    // Listados paginados de /admin/pedidos y /cliente/pedidos. A proposito NO
    // se incluye "detalles" en el @EntityGraph: Hibernate no puede paginar en
    // base de datos cuando el grafo trae una coleccion (@OneToMany), porque
    // tendria que traer TODAS las filas del join a memoria y paginar ahi (deja
    // un aviso HHH90003004 en el log). La cantidad de productos por pedido que
    // antes se mostraba con #lists.size(pedido.detalles) ahora se resuelve con
    // DetallePedidoRepository.sumarCantidadesPorPedidos, una consulta agregada
    // aparte por los ids de la pagina actual.
    @EntityGraph(attributePaths = {"cliente"})
    @Query("SELECT p FROM Pedido p WHERE (:estado IS NULL OR p.estado = :estado)")
    Page<Pedido> buscarConFiltros(@Param("estado") EstadoPedido estado, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente"})
    Page<Pedido> findByClienteId(Long clienteId, Pageable pageable);

    // Dashboard del admin: pedidosPendientes (conteo global, sin limite) y
    // pedidosPendientesLista (los 5 mas recientes). Igual que en
    // buscarConFiltros, "detalles" NO va en el @EntityGraph de una consulta
    // con limite (Top5): es una coleccion @OneToMany y Hibernate no puede
    // paginarla/limitarla en la base de datos si el grafo la incluye.
    long countByEstado(EstadoPedido estado);

    @EntityGraph(attributePaths = {"cliente"})
    List<Pedido> findTop5ByEstadoOrderByFechaDesc(EstadoPedido estado);

    // Actividad reciente del admin: los 8 pedidos mas recientes, sin
    // "detalles" en el grafo por la misma razon de arriba.
    @EntityGraph(attributePaths = {"cliente"})
    List<Pedido> findTop8ByOrderByFechaDesc();

    // Dashboard del cliente: totalPedidos.
    long countByClienteId(Long clienteId);
}
