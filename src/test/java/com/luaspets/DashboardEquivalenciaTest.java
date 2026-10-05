package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.model.Cita;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.EstadoPedido;
import com.luaspets.model.Mascota;
import com.luaspets.model.Pedido;
import com.luaspets.model.Producto;
import com.luaspets.model.Rol;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.repository.UsuarioRepository;

/**
 * Verifica que el refactor de DashboardController (conteos y listas acotadas
 * resueltas en la base de datos) devuelve EXACTAMENTE los mismos valores que
 * la implementacion anterior basada en streams sobre las tablas completas.
 *
 * Metodo: en vez de fijar cifras absolutas esperadas (fragil frente a los
 * datos que siembra DataSeeder o que dejan otras pruebas), cada prueba
 * recalcula el "oraculo" con la MISMA logica que tenia el controlador antes
 * del refactor (streams sobre findAll()/findByRol()/etc., ejecutados dentro
 * de la misma transaccion que la peticion HTTP) y compara ese resultado
 * contra lo que el controlador reoptimizado realmente devuelve en el Model.
 * Asi la prueba es valida sin importar cuantos datos haya sembrado
 * DataSeeder u otras clases de prueba.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardEquivalenciaTest {

    private static final String[] MESES = { "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct",
            "Nov", "Dic" };

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }
        return mockMvc;
    }

    private MockHttpSession registrarYLoguearCliente(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc().perform(post("/registro").with(csrf()).session(session)
                .param("nombre", "Cliente")
                .param("apellido", "DePrueba")
                .param("email", email)
                .param("password", "clave123")
                .param("confirmPassword", "clave123")
                .param("telefono", "987654321"));
        mvc().perform(post("/login").with(csrf()).session(session)
                .param("username", email)
                .param("password", "clave123"));
        return session;
    }

    private MockHttpSession loguearComo(String email, String password) throws Exception {
        if (email.equals("admin@luaspets.com")) {
            return MfaTestSupport.adminLogin(mvc(), webApplicationContext, email, password);
        }
        MockHttpSession session = new MockHttpSession();
        mvc().perform(post("/login").with(csrf()).session(session)
                .param("username", email)
                .param("password", password));
        return session;
    }

    private Usuario crearUsuario(String email, Rol rol, boolean activo, LocalDateTime fechaRegistro) {
        Usuario usuario = new Usuario();
        usuario.setNombre("N" + email);
        usuario.setApellido("A" + email);
        usuario.setEmail(email);
        usuario.setPassword("hash-no-relevante");
        usuario.setTelefono("987654321");
        usuario.setRol(rol);
        usuario.setActivo(activo);
        usuario.setFechaRegistro(fechaRegistro);
        return usuarioRepository.save(usuario);
    }

    private Mascota crearMascota(Usuario cliente, String nombre) {
        Mascota mascota = new Mascota();
        mascota.setNombre(nombre);
        mascota.setEspecie("Perro");
        mascota.setCliente(cliente);
        return mascotaRepository.save(mascota);
    }

    private Cita crearCita(Mascota mascota, Usuario doctor, LocalDateTime fechaHora, EstadoCita estado,
            LocalDateTime fechaCreacion) {
        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaHora);
        cita.setMotivo("Motivo");
        cita.setEstado(estado);
        cita.setFechaCreacion(fechaCreacion);
        return citaRepository.save(cita);
    }

    private Producto crearProducto(String nombre, int stock, boolean activo) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setPrecio(new BigDecimal("20.00"));
        producto.setStock(stock);
        producto.setCategoria("General");
        producto.setActivo(activo);
        return productoRepository.save(producto);
    }

    private Pedido crearPedido(Usuario cliente, EstadoPedido estado, LocalDateTime fecha) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setFecha(fecha);
        pedido.setEstado(estado);
        pedido.setTotal(new BigDecimal("30.00"));
        return pedidoRepository.save(pedido);
    }

    private int porcentaje(long parte, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * parte / total);
    }

    @SuppressWarnings("unchecked")
    private <T> T atributo(MvcResult resultado, String nombre) {
        return (T) resultado.getModelAndView().getModel().get(nombre);
    }

    // ================= ADMIN =================

    @Test
    void adminDashboardCalculaTodasLasMetricasIgualQueLaImplementacionAnterior() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        String marcador = "AdminDashEq";

        // Usuarios de cada rol, incluyendo un doctor inactivo (para separar
        // "total doctores" de "doctores activos").
        Usuario clienteA = crearUsuario(marcador + ".clienteA@test.com", Rol.CLIENTE, true, ahora.minusDays(1));
        Usuario clienteB = crearUsuario(marcador + ".clienteB@test.com", Rol.CLIENTE, true, ahora.minusDays(2));
        Usuario doctorActivo = crearUsuario(marcador + ".doctorActivo@test.com", Rol.DOCTOR, true,
                ahora.minusHours(3));
        crearUsuario(marcador + ".doctorInactivo@test.com", Rol.DOCTOR, false, ahora.minusHours(4));
        crearUsuario(marcador + ".admin@test.com", Rol.ADMIN, true, ahora.minusHours(1));

        Mascota mascotaA = crearMascota(clienteA, marcador + "-MascotaA");
        Mascota mascotaB = crearMascota(clienteB, marcador + "-MascotaB");

        // Citas de HOY en los cuatro estados (para la distribucion con
        // porcentajes) mas citas en otros dias/estados para que
        // citasPendientes/citasAtendidas (globales) difieran de los conteos de
        // "hoy".
        // fechaCreacion escalonada (nunca empatada): dos citas con el mismo
        // instante de creacion dejarian su orden relativo indefinido tanto en
        // la implementacion vieja como en la nueva (ninguna de las dos fijaba
        // un criterio de desempate), asi que la prueba evita esa ambiguedad en
        // vez de depender de un orden que nunca estuvo garantizado.
        crearCita(mascotaA, doctorActivo, ahora.withHour(9).withMinute(0), EstadoCita.PENDIENTE, ahora);
        crearCita(mascotaA, doctorActivo, ahora.withHour(10).withMinute(0), EstadoCita.CONFIRMADA,
                ahora.minusSeconds(1));
        crearCita(mascotaB, doctorActivo, ahora.withHour(11).withMinute(0), EstadoCita.ATENDIDA,
                ahora.minusSeconds(2));
        crearCita(mascotaB, doctorActivo, ahora.withHour(12).withMinute(0), EstadoCita.CANCELADA,
                ahora.minusSeconds(3));
        // Cita pendiente vencida (de "ayer"), para citasVencidas.
        crearCita(mascotaA, doctorActivo, ahora.minusDays(1), EstadoCita.PENDIENTE, ahora.minusDays(2));
        // Cita atendida antigua para actividad reciente / conteos globales.
        crearCita(mascotaB, doctorActivo, ahora.minusDays(20), EstadoCita.ATENDIDA, ahora.minusDays(21));

        Producto productoBajo1 = crearProducto(marcador + "-Bajo1", 2, true);
        Producto productoBajo2 = crearProducto(marcador + "-Bajo2", 5, true);
        crearProducto(marcador + "-Normal", 50, true);
        crearProducto(marcador + "-InactivoBajo", 1, false);

        crearPedido(clienteA, EstadoPedido.PENDIENTE, ahora.minusHours(1));
        crearPedido(clienteB, EstadoPedido.PENDIENTE, ahora.minusHours(2));
        crearPedido(clienteA, EstadoPedido.ENTREGADO, ahora.minusDays(3));

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        MvcResult resultado = mvc().perform(get("/admin/dashboard").session(sesionAdmin))
                .andExpect(status().isOk())
                .andReturn();

        // ---- Oraculo: misma logica que la implementacion original ----
        List<Usuario> todosLosUsuarios = usuarioRepository.findAll();
        List<Usuario> clientes = todosLosUsuarios.stream().filter(u -> u.getRol() == Rol.CLIENTE).toList();
        List<Usuario> doctoresTodos = todosLosUsuarios.stream().filter(u -> u.getRol() == Rol.DOCTOR).toList();
        List<Usuario> admins = todosLosUsuarios.stream().filter(u -> u.getRol() == Rol.ADMIN).toList();
        int totalDoctoresActivosEsperado = (int) doctoresTodos.stream().filter(Usuario::getActivo).count();
        int totalUsuariosEsperado = clientes.size() + doctoresTodos.size() + admins.size();

        assertThat((Integer) atributo(resultado, "totalUsuarios")).isEqualTo(totalUsuariosEsperado);
        assertThat((Integer) atributo(resultado, "totalClientes")).isEqualTo(clientes.size());
        assertThat((Integer) atributo(resultado, "totalDoctores")).isEqualTo(totalDoctoresActivosEsperado);
        assertThat((Integer) atributo(resultado, "totalAdmins")).isEqualTo(admins.size());
        assertThat((Long) atributo(resultado, "totalMascotas")).isEqualTo(mascotaRepository.count());

        assertThat((Integer) atributo(resultado, "pctClientes"))
                .isEqualTo(porcentaje(clientes.size(), totalUsuariosEsperado));
        assertThat((Integer) atributo(resultado, "pctDoctores"))
                .isEqualTo(porcentaje(doctoresTodos.size(), totalUsuariosEsperado));
        assertThat((Integer) atributo(resultado, "pctAdmins"))
                .isEqualTo(porcentaje(admins.size(), totalUsuariosEsperado));

        List<Cita> todasLasCitas = citaRepository.findAll();
        List<Producto> todosLosProductos = productoRepository.findByActivoTrue();
        List<Pedido> todosLosPedidos = pedidoRepository.findAll();

        LocalDate hoy = ahora.toLocalDate();
        YearMonth mesActual = YearMonth.now();

        assertThat((Long) atributo(resultado, "citasPendientes"))
                .isEqualTo(todasLasCitas.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE).count());
        assertThat((Long) atributo(resultado, "citasAtendidas"))
                .isEqualTo(todasLasCitas.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count());
        assertThat((Integer) atributo(resultado, "totalProductos")).isEqualTo(todosLosProductos.size());
        assertThat((Long) atributo(resultado, "pedidosPendientes")).isEqualTo(
                todosLosPedidos.stream().filter(p -> p.getEstado() == EstadoPedido.PENDIENTE).count());

        assertThat((Long) atributo(resultado, "citasDelMes")).isEqualTo(
                todasLasCitas.stream().filter(c -> YearMonth.from(c.getFechaHora()).equals(mesActual)).count());

        List<Cita> citasHoy = todasLasCitas.stream().filter(c -> c.getFechaHora().toLocalDate().equals(hoy)).toList();
        long citasHoyAtendidasEsp = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count();
        long citasHoyPendientesEsp = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE).count();
        long citasHoyConfirmadasEsp = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.CONFIRMADA).count();
        long citasHoyCanceladasEsp = citasHoy.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();

        assertThat((Integer) atributo(resultado, "citasHoyTotal")).isEqualTo(citasHoy.size());
        assertThat((Long) atributo(resultado, "citasHoyAtendidas")).isEqualTo(citasHoyAtendidasEsp);
        assertThat((Long) atributo(resultado, "citasHoyPendientes")).isEqualTo(citasHoyPendientesEsp);
        assertThat((Long) atributo(resultado, "citasHoyConfirmadas")).isEqualTo(citasHoyConfirmadasEsp);
        assertThat((Long) atributo(resultado, "citasHoyCanceladas")).isEqualTo(citasHoyCanceladasEsp);
        assertThat((Integer) atributo(resultado, "pctHoyAtendidas"))
                .isEqualTo(porcentaje(citasHoyAtendidasEsp, citasHoy.size()));
        assertThat((Integer) atributo(resultado, "pctHoyPendientes"))
                .isEqualTo(porcentaje(citasHoyPendientesEsp, citasHoy.size()));
        assertThat((Integer) atributo(resultado, "pctHoyConfirmadas"))
                .isEqualTo(porcentaje(citasHoyConfirmadasEsp, citasHoy.size()));
        assertThat((Integer) atributo(resultado, "pctHoyCanceladas"))
                .isEqualTo(porcentaje(citasHoyCanceladasEsp, citasHoy.size()));

        List<Producto> productosStockBajoEsp = todosLosProductos.stream()
                .filter(p -> p.getStock() < 10)
                .sorted(Comparator.comparing(Producto::getStock))
                .limit(5)
                .toList();
        List<Cita> citasVencidasEsp = todasLasCitas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .limit(5)
                .toList();
        List<Pedido> pedidosPendientesListaEsp = todosLosPedidos.stream()
                .filter(p -> p.getEstado() == EstadoPedido.PENDIENTE)
                .sorted(Comparator.comparing(Pedido::getFecha).reversed())
                .limit(5)
                .toList();

        List<Producto> productosStockBajoReal = atributo(resultado, "productosStockBajo");
        List<Cita> citasVencidasReal = atributo(resultado, "citasVencidas");
        List<Pedido> pedidosPendientesListaReal = atributo(resultado, "pedidosPendientesLista");

        assertThat(productosStockBajoReal).hasSameSizeAs(productosStockBajoEsp);
        assertThat(productosStockBajoReal.stream().map(Producto::getId).toList())
                .containsExactlyElementsOf(productosStockBajoEsp.stream().map(Producto::getId).toList());
        assertThat(citasVencidasReal).hasSameSizeAs(citasVencidasEsp);
        assertThat(citasVencidasReal.stream().map(Cita::getId).toList())
                .containsExactlyElementsOf(citasVencidasEsp.stream().map(Cita::getId).toList());
        assertThat(pedidosPendientesListaReal).hasSameSizeAs(pedidosPendientesListaEsp);
        assertThat(pedidosPendientesListaReal.stream().map(Pedido::getId).toList())
                .containsExactlyElementsOf(pedidosPendientesListaEsp.stream().map(Pedido::getId).toList());

        assertThat((Integer) atributo(resultado, "totalAlertas")).isEqualTo(
                productosStockBajoEsp.size() + citasVencidasEsp.size() + pedidosPendientesListaEsp.size());
        // Confirma que las alertas creadas en esta prueba realmente entraron en
        // las listas (no solo que los tamanos coincidan por casualidad).
        assertThat(productosStockBajoReal.stream().map(Producto::getId))
                .contains(productoBajo1.getId(), productoBajo2.getId());

        // ---- Grafico de 6 meses ----
        List<String> chartLabelsEsp = new ArrayList<>();
        List<Long> chartTotalesEsp = new ArrayList<>();
        List<Long> chartAtendidasEsp = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = mesActual.minusMonths(i);
            chartLabelsEsp.add(MESES[mes.getMonthValue() - 1]);
            chartAtendidasEsp.add(todasLasCitas.stream()
                    .filter(c -> c.getEstado() == EstadoCita.ATENDIDA && YearMonth.from(c.getFechaHora()).equals(mes))
                    .count());
            chartTotalesEsp.add(todasLasCitas.stream()
                    .filter(c -> YearMonth.from(c.getFechaHora()).equals(mes))
                    .count());
        }
        assertThat((String) atributo(resultado, "chartLabels")).isEqualTo(jsonStringArray(chartLabelsEsp));
        assertThat((String) atributo(resultado, "chartCitasTotales")).isEqualTo(jsonNumberArray(chartTotalesEsp));
        assertThat((String) atributo(resultado, "chartCitasAtendidas")).isEqualTo(jsonNumberArray(chartAtendidasEsp));

        // ---- Actividad reciente: tamano, orden y contenido ----
        List<Object> actividadUsuarios = todosLosUsuarios.stream()
                .sorted(Comparator.comparing(Usuario::getFechaRegistro).reversed())
                .limit(8)
                .<Object>map(u -> "Registro|" + (u.getNombre() + " " + u.getApellido() + " se registró como "
                        + u.getRol().name().toLowerCase()) + "|" + u.getFechaRegistro())
                .toList();
        record Actividad(String etiqueta, String descripcion, LocalDateTime fecha) {
        }
        List<Actividad> esperadas = new ArrayList<>();
        todosLosUsuarios.stream().sorted(Comparator.comparing(Usuario::getFechaRegistro).reversed()).limit(8)
                .forEach(u -> esperadas.add(new Actividad("Registro",
                        u.getNombre() + " " + u.getApellido() + " se registró como " + u.getRol().name().toLowerCase(),
                        u.getFechaRegistro())));
        todasLasCitas.stream().sorted(Comparator.comparing(Cita::getFechaCreacion).reversed()).limit(8)
                .forEach(c -> esperadas.add(new Actividad("Cita",
                        "Se agendó una cita para " + c.getMascota().getNombre(), c.getFechaCreacion())));
        todasLasCitas.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA)
                .sorted(Comparator.comparing(Cita::getFechaHora).reversed()).limit(8)
                .forEach(c -> esperadas.add(new Actividad("Atendida",
                        "Dr. " + c.getDoctor().getNombre() + " atendió a " + c.getMascota().getNombre(),
                        c.getFechaHora())));
        todasLasCitas.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA)
                .sorted(Comparator.comparing(Cita::getFechaHora).reversed()).limit(8)
                .forEach(c -> esperadas.add(new Actividad("Cancelada",
                        "Se canceló la cita de " + c.getMascota().getNombre(), c.getFechaHora())));
        todosLosPedidos.stream().sorted(Comparator.comparing(Pedido::getFecha).reversed()).limit(8)
                .forEach(p -> esperadas.add(new Actividad("Pedido", p.getCliente().getNombre() + " realizó un pedido",
                        p.getFecha())));

        List<Actividad> actividadEsperada = esperadas.stream()
                .sorted(Comparator.comparing(Actividad::fecha).reversed())
                .limit(8)
                .toList();

        List<com.luaspets.dto.ActividadItem> actividadReal = atributo(resultado, "actividadReciente");
        assertThat(actividadReal).hasSameSizeAs(actividadEsperada);
        List<String> firmaReal = actividadReal.stream()
                .map(a -> a.getEtiqueta() + "|" + a.getDescripcion() + "|" + a.getFecha())
                .toList();
        List<String> firmaEsperada = actividadEsperada.stream()
                .map(a -> a.etiqueta() + "|" + a.descripcion() + "|" + a.fecha())
                .toList();
        assertThat(firmaReal).containsExactlyElementsOf(firmaEsperada);

        assertThat(actividadUsuarios).isNotEmpty(); // sanity: el oraculo si genero actividad de usuarios
    }

    @Test
    void adminDashboardGraficoRellenaConCeroLosMesesSinCitas() throws Exception {
        // DataSeeder solo siembra citas dentro de +-10 dias de "ahora", asi que
        // mesActual.minusMonths(3) esta garantizado vacio salvo por lo que esta
        // prueba decida insertar ahi, y deliberadamente no se inserta nada.
        LocalDateTime ahora = LocalDateTime.now();
        String marcador = "GraficoMesVacio";
        Usuario cliente = crearUsuario(marcador + "@test.com", Rol.CLIENTE, true, ahora);
        Usuario doctor = usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();
        Mascota mascota = crearMascota(cliente, marcador + "-Mascota");

        YearMonth mesActual = YearMonth.now();
        YearMonth mesVacio = mesActual.minusMonths(3);
        crearCita(mascota, doctor, mesActual.minusMonths(1).atDay(10).atTime(10, 0), EstadoCita.ATENDIDA,
                ahora.minusMonths(1));
        crearCita(mascota, doctor, mesActual.minusMonths(4).atDay(10).atTime(10, 0), EstadoCita.PENDIENTE,
                ahora.minusMonths(4));

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        MvcResult resultado = mvc().perform(get("/admin/dashboard").session(sesionAdmin))
                .andExpect(status().isOk())
                .andReturn();

        int indiceMesVacio = 5 - (int) java.time.temporal.ChronoUnit.MONTHS.between(mesVacio, mesActual);
        String totalesJson = atributo(resultado, "chartCitasTotales");
        String atendidasJson = atributo(resultado, "chartCitasAtendidas");
        List<Long> totales = parsearJsonNumeros(totalesJson);
        List<Long> atendidas = parsearJsonNumeros(atendidasJson);

        assertThat(totales.get(indiceMesVacio)).isEqualTo(0L);
        assertThat(atendidas.get(indiceMesVacio)).isEqualTo(0L);
    }

    @Test
    void adminDashboardSinCitasHoyLosPorcentajesSonCero() throws Exception {
        // Ninguna cita sembrada por DataSeeder cae exactamente "hoy" (todos sus
        // offsets son +-3/4/5/10 dias), y esta prueba no crea ninguna cita
        // nueva: citasHoyTotal debe ser 0 y los cuatro porcentajes tambien,
        // sin division por cero.
        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        MvcResult resultado = mvc().perform(get("/admin/dashboard").session(sesionAdmin))
                .andExpect(status().isOk())
                .andReturn();

        Integer citasHoyTotal = atributo(resultado, "citasHoyTotal");
        assertThat(citasHoyTotal).isZero();
        assertThat((Integer) atributo(resultado, "pctHoyAtendidas")).isZero();
        assertThat((Integer) atributo(resultado, "pctHoyPendientes")).isZero();
        assertThat((Integer) atributo(resultado, "pctHoyConfirmadas")).isZero();
        assertThat((Integer) atributo(resultado, "pctHoyCanceladas")).isZero();
    }

    @Test
    void adminDashboardSinAlertasMuestraListasVaciasYTotalAlertasCero() throws Exception {
        // Neutraliza cualquier disparador de alerta que ya exista en la base
        // (sembrado por DataSeeder o por el estado compartido de la suite):
        // sube el stock de todos los productos, marca como ENTREGADO todos los
        // pedidos PENDIENTE, y marca como ATENDIDA cualquier cita PENDIENTE o
        // CONFIRMADA que ya haya vencido. Los cambios se revierten al terminar
        // la prueba (@Transactional).
        productoRepository.findAll().forEach(p -> {
            p.setStock(999);
            productoRepository.save(p);
        });
        pedidoRepository.findAll().stream()
                .filter(p -> p.getEstado() == EstadoPedido.PENDIENTE)
                .forEach(p -> {
                    p.setEstado(EstadoPedido.ENTREGADO);
                    pedidoRepository.save(p);
                });
        LocalDateTime ahora = LocalDateTime.now();
        citaRepository.findAll().stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isBefore(ahora))
                .forEach(c -> {
                    c.setEstado(EstadoCita.ATENDIDA);
                    citaRepository.save(c);
                });

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        MvcResult resultado = mvc().perform(get("/admin/dashboard").session(sesionAdmin))
                .andExpect(status().isOk())
                .andReturn();

        List<?> productosStockBajo = atributo(resultado, "productosStockBajo");
        List<?> citasVencidas = atributo(resultado, "citasVencidas");
        List<?> pedidosPendientesLista = atributo(resultado, "pedidosPendientesLista");
        assertThat(productosStockBajo).isEmpty();
        assertThat(citasVencidas).isEmpty();
        assertThat(pedidosPendientesLista).isEmpty();
        assertThat((Integer) atributo(resultado, "totalAlertas")).isZero();
    }

    private List<Long> parsearJsonNumeros(String json) {
        String limpio = json.replace("[", "").replace("]", "").trim();
        if (limpio.isEmpty()) {
            return List.of();
        }
        return Stream.of(limpio.split(",")).map(String::trim).map(Long::parseLong).toList();
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

    // ================= CLIENTE =================

    @Test
    void clienteDashboardCalculaTodasLasMetricasIgualQueLaImplementacionAnterior() throws Exception {
        String email = "cliente.dash.equivalencia@test.com";
        MockHttpSession sesionCliente = registrarYLoguearCliente(email);
        Usuario cliente = usuarioRepository.findByEmail(email).orElseThrow();
        Usuario doctor = usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();

        // Cliente recien registrado: sin mascotas ni citas ni pedidos previos,
        // asi que todo lo que aparezca en el dashboard viene de esta prueba.
        Mascota mascotaA = crearMascota(cliente, "MascotaA");
        Mascota mascotaB = crearMascota(cliente, "MascotaB");
        crearMascota(cliente, "MascotaC");

        LocalDateTime ahora = LocalDateTime.now();
        // Dos citas futuras activas (PENDIENTE/CONFIRMADA), una pasada, una
        // cancelada futura (no cuenta como "proxima").
        crearCita(mascotaA, doctor, ahora.plusDays(3), EstadoCita.PENDIENTE, ahora);
        crearCita(mascotaB, doctor, ahora.plusDays(1), EstadoCita.CONFIRMADA, ahora);
        crearCita(mascotaA, doctor, ahora.minusDays(5), EstadoCita.ATENDIDA, ahora.minusDays(6));
        crearCita(mascotaB, doctor, ahora.plusDays(10), EstadoCita.CANCELADA, ahora);
        crearCita(mascotaA, doctor, ahora.minusDays(1), EstadoCita.PENDIENTE, ahora.minusDays(2));

        crearPedido(cliente, EstadoPedido.PENDIENTE, ahora.minusDays(1));
        crearPedido(cliente, EstadoPedido.ENTREGADO, ahora.minusDays(2));

        MvcResult resultado = mvc().perform(get("/cliente/dashboard").session(sesionCliente))
                .andExpect(status().isOk())
                .andReturn();

        List<Mascota> todasLasMascotas = mascotaRepository.findByClienteId(cliente.getId());
        List<Cita> todasLasCitas = citaRepository.findByMascotaClienteIdOrderByFechaHoraDesc(cliente.getId());

        assertThat((Long) atributo(resultado, "totalMascotas")).isEqualTo(todasLasMascotas.size());
        List<Mascota> mascotasReal = atributo(resultado, "mascotas");
        assertThat(mascotasReal).hasSize(Math.min(2, todasLasMascotas.size()));

        List<Cita> futurasActivas = todasLasCitas.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isAfter(ahora))
                .toList();
        assertThat((Long) atributo(resultado, "citasProximas")).isEqualTo(futurasActivas.size());

        Cita proximaEsperada = futurasActivas.stream().min(Comparator.comparing(Cita::getFechaHora)).orElse(null);
        Cita proximaReal = atributo(resultado, "proximaCita");
        if (proximaEsperada == null) {
            assertThat(proximaReal).isNull();
        } else {
            assertThat(proximaReal.getId()).isEqualTo(proximaEsperada.getId());
        }

        assertThat((Long) atributo(resultado, "citasPendientes"))
                .isEqualTo(todasLasCitas.stream().filter(c -> c.getEstado() == EstadoCita.PENDIENTE).count());
        assertThat((Long) atributo(resultado, "totalPedidos"))
                .isEqualTo(pedidoRepository.findByClienteIdOrderByFechaDesc(cliente.getId()).size());

        List<Cita> actividadEsperada = todasLasCitas.stream().limit(4).toList();
        List<Cita> actividadReal = atributo(resultado, "actividadReciente");
        assertThat(actividadReal.stream().map(Cita::getId).toList())
                .containsExactlyElementsOf(actividadEsperada.stream().map(Cita::getId).toList());
    }

    @Test
    void clienteDashboardSinCitasProximasProximaCitaEsNulaYCitasProximasEsCero() throws Exception {
        String email = "cliente.dash.sinproximas@test.com";
        MockHttpSession sesionCliente = registrarYLoguearCliente(email);
        Usuario cliente = usuarioRepository.findByEmail(email).orElseThrow();
        Usuario doctor = usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();
        Mascota mascota = crearMascota(cliente, "MascotaSolitaria");
        LocalDateTime ahora = LocalDateTime.now();
        // Solo una cita, y ya pasada: no cuenta como "proxima".
        crearCita(mascota, doctor, ahora.minusDays(2), EstadoCita.ATENDIDA, ahora.minusDays(3));

        MvcResult resultado = mvc().perform(get("/cliente/dashboard").session(sesionCliente))
                .andExpect(status().isOk())
                .andReturn();

        assertThat((Long) atributo(resultado, "citasProximas")).isZero();
        assertThat((Cita) atributo(resultado, "proximaCita")).isNull();
    }

    // ================= DOCTOR =================

    @Test
    void doctorDashboardCalculaTodasLasMetricasIgualQueLaImplementacionAnterior() throws Exception {
        Usuario doctor = usuarioRepository.findByEmail("lucio@gmail.com").orElseThrow();
        String marcador = "DoctorDashEq";
        Usuario cliente = crearUsuario(marcador + "@test.com", Rol.CLIENTE, true, LocalDateTime.now());
        Mascota mascotaA = crearMascota(cliente, marcador + "-A");
        Mascota mascotaB = crearMascota(cliente, marcador + "-B");

        LocalDateTime ahora = LocalDateTime.now();
        crearCita(mascotaA, doctor, ahora.withHour(9).withMinute(0), EstadoCita.PENDIENTE, ahora);
        crearCita(mascotaB, doctor, ahora.withHour(15).withMinute(0), EstadoCita.CONFIRMADA, ahora);
        crearCita(mascotaA, doctor, ahora.minusDays(3), EstadoCita.ATENDIDA, ahora.minusDays(4));
        crearCita(mascotaB, doctor, ahora.minusDays(1), EstadoCita.PENDIENTE, ahora.minusDays(2));
        crearCita(mascotaA, doctor, ahora.plusDays(5), EstadoCita.CONFIRMADA, ahora);

        MockHttpSession sesionDoctor = loguearComo("lucio@gmail.com", "doctor123");
        MvcResult resultado = mvc().perform(get("/doctor/dashboard").session(sesionDoctor))
                .andExpect(status().isOk())
                .andReturn();

        List<Cita> todasLasCitasDelDoctor = citaRepository.findByDoctorIdOrderByFechaHoraAsc(doctor.getId());
        LocalDate hoy = ahora.toLocalDate();

        List<Cita> citasHoyEsp = todasLasCitasDelDoctor.stream()
                .filter(c -> c.getFechaHora().toLocalDate().equals(hoy))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .toList();
        List<Cita> citasHoyReal = atributo(resultado, "citasHoy");
        assertThat(citasHoyReal.stream().map(Cita::getId).toList())
                .containsExactlyElementsOf(citasHoyEsp.stream().map(Cita::getId).toList());
        assertThat((Integer) atributo(resultado, "totalCitasHoy")).isEqualTo(citasHoyEsp.size());

        assertThat((Long) atributo(resultado, "citasAtendidas")).isEqualTo(
                todasLasCitasDelDoctor.stream().filter(c -> c.getEstado() == EstadoCita.ATENDIDA).count());
        assertThat((Long) atributo(resultado, "citasPendientes")).isEqualTo(todasLasCitasDelDoctor.stream()
                .filter(c -> c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                .count());
        assertThat((Long) atributo(resultado, "totalCitas")).isEqualTo(todasLasCitasDelDoctor.size());

        Cita proximaEsperada = todasLasCitasDelDoctor.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isAfter(ahora))
                .min(Comparator.comparing(Cita::getFechaHora))
                .orElse(null);
        Cita proximaReal = atributo(resultado, "proximaCita");
        assertThat(proximaReal).isNotNull();
        assertThat(proximaReal.getId()).isEqualTo(proximaEsperada.getId());

        List<Cita> citasVencidasEsp = todasLasCitasDelDoctor.stream()
                .filter(c -> (c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                        && c.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Cita::getFechaHora))
                .limit(5)
                .toList();
        List<Cita> citasVencidasReal = atributo(resultado, "citasVencidas");
        assertThat(citasVencidasReal.stream().map(Cita::getId).toList())
                .containsExactlyElementsOf(citasVencidasEsp.stream().map(Cita::getId).toList());

        Set<Long> vistos = new HashSet<>();
        List<Mascota> pacientesEsperados = todasLasCitasDelDoctor.stream()
                .sorted(Comparator.comparing(Cita::getFechaHora).reversed())
                .map(Cita::getMascota)
                .filter(m -> vistos.add(m.getId()))
                .limit(5)
                .toList();
        List<Mascota> pacientesReal = atributo(resultado, "pacientesRecientes");
        assertThat(pacientesReal.stream().map(Mascota::getId).toList())
                .containsExactlyElementsOf(pacientesEsperados.stream().map(Mascota::getId).toList());
    }
}
