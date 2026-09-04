package com.luaspets.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
