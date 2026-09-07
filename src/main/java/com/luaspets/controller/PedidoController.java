package com.luaspets.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.luaspets.model.Pedido;
import com.luaspets.repository.DetallePedidoRepository;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.PedidoService;
import com.luaspets.util.PaginacionUtil;

@Controller
@RequestMapping("/cliente/pedidos")
public class PedidoController {

    private static final int TAMANIO_PAGINA = 10;

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;

    public PedidoController(PedidoService pedidoService, PedidoRepository pedidoRepository,
            DetallePedidoRepository detallePedidoRepository) {
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
    }

    @GetMapping("")
    public String listar(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page, Model model) {
        // clienteId sale siempre del usuario autenticado, nunca de un parametro de
        // la peticion: findByClienteId acota la consulta a sus propios pedidos sin
        // importar que pagina pida.
        Long clienteId = userDetails.getUsuario().getId();
        int paginaSolicitada = Math.max(page, 0);
        PageRequest pageRequest = PageRequest.of(paginaSolicitada, TAMANIO_PAGINA, Sort.by("fecha").descending());
        Page<Pedido> resultado = pedidoRepository.findByClienteId(clienteId, pageRequest);

        model.addAttribute("pedidos", resultado.getContent());
        model.addAttribute("page", resultado);
        model.addAttribute("numerosPagina", PaginacionUtil.numerosPagina(resultado));
        model.addAttribute("cantidadPorPedido", cantidadPorPedido(resultado.getContent()));

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) paginaSolicitada * TAMANIO_PAGINA + 1;
        long hasta = total == 0 ? 0 : desde + resultado.getNumberOfElements() - 1;
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "cliente/pedidos/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        Pedido pedido = pedidoService.buscarPorId(id);
        Long clienteId = userDetails.getUsuario().getId();
        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException("El pedido no pertenece al cliente logueado");
        }
        model.addAttribute("pedido", pedido);
        return "cliente/pedidos/detalle";
    }

    private Map<Long, Long> cantidadPorPedido(List<Pedido> pedidos) {
        Map<Long, Long> mapa = new HashMap<>();
        List<Long> ids = pedidos.stream().map(Pedido::getId).toList();
        if (ids.isEmpty()) {
            return mapa;
        }
        for (Object[] fila : detallePedidoRepository.sumarCantidadesPorPedidos(ids)) {
            mapa.put((Long) fila[0], ((Number) fila[1]).longValue());
        }
        return mapa;
    }
}
