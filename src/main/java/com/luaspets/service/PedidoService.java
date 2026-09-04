package com.luaspets.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luaspets.exception.BusinessException;
import com.luaspets.exception.ResourceNotFoundException;
import com.luaspets.model.DetallePedido;
import com.luaspets.model.EstadoPedido;
import com.luaspets.model.Pedido;
import com.luaspets.model.Producto;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.repository.UsuarioRepository;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final int UMBRAL_STOCK_BAJO = 10;

    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final NotificacionService notificacionService;

    public PedidoService(PedidoRepository pedidoRepository, UsuarioRepository usuarioRepository,
            ProductoRepository productoRepository, NotificacionService notificacionService) {
        this.pedidoRepository = pedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public Pedido crearPedido(Long clienteId, List<DetallePedido> detalles) {
        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + clienteId));

        if (detalles == null || detalles.isEmpty()) {
            throw new BusinessException("El pedido debe tener al menos un producto");
        }

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setFecha(LocalDateTime.now());
        pedido.setEstado(EstadoPedido.PENDIENTE);

        BigDecimal total = BigDecimal.ZERO;

        for (DetallePedido detalle : detalles) {
            Producto producto = productoRepository.findById(detalle.getProducto().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado con id: " + detalle.getProducto().getId()));

            if (producto.getStock() < detalle.getCantidad()) {
                throw new BusinessException("Stock insuficiente para el producto: " + producto.getNombre());
            }

            producto.setStock(producto.getStock() - detalle.getCantidad());
            productoRepository.save(producto);

            detalle.setProducto(producto);
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setPedido(pedido);

            total = total.add(detalle.getPrecioUnitario().multiply(BigDecimal.valueOf(detalle.getCantidad())));
        }

        pedido.setTotal(total);
        pedido.setDetalles(detalles);

        Pedido guardado = pedidoRepository.save(pedido);

        // Igual que en CitaService: si notificar falla, el pedido ya quedo
        // persistido y no debe revertirse ni impedir la respuesta al cliente.
        try {
            notificacionService.notificarAdmins(TipoNotificacion.PEDIDO_NUEVO, "Nuevo pedido recibido",
                    cliente.getNombre() + " realizó un pedido por " + String.format("S/ %.2f", guardado.getTotal())
                            + ".",
                    "/admin/pedidos");
        } catch (Exception e) {
            log.error("No se pudo notificar a los admins sobre el nuevo pedido {}", guardado.getId(), e);
        }

        try {
            for (DetallePedido detalle : detalles) {
                Producto producto = detalle.getProducto();
                if (producto.getStock() < UMBRAL_STOCK_BAJO) {
                    notificacionService.notificarAdmins(TipoNotificacion.STOCK_BAJO, "Stock bajo",
                            producto.getNombre() + " tiene solo " + producto.getStock() + " unidades disponibles.",
                            "/admin/productos");
                }
            }
        } catch (Exception e) {
            log.error("No se pudo notificar a los admins sobre stock bajo tras el pedido {}", guardado.getId(), e);
        }

        return guardado;
    }

    public List<Pedido> listarPorCliente(Long clienteId) {
        return pedidoRepository.findByClienteIdOrderByFechaDesc(clienteId);
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAllByOrderByFechaDesc();
    }

    public List<Pedido> listarPorEstado(EstadoPedido estado) {
        return pedidoRepository.findByEstado(estado);
    }

    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con id: " + id));
    }

    @Transactional
    public Pedido actualizarEstado(Long pedidoId, EstadoPedido nuevoEstado) {
        Pedido pedido = buscarPorId(pedidoId);
        pedido.setEstado(nuevoEstado);
        Pedido guardado = pedidoRepository.save(pedido);

        try {
            notificacionService.crear(guardado.getCliente(), TipoNotificacion.PEDIDO_ESTADO,
                    "Tu pedido cambió de estado",
                    "El pedido #" + guardado.getId() + " ahora está "
                            + guardado.getEstado().name().toLowerCase(Locale.ROOT) + ".",
                    "/cliente/pedidos/" + guardado.getId());
        } catch (Exception e) {
            log.error("No se pudo notificar al cliente sobre el cambio de estado del pedido {}", guardado.getId(),
                    e);
        }

        return guardado;
    }
}
