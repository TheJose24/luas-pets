package com.luaspets.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.model.EstadoPedido;
import com.luaspets.service.PedidoService;

@Controller
@RequestMapping("/admin/pedidos")
public class AdminPedidoController {

    private final PedidoService pedidoService;

    public AdminPedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("")
    public String listar(@RequestParam(required = false) EstadoPedido estado, Model model) {
        model.addAttribute("pedidos",
                estado != null ? pedidoService.listarPorEstado(estado) : pedidoService.listarTodos());
        model.addAttribute("estadoFiltro", estado);
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
}
