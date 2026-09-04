package com.luaspets.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import com.luaspets.dto.ItemCarrito;
import com.luaspets.model.Producto;

@Service
@SessionScope
public class CarritoService implements Serializable {

    private final List<ItemCarrito> items = new ArrayList<>();

    public void agregarProducto(Producto producto, Integer cantidad) {
        items.stream()
                .filter(item -> item.getProductoId().equals(producto.getId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.setCantidad(item.getCantidad() + cantidad),
                        () -> items.add(new ItemCarrito(producto.getId(), producto.getNombre(),
                                producto.getPrecio(), cantidad)));
    }

    public void eliminarProducto(Long productoId) {
        items.removeIf(item -> item.getProductoId().equals(productoId));
    }

    public void actualizarCantidad(Long productoId, Integer nuevaCantidad) {
        if (nuevaCantidad <= 0) {
            eliminarProducto(productoId);
            return;
        }
        items.stream()
                .filter(item -> item.getProductoId().equals(productoId))
                .findFirst()
                .ifPresent(item -> item.setCantidad(nuevaCantidad));
    }

    public List<ItemCarrito> listarItems() {
        return items;
    }

    public BigDecimal calcularTotal() {
        return items.stream()
                .map(ItemCarrito::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int contarItems() {
        return items.stream().mapToInt(ItemCarrito::getCantidad).sum();
    }

    public void vaciar() {
        items.clear();
    }

    public boolean estaVacio() {
        return items.isEmpty();
    }
}
