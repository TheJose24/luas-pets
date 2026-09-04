package com.luaspets.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.model.Pedido;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.PedidoService;

@Controller
@RequestMapping("/cliente/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("")
    public String listar(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        model.addAttribute("pedidos", pedidoService.listarPorCliente(clienteId));
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
}
