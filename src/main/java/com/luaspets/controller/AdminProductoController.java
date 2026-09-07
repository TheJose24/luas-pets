package com.luaspets.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.luaspets.model.Producto;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.service.ProductoService;
import com.luaspets.util.PaginacionUtil;

@Controller
@RequestMapping("/admin/productos")
public class AdminProductoController {

    private static final int TAMANIO_PAGINA = 10;

    private final ProductoService productoService;
    private final ProductoRepository productoRepository;

    public AdminProductoController(ProductoService productoService, ProductoRepository productoRepository) {
        this.productoService = productoService;
        this.productoRepository = productoRepository;
    }

    @GetMapping("")
    public String listar(
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        String buscarNorm = normalizar(buscar);
        String categoriaNorm = normalizar(categoria);
        int paginaSolicitada = Math.max(page, 0);

        PageRequest pageRequest = PageRequest.of(paginaSolicitada, TAMANIO_PAGINA, Sort.by("nombre").ascending());
        Page<Producto> resultado = productoRepository.buscarConFiltros(categoriaNorm, buscarNorm, activo,
                pageRequest);

        model.addAttribute("productos", resultado.getContent());
        model.addAttribute("page", resultado);
        model.addAttribute("numerosPagina", PaginacionUtil.numerosPagina(resultado));
        model.addAttribute("categorias", productoRepository.findCategoriasDistintas());
        model.addAttribute("buscar", buscarNorm);
        model.addAttribute("categoriaSel", categoriaNorm);
        model.addAttribute("activoSel", activo);
        model.addAttribute("hayFiltros", buscarNorm != null || categoriaNorm != null || activo != null);
        model.addAttribute("queryFiltros", construirQueryFiltros(buscarNorm, categoriaNorm, activo));

        long total = resultado.getTotalElements();
        long desde = total == 0 ? 0 : (long) paginaSolicitada * TAMANIO_PAGINA + 1;
        long hasta = total == 0 ? 0 : desde + resultado.getNumberOfElements() - 1;
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "admin/productos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevoForm(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("esEdicion", false);
        return "admin/productos/formulario";
    }

    @PostMapping("/nuevo")
    public String nuevo(@ModelAttribute Producto producto) {
        productoService.crearProducto(producto);
        return "redirect:/admin/productos?exito";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        model.addAttribute("producto", productoService.buscarPorId(id));
        model.addAttribute("esEdicion", true);
        return "admin/productos/formulario";
    }

    @PostMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long productoId, @ModelAttribute Producto producto) {
        productoService.actualizarProducto(productoId, producto);
        return "redirect:/admin/productos?actualizado";
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(@PathVariable Long id) {
        productoService.desactivarProducto(id);
        return "redirect:/admin/productos";
    }

    @PostMapping("/{id}/activar")
    public String activar(@PathVariable Long id) {
        productoService.activarProducto(id);
        return "redirect:/admin/productos";
    }

    private String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private String construirQueryFiltros(String buscar, String categoria, Boolean activo) {
        StringBuilder sb = new StringBuilder();
        if (buscar != null) {
            sb.append("&buscar=").append(URLEncoder.encode(buscar, StandardCharsets.UTF_8));
        }
        if (categoria != null) {
            sb.append("&categoria=").append(URLEncoder.encode(categoria, StandardCharsets.UTF_8));
        }
        if (activo != null) {
            sb.append("&activo=").append(activo);
        }
        return sb.toString();
    }
}
