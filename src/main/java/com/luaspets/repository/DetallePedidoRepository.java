package com.luaspets.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.luaspets.model.DetallePedido;

@Repository
public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {

    List<DetallePedido> findByPedidoId(Long pedidoId);

    // Usada por los listados paginados de pedidos para mostrar la cantidad de
    // productos de cada pedido sin incluir la coleccion "detalles" en el
    // @EntityGraph de la consulta paginada (ver PedidoRepository). Devuelve
    // Object[]{pedidoId, sumaDeCantidad} solo para los pedidos de la pagina
    // actual.
    @Query("SELECT d.pedido.id, SUM(d.cantidad) FROM DetallePedido d WHERE d.pedido.id IN :pedidoIds GROUP BY d.pedido.id")
    List<Object[]> sumarCantidadesPorPedidos(@Param("pedidoIds") List<Long> pedidoIds);
}
