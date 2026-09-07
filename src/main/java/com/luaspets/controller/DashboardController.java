package com.luaspets.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.security.CustomUserDetails;

/**
 * Los tres dashboards se resuelven con conteos y listados acotados en la base
 * de datos (ver los repositorios), no cargando tablas completas y calculando
 * con streams en memoria: en un plan de 512MB de RAM (heap de 350MB) y con
 * open-in-view=false, cargar el historial completo de citas/pedidos/productos
 * en cada visita al dashboard escalaba con el tamano total de esas tablas, no
 * con lo que la vista realmente muestra.
 */
@Controller
public class DashboardController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] MESES = { "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct",
            "Nov", "Dic" };
    private static final List<EstadoCita> ESTADOS_ACTIVOS = List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);
    private static final int UMBRAL_STOCK_BAJO = 10;

    private final UsuarioRepository usuarioRepository;
    private final CitaRepository citaRepository;
    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final MascotaRepository mascotaRepository;

    public DashboardController(UsuarioRepository usuarioRepository, CitaRepository citaRepository,
            ProductoRepository productoRepository, PedidoRepository pedidoRepository,
            MascotaRepository mascotaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.citaRepository = citaRepository;
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.mascotaRepository = mascotaRepository;
    }

    @GetMapping("/cliente/dashboard")
    public String clienteDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long clienteId = userDetails.getUsuario().getId();
        model.addAttribute("usuario", userDetails.getUsuario());

        // La tarjeta de resumen solo muestra las 2 primeras mascotas (el resto
        // se ve en /cliente/mascotas); totalMascotas es un conteo aparte, no
        // mascotas.size(), para no tener que traer todas las filas solo para
        // contarlas.
        model.addAttribute("mascotas", mascotaRepository.findTop2ByClienteIdOrderById(clienteId));
        model.addAttribute("totalMascotas", mascotaRepository.countByClienteId(clienteId));

        LocalDateTime ahora = LocalDateTime.now();

        model.addAttribute("citasProximas", citaRepository
                .countByMascotaClienteIdAndEstadoInAndFechaHoraAfter(clienteId, ESTADOS_ACTIVOS, ahora));
        model.addAttribute("proximaCita", citaRepository
                .findFirstByMascotaClienteIdAndEstadoInAndFechaHoraAfterOrderByFechaHoraAsc(clienteId,
                        ESTADOS_ACTIVOS, ahora)
                .orElse(null));

        model.addAttribute("citasPendientes",
                citaRepository.countByMascotaClienteIdAndEstado(clienteId, EstadoCita.PENDIENTE));

        model.addAttribute("totalPedidos", pedidoRepository.countByClienteId(clienteId));

        // Igual que antes (.limit(4) sobre las citas del cliente ordenadas por
        // fecha descendente), pero acotado directamente en la consulta.
        model.addAttribute("actividadReciente",
                citaRepository.findTop4ByMascotaClienteIdOrderByFechaHoraDesc(clienteId));

        return "cliente/dashboard";
    }

    @GetMapping("/doctor/dashboard")
    public String doctorDashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Long doctorId = userDetails.getUsuario().getId();
        model.addAttribute("usuario", userDetails.getUsuario());
        model.addAttribute("saludo", saludoSegunHora());

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        LocalDateTime inicioHoy = LocalDateTime.of(hoy, LocalTime.MIN);
        LocalDateTime finHoy = LocalDateTime.of(hoy, LocalTime.of(23, 59, 59));

        List<Cita> citasHoy = citaRepository.findByDoctorIdAndFechaHoraBetweenOrderByFechaHoraAsc(doctorId,
                inicioHoy, finHoy);
        model.addAttribute("citasHoy", citasHoy);
        model.addAttribute("totalCitasHoy", citasHoy.size());

        model.addAttribute("citasAtendidas", citaRepository.countByDoctorIdAndEstado(doctorId, EstadoCita.ATENDIDA));
        model.addAttribute("citasPendientes",
                citaRepository.countByDoctorIdAndEstadoIn(doctorId, ESTADOS_ACTIVOS));
        model.addAttribute("totalCitas", citaRepository.countByDoctorId(doctorId));

        model.addAttribute("proximaCita", citaRepository
                .findFirstByDoctorIdAndEstadoInAndFechaHoraAfterOrderByFechaHoraAsc(doctorId, ESTADOS_ACTIVOS, ahora)
                .orElse(null));

        model.addAttribute("citasVencidas", citaRepository
                .findTop5ByDoctorIdAndEstadoInAndFechaHoraBeforeOrderByFechaHoraAsc(doctorId, ESTADOS_ACTIVOS,
                        ahora));

        // No existe una consulta JPQL directa y simple para "las 5 mascotas
        // distintas mas recientes"; se trae un lote acotado (las 20 citas mas
        // recientes del doctor, no su historial completo) y el distinct se
        // hace en memoria sobre ese lote, documentado tambien en el
        // repositorio. Limitacion aceptada: si el doctor atendio repetidamente
        // a los mismos pocos pacientes en sus ultimas 20 citas, esta version
        // podria devolver menos de 5 pacientes distintos aunque existan mas
        // en su historial completo.
        Set<Long> mascotasVistas = new HashSet<>();
        List<Mascota> pacientesRecientes = citaRepository.findTop20ByDoctorIdOrderByFechaHoraDesc(doctorId).stream()
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

        // Una sola consulta GROUP BY para los tres conteos por rol, en vez de
        // tres countByRol separados: la tabla de usuarios es pequena y el
        // dashboard ya necesita los tres conteos en la misma peticion. OJO: un
        // rol sin usuarios no aparece en el resultado (GROUP BY no genera
        // filas con conteo cero), por eso el mapa se inicializa en 0 para los
        // tres roles antes de volcar el resultado real.
        Map<Rol, Long> conteoPorRol = new EnumMap<>(Rol.class);
        for (Rol rol : Rol.values()) {
            conteoPorRol.put(rol, 0L);
        }
        for (Object[] fila : usuarioRepository.contarPorRol()) {
            conteoPorRol.put((Rol) fila[0], (Long) fila[1]);
        }
        long totalClientes = conteoPorRol.get(Rol.CLIENTE);
        long totalDoctoresTodos = conteoPorRol.get(Rol.DOCTOR);
        long totalAdmins = conteoPorRol.get(Rol.ADMIN);
        long totalUsuarios = totalClientes + totalDoctoresTodos + totalAdmins;

        model.addAttribute("totalUsuarios", (int) totalUsuarios);
        model.addAttribute("totalClientes", (int) totalClientes);
        model.addAttribute("totalDoctores", (int) usuarioRepository.countByRolAndActivoTrue(Rol.DOCTOR));
        model.addAttribute("totalAdmins", (int) totalAdmins);
        model.addAttribute("totalMascotas", mascotaRepository.count());

        model.addAttribute("pctClientes", porcentaje(totalClientes, totalUsuarios));
        model.addAttribute("pctDoctores", porcentaje(totalDoctoresTodos, totalUsuarios));
        model.addAttribute("pctAdmins", porcentaje(totalAdmins, totalUsuarios));

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        LocalDateTime inicioHoy = LocalDateTime.of(hoy, LocalTime.MIN);
        LocalDateTime finHoy = LocalDateTime.of(hoy, LocalTime.of(23, 59, 59));
        YearMonth mesActual = YearMonth.now();
        LocalDateTime inicioMes = LocalDateTime.of(mesActual.atDay(1), LocalTime.MIN);
        LocalDateTime finMes = LocalDateTime.of(mesActual.atEndOfMonth(), LocalTime.of(23, 59, 59));

        // citasPendientes/citasAtendidas (globales, sin filtro de fecha) y
        // totalProductos/pedidosPendientes: no los usa admin/dashboard.html hoy,
        // pero seguian siendo atributos del Model antes de este refactor y las
        // restricciones piden no cambiar ningun valor, se use o no en la vista.
        model.addAttribute("citasPendientes", citaRepository.countByEstado(EstadoCita.PENDIENTE));
        model.addAttribute("citasAtendidas", citaRepository.countByEstado(EstadoCita.ATENDIDA));
        model.addAttribute("totalProductos", (int) productoRepository.countByActivoTrue());
        model.addAttribute("pedidosPendientes", pedidoRepository.countByEstado(EstadoPedido.PENDIENTE));

        model.addAttribute("citasDelMes", citaRepository.countByFechaHoraBetween(inicioMes, finMes));

        long citasHoyTotal = citaRepository.countByFechaHoraBetween(inicioHoy, finHoy);
        long citasHoyAtendidas = citaRepository.countByFechaHoraBetweenAndEstado(inicioHoy, finHoy,
                EstadoCita.ATENDIDA);
        long citasHoyPendientes = citaRepository.countByFechaHoraBetweenAndEstado(inicioHoy, finHoy,
                EstadoCita.PENDIENTE);
        long citasHoyConfirmadas = citaRepository.countByFechaHoraBetweenAndEstado(inicioHoy, finHoy,
                EstadoCita.CONFIRMADA);
        long citasHoyCanceladas = citaRepository.countByFechaHoraBetweenAndEstado(inicioHoy, finHoy,
                EstadoCita.CANCELADA);
        model.addAttribute("citasHoyTotal", (int) citasHoyTotal);
        model.addAttribute("citasHoyAtendidas", citasHoyAtendidas);
        model.addAttribute("citasHoyPendientes", citasHoyPendientes);
        model.addAttribute("citasHoyConfirmadas", citasHoyConfirmadas);
        model.addAttribute("citasHoyCanceladas", citasHoyCanceladas);
        model.addAttribute("pctHoyAtendidas", porcentaje(citasHoyAtendidas, citasHoyTotal));
        model.addAttribute("pctHoyPendientes", porcentaje(citasHoyPendientes, citasHoyTotal));
        model.addAttribute("pctHoyConfirmadas", porcentaje(citasHoyConfirmadas, citasHoyTotal));
        model.addAttribute("pctHoyCanceladas", porcentaje(citasHoyCanceladas, citasHoyTotal));

        List<Producto> productosStockBajo = productoRepository
                .findTop5ByActivoTrueAndStockLessThanOrderByStockAsc(UMBRAL_STOCK_BAJO);
        model.addAttribute("productosStockBajo", productosStockBajo);

        List<Cita> citasVencidas = citaRepository.findTop5ByEstadoInAndFechaHoraBeforeOrderByFechaHoraAsc(
                ESTADOS_ACTIVOS, ahora);
        model.addAttribute("citasVencidas", citasVencidas);

        List<Pedido> pedidosPendientesLista = pedidoRepository.findTop5ByEstadoOrderByFechaDesc(
                EstadoPedido.PENDIENTE);
        model.addAttribute("pedidosPendientesLista", pedidosPendientesLista);

        model.addAttribute("totalAlertas",
                productosStockBajo.size() + citasVencidas.size() + pedidosPendientesLista.size());

        List<String> chartLabels = new ArrayList<>();
        List<Long> chartAtendidas = new ArrayList<>();
        List<Long> chartTotales = new ArrayList<>();
        // Consulta agregada unica para los 6 meses del grafico (antes: recorrer
        // TODAS las citas del sistema y contarlas mes a mes en memoria). El mes
        // mas antiguo del rango, mesActual.minusMonths(5), es el limite inferior
        // de la consulta.
        LocalDateTime desdeGrafico = LocalDateTime.of(mesActual.minusMonths(5).atDay(1), LocalTime.MIN);
        Map<YearMonth, long[]> conteoPorMes = new HashMap<>();
        for (Object[] fila : citaRepository.contarCitasPorMesDesde(desdeGrafico, EstadoCita.ATENDIDA)) {
            int anio = ((Number) fila[0]).intValue();
            int mesNumero = ((Number) fila[1]).intValue();
            long total = ((Number) fila[2]).longValue();
            long atendidas = ((Number) fila[3]).longValue();
            conteoPorMes.put(YearMonth.of(anio, mesNumero), new long[] { total, atendidas });
        }
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = mesActual.minusMonths(i);
            chartLabels.add(MESES[mes.getMonthValue() - 1]);
            // Un mes sin ninguna cita simplemente no aparece en el resultado de
            // la consulta GROUP BY: si no esta en el mapa, su valor es 0, no se
            // omite del grafico.
            long[] conteo = conteoPorMes.getOrDefault(mes, new long[] { 0L, 0L });
            chartTotales.add(conteo[0]);
            chartAtendidas.add(conteo[1]);
        }
        model.addAttribute("chartLabels", jsonStringArray(chartLabels));
        model.addAttribute("chartCitasAtendidas", jsonNumberArray(chartAtendidas));
        model.addAttribute("chartCitasTotales", jsonNumberArray(chartTotales));

        // Actividad reciente: 5 fuentes acotadas (8 de cada una, 40 objetos como
        // maximo) en vez de las tablas completas de usuarios/citas/pedidos.
        // Se trae 8 de CADA fuente, no menos, porque cualquiera de las cinco
        // podria aportar los 8 elementos que terminan en el top 8 final una vez
        // combinadas y reordenadas por fecha: traer menos arriesgaria perder
        // registros que si deberian aparecer.
        List<ActividadItem> actUsuarios = usuarioRepository.findTop8ByOrderByFechaRegistroDesc().stream()
                .map(u -> new ActividadItem("bi-person-plus", "info",
                        u.getNombre() + " " + u.getApellido() + " se registró como "
                                + u.getRol().name().toLowerCase(),
                        "Registrado el " + u.getFechaRegistro().format(FORMATO_FECHA),
                        "Registro", u.getFechaRegistro()))
                .toList();

        List<ActividadItem> actCitasCreadas = citaRepository.findTop8ByOrderByFechaCreacionDesc().stream()
                .map(c -> new ActividadItem("bi-calendar-plus", "primary",
                        "Se agendó una cita para " + c.getMascota().getNombre(),
                        "Cita programada para el " + c.getFechaHora().format(FORMATO_FECHA),
                        "Cita", c.getFechaCreacion()))
                .toList();

        List<ActividadItem> actAtendidas = citaRepository.findTop8ByEstadoOrderByFechaHoraDesc(EstadoCita.ATENDIDA)
                .stream()
                .map(c -> new ActividadItem("bi-clipboard-check", "primary",
                        "Dr. " + c.getDoctor().getNombre() + " atendió a " + c.getMascota().getNombre(),
                        "Atendida el " + c.getFechaHora().format(FORMATO_FECHA),
                        "Atendida", c.getFechaHora()))
                .toList();

        List<ActividadItem> actCanceladas = citaRepository.findTop8ByEstadoOrderByFechaHoraDesc(EstadoCita.CANCELADA)
                .stream()
                .map(c -> new ActividadItem("bi-calendar-x", "danger",
                        "Se canceló la cita de " + c.getMascota().getNombre(),
                        "Estaba programada para el " + c.getFechaHora().format(FORMATO_FECHA),
                        "Cancelada", c.getFechaHora()))
                .toList();

        List<ActividadItem> actPedidos = pedidoRepository.findTop8ByOrderByFechaDesc().stream()
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
