package com.luaspets.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.dto.ItemCarrito;
import com.luaspets.exception.BusinessException;
import com.luaspets.model.DetallePedido;
import com.luaspets.model.Producto;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CarritoService;
import com.luaspets.service.PedidoService;
import com.luaspets.service.ProductoService;

@Controller
@RequestMapping("/cliente/carrito")
public class CarritoController {

    private final CarritoService carritoService;
    private final ProductoService productoService;
    private final PedidoService pedidoService;

    public CarritoController(CarritoService carritoService, ProductoService productoService,
            PedidoService pedidoService) {
        this.carritoService = carritoService;
        this.productoService = productoService;
        this.pedidoService = pedidoService;
    }

    @GetMapping("")
    public String ver(Model model) {
        model.addAttribute("items", carritoService.listarItems());
        model.addAttribute("total", carritoService.calcularTotal());
        return "cliente/carrito/ver";
    }

    @PostMapping("/actualizar")
    public String actualizar(@RequestParam Long productoId, @RequestParam Integer cantidad) {
        carritoService.actualizarCantidad(productoId, cantidad);
        return "redirect:/cliente/carrito";
    }

    @PostMapping("/eliminar")
    public String eliminar(@RequestParam Long productoId) {
        carritoService.eliminarProducto(productoId);
        return "redirect:/cliente/carrito";
    }

    @PostMapping("/confirmar")
    public String confirmar(@AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (carritoService.estaVacio()) {
            redirectAttributes.addFlashAttribute("error", "El carrito está vacío");
            return "redirect:/cliente/carrito";
        }

        List<DetallePedido> detalles = new ArrayList<>();
        for (ItemCarrito item : carritoService.listarItems()) {
            Producto producto = productoService.buscarPorId(item.getProductoId());
            DetallePedido detalle = new DetallePedido();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalles.add(detalle);
        }

        Long clienteId = userDetails.getUsuario().getId();
        try {
            pedidoService.crearPedido(clienteId, detalles);
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cliente/carrito";
        }

        carritoService.vaciar();
        return "redirect:/cliente/pedidos?exito";
    }
}
