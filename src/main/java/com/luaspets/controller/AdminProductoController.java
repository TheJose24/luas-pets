package com.luaspets.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.luaspets.model.Producto;
import com.luaspets.service.ProductoService;

@Controller
@RequestMapping("/admin/productos")
public class AdminProductoController {

    private final ProductoService productoService;

    public AdminProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping("")
    public String listar(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
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
}
