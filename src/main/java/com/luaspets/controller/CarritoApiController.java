package com.luaspets.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.luaspets.dto.CarritoItemResponse;
import com.luaspets.dto.CarritoResponse;
import com.luaspets.dto.ItemCarrito;
import com.luaspets.model.Producto;
import com.luaspets.service.CarritoService;
import com.luaspets.service.ProductoService;

/**
 * API JSON del carrito, usada por el panel lateral para actualizarse sin
 * recargar la pagina. La validacion de stock ocurre SIEMPRE aqui, nunca
 * confiando en los atributos del formulario del cliente.
 */
@RestController
@RequestMapping("/cliente/api/carrito")
public class CarritoApiController {

    private final CarritoService carritoService;
    private final ProductoService productoService;

    public CarritoApiController(CarritoService carritoService, ProductoService productoService) {
        this.carritoService = carritoService;
        this.productoService = productoService;
    }

    @GetMapping("")
    public CarritoResponse ver() {
        return construirRespuesta(null, true);
    }

    @PostMapping("/agregar")
    public CarritoResponse agregar(@RequestParam Long productoId,
            @RequestParam(defaultValue = "1") Integer cantidad) {
        Producto producto = productoService.buscarPorId(productoId);

        if (cantidad == null || cantidad <= 0) {
            return construirRespuesta("La cantidad debe ser mayor a 0", false);
        }

        int cantidadActualEnCarrito = carritoService.listarItems().stream()
                .filter(item -> item.getProductoId().equals(productoId))
                .mapToInt(ItemCarrito::getCantidad)
                .findFirst()
                .orElse(0);

        if (cantidadActualEnCarrito + cantidad > producto.getStock()) {
            return construirRespuesta(
                    "Solo quedan " + producto.getStock() + " unidades de " + producto.getNombre(), false);
        }

        carritoService.agregarProducto(producto, cantidad);
        return construirRespuesta(producto.getNombre() + " agregado al carrito", true);
    }

    @PostMapping("/actualizar")
    public CarritoResponse actualizar(@RequestParam Long productoId, @RequestParam Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            carritoService.eliminarProducto(productoId);
            return construirRespuesta(null, true);
        }

        Producto producto = productoService.buscarPorId(productoId);
        if (cantidad > producto.getStock()) {
            return construirRespuesta(
                    "Solo quedan " + producto.getStock() + " unidades de " + producto.getNombre(), false);
        }

        carritoService.actualizarCantidad(productoId, cantidad);
        return construirRespuesta(null, true);
    }

    @PostMapping("/eliminar")
    public CarritoResponse eliminar(@RequestParam Long productoId) {
        carritoService.eliminarProducto(productoId);
        return construirRespuesta(null, true);
    }

    private CarritoResponse construirRespuesta(String mensaje, boolean exito) {
        List<CarritoItemResponse> items = carritoService.listarItems().stream()
                .map(this::mapearItem)
                .toList();

        BigDecimal total = carritoService.calcularTotal();

        return new CarritoResponse(items, carritoService.contarItems(), formatearPrecio(total), total,
                carritoService.estaVacio(), mensaje, exito);
    }

    private CarritoItemResponse mapearItem(ItemCarrito item) {
        Producto producto = productoService.buscarPorId(item.getProductoId());
        return new CarritoItemResponse(
                item.getProductoId(),
                item.getNombreProducto(),
                producto.getImagenUrl(),
                formatearPrecio(item.getPrecioUnitario()),
                item.getPrecioUnitario(),
                item.getCantidad(),
                producto.getStock(),
                formatearPrecio(item.getSubtotal()));
    }

    private String formatearPrecio(BigDecimal valor) {
        return "S/ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
