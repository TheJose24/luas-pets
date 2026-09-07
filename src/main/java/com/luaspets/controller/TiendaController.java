package com.luaspets.controller;

import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.luaspets.dto.ProductoCard;
import com.luaspets.model.Producto;
import com.luaspets.service.CarritoService;
import com.luaspets.service.ProductoService;

@Controller
@RequestMapping("/cliente/tienda")
public class TiendaController {

    private static final int LONGITUD_DESCRIPCION_CORTA = 90;

    private final ProductoService productoService;
    private final CarritoService carritoService;

    public TiendaController(ProductoService productoService, CarritoService carritoService) {
        this.productoService = productoService;
        this.carritoService = carritoService;
    }

    @GetMapping("")
    public String catalogo(@RequestParam(required = false) String categoria,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "nombre") String orden,
            Model model) {

        List<Producto> productos;
        if (buscar != null && !buscar.isBlank()) {
            productos = productoService.buscarPorNombre(buscar);
        } else if (categoria != null && !categoria.isBlank()) {
            productos = productoService.buscarPorCategoria(categoria);
        } else {
            productos = productoService.listarActivos();
        }

        String ordenNorm = normalizarOrden(orden);
        List<ProductoCard> tarjetas = ordenarProductos(productos, ordenNorm).stream()
                .map(this::mapearTarjeta)
                .toList();

        model.addAttribute("productos", tarjetas);
        model.addAttribute("categorias", productoService.listarCategoriasActivas());
        model.addAttribute("categoriaSel", categoria);
        model.addAttribute("buscar", buscar);
        model.addAttribute("orden", ordenNorm);
        // catalogo.html no lo lee (el badge del carrito se pinta por AJAX vía
        // carrito.js), pero TiendaCarritoPedidoIntegrationTest sí verifica este
        // atributo directamente sobre el Model tras agregar un producto: se
        // mantiene por eso, no es código muerto.
        model.addAttribute("itemsCarrito", carritoService.contarItems());
        return "cliente/tienda/catalogo";
    }

    @PostMapping("/agregar")
    public String agregar(@RequestParam Long productoId, @RequestParam(defaultValue = "1") Integer cantidad,
            RedirectAttributes redirectAttributes) {
        Producto producto = productoService.buscarPorId(productoId);

        if (cantidad <= 0 || cantidad > producto.getStock()) {
            redirectAttributes.addFlashAttribute("error",
                    "Stock insuficiente. Disponible: " + producto.getStock());
            return "redirect:/cliente/tienda";
        }

        carritoService.agregarProducto(producto, cantidad);
        redirectAttributes.addFlashAttribute("exito", "Producto agregado al carrito");
        return "redirect:/cliente/tienda";
    }

    private String normalizarOrden(String orden) {
        if (orden == null) {
            return "nombre";
        }
        return switch (orden) {
            case "precio_asc", "precio_desc", "recientes" -> orden;
            default -> "nombre";
        };
    }

    private List<Producto> ordenarProductos(List<Producto> productos, String orden) {
        Comparator<Producto> comparador = switch (orden) {
            case "precio_asc" -> Comparator.comparing(Producto::getPrecio);
            case "precio_desc" -> Comparator.comparing(Producto::getPrecio).reversed();
            case "recientes" -> Comparator.comparing(Producto::getId).reversed();
            default -> Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER);
        };
        return productos.stream().sorted(comparador).toList();
    }

    private ProductoCard mapearTarjeta(Producto producto) {
        int stock = producto.getStock() != null ? producto.getStock() : 0;
        String etiquetaStock;
        String varianteStock;
        if (stock == 0) {
            etiquetaStock = "Agotado";
            varianteStock = "secondary";
        } else if (stock < 5) {
            etiquetaStock = "Últimas unidades";
            varianteStock = "warning";
        } else {
            etiquetaStock = "Disponible";
            varianteStock = "success";
        }

        return new ProductoCard(
                producto.getId(),
                producto.getNombre(),
                truncarDescripcion(producto.getDescripcion()),
                producto.getCategoria(),
                producto.getCategoria() != null ? producto.getCategoria().toUpperCase(Locale.ROOT) : "",
                producto.getImagenUrl(),
                "S/ " + producto.getPrecio().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                producto.getPrecio(),
                stock,
                stock > 0,
                etiquetaStock,
                varianteStock);
    }

    private String truncarDescripcion(String descripcion) {
        if (descripcion == null) {
            return "";
        }
        if (descripcion.length() <= LONGITUD_DESCRIPCION_CORTA) {
            return descripcion;
        }
        return descripcion.substring(0, LONGITUD_DESCRIPCION_CORTA) + "…";
    }
}
