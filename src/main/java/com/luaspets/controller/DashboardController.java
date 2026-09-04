package com.luaspets.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.luaspets.dto.ActividadItem;
import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.EstadoPedido;
import com.luaspets.model.Mascota;
import com.luaspets.model.Pedido;
import com.luaspets.model.Producto;
import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.security.CustomUserDetails;
import com.luaspets.service.CitaService;
import com.luaspets.service.MascotaService;
import com.luaspets.service.PedidoService;
import com.luaspets.service.ProductoService;
import com.luaspets.service.UsuarioService;

@Controller
public class DashboardController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] MESES = { "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct",
            "Nov", "Dic" };

    private final UsuarioService usuarioService;
    private final CitaService citaService;
    private final ProductoService productoService;
    private final PedidoService pedidoService;
    private final MascotaService mascotaService;
    private final MascotaRepository mascotaRepository;

    public DashboardController(UsuarioService usuarioService, CitaService citaService,
            ProductoService productoService, PedidoService pedidoService, MascotaService mascotaService,
            MascotaRepository mascotaRepository) {
        this.usuarioService = usuarioService;
        this.citaService = citaService;
        this.productoService = productoService;
        this.pedidoService = pedidoService;
        this.mascotaService = mascotaService;
        this.mascotaRepository = mascotaRepository;
    }

    @GetMapping("/cliente/dashboard")
    public String clienteDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        model.addAttribute("usuario", userDetails.getUsuario());

        List<Mascota> mascotas = mascotaService.listarPorCliente(clienteId);
        model.addAttribute("mascotas", mascotas);
        model.addAttribute("totalMascotas", mascotas.size());

        List<Cita> citas = citaService.listarPorCliente(clienteId);
        model.addAttribute("citas", citas);

        LocalDateTime ahora = LocalDateTime.now();
        List<Cita> citasFuturasActivas = citas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isAfter(ahora))
                .toList();
        model.addAttribute("citasProximas", citasFuturasActivas.size());
        model.addAttribute("proximaCita",
                citasFuturasActivas.stream().min(Comparator.comparing(Cita::getFechaHora)).orElse(null));

        model.addAttribute("citasPendientes",
                citas.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE).count());

        model.addAttribute("totalPedidos", pedidoService.listarPorCliente(clienteId).size());

        model.addAttribute("actividadReciente", citas.stream().limit(4).toList());

        return "cliente/dashboard";
    }

    @GetMapping("/doctor/dashboard")
    public String doctorDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long doctorId = userDetails.getUsuario().getId();
        model.addAttribute("usuario", userDetails.getUsuario());

        model.addAttribute("saludo", saludoSegunHora());

        List<Cita> citas = citaService.listarPorDoctor(doctorId);
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();

        List<Cita> citasHoy = citas.stream()
                .filter(c -> c.getFechaHora().toLocalDate().equals(hoy))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .toList();
        model.addAttribute("citasHoy", citasHoy);
        model.addAttribute("totalCitasHoy", citasHoy.size());

        model.addAttribute("citasAtendidas",
                citas.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count());
        model.addAttribute("citasPendientes",
                citas.stream()
                        .filter(c -> c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        .count());
        model.addAttribute("totalCitas", citas.size());

        Cita proximaCita = citas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isAfter(ahora))
                .min(Comparator.comparing(Cita::getFechaHora))
                .orElse(null);
        model.addAttribute("proximaCita", proximaCita);

        List<Cita> citasVencidas = citas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .limit(5)
                .toList();
        model.addAttribute("citasVencidas", citasVencidas);

        Set<Long> mascotasVistas = new HashSet<>();
        List<Mascota> pacientesRecientes = citas.stream()
                .sorted(Comparator.comparing(Cita::getFechaHora).reversed())
                .map(Cita::getMascota)
                .filter(m -> mascotasVistas.add(m.getId()))
                .limit(5)
                .toList();
        model.addAttribute("pacientesRecientes", pacientesRecientes);

        return "doctor/dashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("usuario", userDetails.getUsuario());
        model.addAttribute("saludo", saludoSegunHora());

        List<Usuario> clientes = usuarioService.listarPorRol(Rol.CLIENTE);
        List<Usuario> doctoresTodos = usuarioService.listarPorRol(Rol.DOCTOR);
        List<Usuario> admins = usuarioService.listarPorRol(Rol.ADMIN);
        int totalDoctoresActivos = usuarioService.listarDoctoresActivos().size();
        int totalUsuarios = clientes.size() + doctoresTodos.size() + admins.size();

        model.addAttribute("totalUsuarios", totalUsuarios);
        model.addAttribute("totalClientes", clientes.size());
        model.addAttribute("totalDoctores", totalDoctoresActivos);
        model.addAttribute("totalAdmins", admins.size());
        model.addAttribute("totalMascotas", mascotaRepository.count());

        model.addAttribute("pctClientes", porcentaje(clientes.size(), totalUsuarios));
        model.addAttribute("pctDoctores", porcentaje(doctoresTodos.size(), totalUsuarios));
        model.addAttribute("pctAdmins", porcentaje(admins.size(), totalUsuarios));

        List<Cita> citas = citaService.listarTodas();
        List<Producto> productos = productoService.listarActivos();
        List<Pedido> pedidos = pedidoService.listarTodos();

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        YearMonth mesActual = YearMonth.now();

        model.addAttribute("citasPendientes",
                citas.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE).count());
        model.addAttribute("citasAtendidas",
                citas.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count());
        model.addAttribute("totalProductos", productos.size());
        model.addAttribute("pedidosPendientes",
                pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.PENDIENTE).count());

        model.addAttribute("citasDelMes",
                citas.stream().filter(c -> YearMonth.from(c.getFechaHora()).equals(mesActual)).count());

        List<Cita> citasHoy = citas.stream().filter(c -> c.getFechaHora().toLocalDate().equals(hoy)).toList();
        long citasHoyTotal = citasHoy.size();
        long citasHoyAtendidas = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count();
        long citasHoyPendientes = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE).count();
        long citasHoyConfirmadas = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.CONFIRMADA).count();
        long citasHoyCanceladas = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        model.addAttribute("citasHoyTotal", citasHoyTotal);
        model.addAttribute("citasHoyAtendidas", citasHoyAtendidas);
        model.addAttribute("citasHoyPendientes", citasHoyPendientes);
        model.addAttribute("citasHoyConfirmadas", citasHoyConfirmadas);
        model.addAttribute("citasHoyCanceladas", citasHoyCanceladas);
        model.addAttribute("pctHoyAtendidas", porcentaje(citasHoyAtendidas, citasHoyTotal));
        model.addAttribute("pctHoyPendientes", porcentaje(citasHoyPendientes, citasHoyTotal));
        model.addAttribute("pctHoyConfirmadas", porcentaje(citasHoyConfirmadas, citasHoyTotal));
        model.addAttribute("pctHoyCanceladas", porcentaje(citasHoyCanceladas, citasHoyTotal));

        List<Producto> productosStockBajo = productos.stream()
                .filter(p -> p.getStock() < 10)
                .sorted(Comparator.comparing(Producto::getStock))
                .limit(5)
                .toList();
        model.addAttribute("productosStockBajo", productosStockBajo);

        List<Cita> citasVencidas = citas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .limit(5)
                .toList();
        model.addAttribute("citasVencidas", citasVencidas);

        List<Pedido> pedidosPendientesLista = pedidos.stream()
                .filter(p -> p.getEstado() == EstadoPedido.PENDIENTE)
                .limit(5)
                .toList();
        model.addAttribute("pedidosPendientesLista", pedidosPendientesLista);

        model.addAttribute("totalAlertas",
                productosStockBajo.size() + citasVencidas.size() + pedidosPendientesLista.size());

        List<String> chartLabels = new ArrayList<>();
        List<Long> chartAtendidas = new ArrayList<>();
        List<Long> chartTotales = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = mesActual.minusMonths(i);
            chartLabels.add(MESES[mes.getMonthValue() - 1]);
            chartAtendidas.add(citas.stream()
                    .filter(c -> c.getEstado() == EstadoCita.ATENDIDA && YearMonth.from(c.getFechaHora()).equals(mes))
                    .count());
            chartTotales.add(citas.stream()
                    .filter(c -> YearMonth.from(c.getFechaHora()).equals(mes))
                    .count());
        }
        model.addAttribute("chartLabels", jsonStringArray(chartLabels));
        model.addAttribute("chartCitasAtendidas", jsonNumberArray(chartAtendidas));
        model.addAttribute("chartCitasTotales", jsonNumberArray(chartTotales));

        List<Usuario> todosUsuarios = new ArrayList<>();
        todosUsuarios.addAll(clientes);
        todosUsuarios.addAll(doctoresTodos);
        todosUsuarios.addAll(admins);

        List<ActividadItem> actUsuarios = todosUsuarios.stream()
                .sorted(Comparator.comparing(Usuario::getFechaRegistro).reversed())
                .limit(8)
                .map(u -> new ActividadItem("bi-person-plus", "info",
                        u.getNombre() + " " + u.getApellido() + " se registró como "
                                + u.getRol().name().toLowerCase(),
                        "Registrado el " + u.getFechaRegistro().format(FORMATO_FECHA),
                        "Registro", u.getFechaRegistro()))
                .toList();

        List<ActividadItem> actCitasCreadas = citas.stream()
                .sorted(Comparator.comparing(Cita::getFechaCreacion).reversed())
                .limit(8)
                .map(c -> new ActividadItem("bi-calendar-plus", "primary",
                        "Se agendó una cita para " + c.getMascota().getNombre(),
                        "Cita programada para el " + c.getFechaHora().format(FORMATO_FECHA),
                        "Cita", c.getFechaCreacion()))
                .toList();

        List<ActividadItem> actAtendidas = citas.stream()
                .filter(c -> c.getEstado() == EstadoCita.ATENDIDA)
                .sorted(Comparator.comparing(Cita::getFechaHora).reversed())
                .limit(8)
                .map(c -> new ActividadItem("bi-clipboard-check", "primary",
                        "Dr. " + c.getDoctor().getNombre() + " atendió a " + c.getMascota().getNombre(),
                        "Atendida el " + c.getFechaHora().format(FORMATO_FECHA),
                        "Atendida", c.getFechaHora()))
                .toList();

        List<ActividadItem> actCanceladas = citas.stream()
                .filter(c -> c.getEstado() == EstadoCita.CANCELADA)
                .sorted(Comparator.comparing(Cita::getFechaHora).reversed())
                .limit(8)
                .map(c -> new ActividadItem("bi-calendar-x", "danger",
                        "Se canceló la cita de " + c.getMascota().getNombre(),
                        "Estaba programada para el " + c.getFechaHora().format(FORMATO_FECHA),
                        "Cancelada", c.getFechaHora()))
                .toList();

        List<ActividadItem> actPedidos = pedidos.stream()
                .sorted(Comparator.comparing(Pedido::getFecha).reversed())
                .limit(8)
                .map(p -> new ActividadItem("bi-bag-check", "warning",
                        p.getCliente().getNombre() + " realizó un pedido",
                        String.format("Total: S/ %.2f", p.getTotal()),
                        "Pedido", p.getFecha()))
                .toList();

        List<ActividadItem> actividadReciente = Stream
                .of(actUsuarios, actCitasCreadas, actAtendidas, actCanceladas, actPedidos)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(ActividadItem::getFecha).reversed())
                .limit(8)
                .toList();
        model.addAttribute("actividadReciente", actividadReciente);

        return "admin/dashboard";
    }

    private String saludoSegunHora() {
        int hora = LocalTime.now().getHour();
        if (hora < 12) {
            return "¡Buenos días";
        } else if (hora < 19) {
            return "¡Buenas tardes";
        }
        return "¡Buenas noches";
    }

    private int porcentaje(long parte, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * parte / total);
    }

    private String jsonStringArray(List<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(valores.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    private String jsonNumberArray(List<Long> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(valores.get(i));
        }
        return sb.append(']').toString();
    }
}
