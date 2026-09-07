package com.luaspets.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.model.EstadoPedido;
import com.luaspets.model.Pedido;
import com.luaspets.repository.DetallePedidoRepository;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.service.PedidoService;
import com.luaspets.util.PaginacionUtil;

@Controller
@RequestMapping("/admin/pedidos")
public class AdminPedidoController {

    private static final int TAMANIO_PAGINA = 10;

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;

    public AdminPedidoController(PedidoService pedidoService, PedidoRepository pedidoRepository,
            DetallePedidoRepository detallePedidoRepository) {
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
    }

    @GetMapping("")
    public String listar(@RequestParam(required = false) EstadoPedido estado,
            @RequestParam(defaultValue = "0") int page, Model model) {

        int paginaSolicitada = Math.max(page, 0);
        PageRequest pageRequest = PageRequest.of(paginaSolicitada, TAMANIO_PAGINA, Sort.by("fecha").descending());
        Page<Pedido> resultado = pedidoRepository.buscarConFiltros(estado, pageRequest);

        model.addAttribute("pedidos", resultado.getContent());
        model.addAttribute("page", resultado);
        model.addAttribute("numerosPagina", PaginacionUtil.numerosPagina(resultado));
        model.addAttribute("cantidadPorPedido", cantidadPorPedido(resultado.getContent()));
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("queryFiltros", estado != null ? "&estado=" + estado.name() : "");

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) paginaSolicitada * TAMANIO_PAGINA + 1;
        long hasta = total == 0 ? 0 : desde + resultado.getNumberOfElements() - 1;
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "admin/pedidos/lista";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("pedido", pedidoService.buscarPorId(id));
        return "admin/pedidos/detalle";
    }

    @PostMapping("/{id}/estado")
    public String actualizarEstado(@PathVariable Long id, @RequestParam EstadoPedido nuevoEstado,
            RedirectAttributes redirectAttributes) {
        pedidoService.actualizarEstado(id, nuevoEstado);
        redirectAttributes.addFlashAttribute("exito", "Estado del pedido actualizado correctamente.");
        return "redirect:/admin/pedidos";
    }

    // La cantidad de productos por pedido se resuelve con una consulta agregada
    // aparte (ver DetallePedidoRepository.sumarCantidadesPorPedidos) en vez de
    // incluir "detalles" en el @EntityGraph de la consulta paginada: Hibernate
    // no puede paginar en base de datos si el grafo trae una coleccion.
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
