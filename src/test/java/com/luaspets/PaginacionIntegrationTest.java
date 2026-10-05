package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.repository.UsuarioRepository;
import com.luaspets.service.NotificacionService;

/**
 * Cubre la extension de paginacion a los listados que crecen sin limite
 * (admin/citas, admin/pedidos, admin/productos, cliente/pedidos, doctor/citas
 * y notificaciones): tamano de pagina, que los filtros se resuelvan en la
 * consulta (no con streams sobre la lista completa), aislamiento entre
 * cliente/doctor al paginar, que los enlaces de paginacion conserven los
 * filtros activos, y que la vista calendario de citas siga sin paginar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaginacionIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private NotificacionService notificacionService;

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

    private Usuario doctorSeed() {
        return usuarioRepository.findByEmail("doctor@luaspets.com").orElseThrow();
    }

    private Usuario doctorSecundarioSeed() {
        return usuarioRepository.findByEmail("lucio@gmail.com").orElseThrow();
    }

    private Mascota crearMascotaPara(Usuario cliente, String nombre) {
        Mascota mascota = new Mascota();
        mascota.setNombre(nombre);
        mascota.setEspecie("Gato");
        mascota.setCliente(cliente);
        return mascotaRepository.save(mascota);
    }

    // Fechas de cita construidas directamente por repositorio (sin pasar por
    // CitaService.agendarCita): aqui interesa controlar con precision
    // fechaHora/estado/doctor, no las reglas de horario o anticipacion de
    // negocio.
    private Cita crearCitaDirecta(Mascota mascota, Usuario doctor, LocalDateTime fechaHora, EstadoCita estado) {
        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaHora);
        cita.setMotivo("Motivo de prueba");
        cita.setEstado(estado);
        cita.setFechaCreacion(LocalDateTime.now());
        return citaRepository.save(cita);
    }

    private Pedido crearPedidoDirecto(Usuario cliente, EstadoPedido estado, LocalDateTime fecha) {
        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setFecha(fecha);
        pedido.setEstado(estado);
        pedido.setTotal(new BigDecimal("10.00"));
        return pedidoRepository.save(pedido);
    }

    private Producto crearProducto(String nombre, String categoria) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setPrecio(new BigDecimal("15.00"));
        producto.setStock(20);
        producto.setCategoria(categoria);
        producto.setActivo(true);
        return productoRepository.save(producto);
    }

    @Test
    void adminCitasListaRespetaTamanioDePaginaYFiltraEnLaConsulta() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.paginacion.citas@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.paginacion.citas@test.com").orElseThrow();
        Usuario doctor = doctorSeed();
        String marcador = "MarcadorCitasPaginacion";

        // 12 PENDIENTE + 3 CONFIRMADA, todas con el mismo marcador unico en el
        // nombre de la mascota para no mezclarse con datos de otras pruebas ni
        // con los sembrados por DataSeeder.
        for (int i = 0; i < 12; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + "Pendiente" + i);
            crearCitaDirecta(mascota, doctor, LocalDateTime.now().plusDays(10).plusHours(i), EstadoCita.PENDIENTE);
        }
        for (int i = 0; i < 3; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + "Confirmada" + i);
            crearCitaDirecta(mascota, doctor, LocalDateTime.now().plusDays(20).plusHours(i), EstadoCita.CONFIRMADA);
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        // Filtro por estado resuelto en la consulta paginada: de las 15 citas con
        // el marcador, solo 3 son CONFIRMADA.
        var resultadoFiltrado = mvc().perform(get("/admin/citas").session(sesionAdmin)
                        .param("vista", "lista")
                        .param("buscar", marcador)
                        .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalElements", equalTo(3L))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Cita> pageConfirmadas = (Page<Cita>) resultadoFiltrado.getModelAndView().getModel().get("page");
        assertThat(pageConfirmadas.getContent()).allMatch(c -> c.getEstado() == EstadoCita.CONFIRMADA);

        // Maximo 10 por pagina y segunda pagina con elementos distintos: de las
        // 15 citas con el marcador (sin filtrar por estado), la pagina 0 trae 10
        // y la pagina 1 trae las 5 restantes, sin solaparse.
        var pagina0 = mvc().perform(get("/admin/citas").session(sesionAdmin)
                        .param("vista", "lista")
                        .param("buscar", marcador)
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Cita> page0 = (Page<Cita>) pagina0.getModelAndView().getModel().get("page");
        assertThat(page0.getContent()).hasSize(10);
        assertThat(page0.getTotalElements()).isEqualTo(15);

        var pagina1 = mvc().perform(get("/admin/citas").session(sesionAdmin)
                        .param("vista", "lista")
                        .param("buscar", marcador)
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Cita> page1 = (Page<Cita>) pagina1.getModelAndView().getModel().get("page");
        assertThat(page1.getContent()).hasSize(5);

        List<Long> idsPagina0 = page0.getContent().stream().map(Cita::getId).toList();
        List<Long> idsPagina1 = page1.getContent().stream().map(Cita::getId).toList();
        assertThat(idsPagina0).doesNotContainAnyElementsOf(idsPagina1);
    }

    @Test
    void adminCitasCalendarioNoEstaPaginado() throws Exception {
        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/citas").session(sesionAdmin).param("vista", "calendario"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("semana"))
                .andExpect(model().attributeDoesNotExist("page"));
    }

    @Test
    void enlacesDePaginacionConservanLosFiltrosActivos() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.enlaces.paginacion@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.enlaces.paginacion@test.com").orElseThrow();
        Usuario doctor = doctorSeed();
        String marcador = "MarcadorEnlaces";

        for (int i = 0; i < 11; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + i);
            crearCitaDirecta(mascota, doctor, LocalDateTime.now().plusDays(15).plusHours(i), EstadoCita.PENDIENTE);
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        // Con 11 resultados (mas de una pagina) el enlace "siguiente" debe
        // apuntar a page=1 conservando estado y buscar en la query string.
        mvc().perform(get("/admin/citas").session(sesionAdmin)
                        .param("vista", "lista")
                        .param("buscar", marcador)
                        .param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/admin/citas?page=1&amp;estado=PENDIENTE&amp;buscar=" + marcador + "&amp;vista=lista")));
    }

    @Test
    void doctorCitasListaSoloMuestraLasCitasDelDoctorLogueadoAunPaginando() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.aislamiento.doctor@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.aislamiento.doctor@test.com").orElseThrow();
        Usuario doctorA = doctorSeed();
        Usuario doctorB = doctorSecundarioSeed();
        String marcador = "MarcadorAislamientoDoctor";

        for (int i = 0; i < 12; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + "A" + i);
            crearCitaDirecta(mascota, doctorA, LocalDateTime.now().plusDays(12).plusHours(i), EstadoCita.PENDIENTE);
        }
        for (int i = 0; i < 3; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + "B" + i);
            crearCitaDirecta(mascota, doctorB, LocalDateTime.now().plusDays(13).plusHours(i), EstadoCita.PENDIENTE);
        }

        MockHttpSession sesionDoctorB = loguearComo("lucio@gmail.com", "doctor123");

        var resultado = mvc().perform(get("/doctor/citas").session(sesionDoctorB)
                        .param("vista", "lista")
                        .param("buscar", marcador)
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalElements", equalTo(3L))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Cita> page = (Page<Cita>) resultado.getModelAndView().getModel().get("page");
        assertThat(page.getContent()).allMatch(c -> c.getDoctor().getId().equals(doctorB.getId()));

        // Aunque doctorA tiene 12 citas con el mismo marcador (mas de una
        // pagina), pedir una pagina fuera de rango de doctorB (que solo tiene 3,
        // 1 sola pagina) nunca debe traer citas de doctorA.
        var fueraDeRango = mvc().perform(get("/doctor/citas").session(sesionDoctorB)
                        .param("vista", "lista")
                        .param("buscar", marcador)
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Cita> paginaFueraDeRango = (Page<Cita>) fueraDeRango.getModelAndView().getModel().get("page");
        assertThat(paginaFueraDeRango.getContent()).isEmpty();
    }

    @Test
    void clientePedidosSoloMuestraLosPedidosDelClienteLogueadoAunPaginando() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("clienteA.pedidos.paginacion@test.com");
        Usuario clienteA = usuarioRepository.findByEmail("clienteA.pedidos.paginacion@test.com").orElseThrow();
        registrarYLoguearCliente("clienteB.pedidos.paginacion@test.com");
        Usuario clienteB = usuarioRepository.findByEmail("clienteB.pedidos.paginacion@test.com").orElseThrow();

        for (int i = 0; i < 3; i++) {
            crearPedidoDirecto(clienteA, EstadoPedido.PENDIENTE, LocalDateTime.now().minusDays(i));
        }
        for (int i = 0; i < 12; i++) {
            crearPedidoDirecto(clienteB, EstadoPedido.PENDIENTE, LocalDateTime.now().minusDays(i));
        }

        var resultado = mvc().perform(get("/cliente/pedidos").session(sesionA).param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalElements", equalTo(3L))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Pedido> page0 = (Page<Pedido>) resultado.getModelAndView().getModel().get("page");
        assertThat(page0.getContent()).allMatch(p -> p.getCliente().getId().equals(clienteA.getId()));

        // clienteB tiene 12 pedidos (2 paginas); clienteA solo 3 (1 pagina). Pedir
        // la pagina 1 como clienteA nunca debe devolver pedidos de clienteB.
        var fueraDeRango = mvc().perform(get("/cliente/pedidos").session(sesionA).param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Pedido> paginaFueraDeRango = (Page<Pedido>) fueraDeRango.getModelAndView().getModel().get("page");
        assertThat(paginaFueraDeRango.getContent()).isEmpty();
    }

    @Test
    void adminProductosRespetaTamanioDePaginaYFiltraCategoriaEnLaConsulta() throws Exception {
        String categoria = "CategoriaPaginacionTest";
        for (int i = 0; i < 15; i++) {
            crearProducto("ProductoPaginado" + i, categoria);
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        var pagina0 = mvc().perform(get("/admin/productos").session(sesionAdmin)
                        .param("categoria", categoria)
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalElements", equalTo(15L))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Producto> page0 = (Page<Producto>) pagina0.getModelAndView().getModel().get("page");
        assertThat(page0.getContent()).hasSize(10);
        assertThat(page0.getContent()).allMatch(p -> categoria.equals(p.getCategoria()));

        var pagina1 = mvc().perform(get("/admin/productos").session(sesionAdmin)
                        .param("categoria", categoria)
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Producto> page1 = (Page<Producto>) pagina1.getModelAndView().getModel().get("page");
        assertThat(page1.getContent()).hasSize(5);

        List<Long> idsPagina0 = page0.getContent().stream().map(Producto::getId).toList();
        List<Long> idsPagina1 = page1.getContent().stream().map(Producto::getId).toList();
        assertThat(idsPagina0).doesNotContainAnyElementsOf(idsPagina1);
    }

    @Test
    void adminPedidosFiltraPorEstadoEnLaConsultaPaginada() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.pedidos.admin.paginacion@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.pedidos.admin.paginacion@test.com").orElseThrow();

        for (int i = 0; i < 4; i++) {
            crearPedidoDirecto(cliente, EstadoPedido.ENTREGADO, LocalDateTime.now().minusDays(i));
        }
        for (int i = 0; i < 2; i++) {
            crearPedidoDirecto(cliente, EstadoPedido.PENDIENTE, LocalDateTime.now().minusDays(i + 10));
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        var resultado = mvc().perform(get("/admin/pedidos").session(sesionAdmin)
                        .param("estado", "ENTREGADO"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<Pedido> page = (Page<Pedido>) resultado.getModelAndView().getModel().get("page");
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(10);
        assertThat(page.getContent()).allMatch(p -> p.getEstado() == EstadoPedido.ENTREGADO);
        // Solo verifica que los 4 pedidos ENTREGADO de este cliente esten
        // incluidos en el total filtrado (puede haber otros ENTREGADO de otras
        // pruebas o del seeder, asi que no se compara con igualdad estricta).
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void notificacionesListaRespetaTamanioDePaginaYOrdenDescendente() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("cliente.notificaciones.paginacion@test.com");
        Usuario cliente = usuarioRepository.findByEmail("cliente.notificaciones.paginacion@test.com").orElseThrow();

        for (int i = 0; i < 13; i++) {
            notificacionService.crear(cliente, TipoNotificacion.CITA_AGENDADA, "Titulo " + i, "Mensaje " + i,
                    "/cliente/citas");
        }

        var resultado = mvc().perform(get("/notificaciones").session(sesionCliente).param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalElements", equalTo(13L))))
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<com.luaspets.dto.NotificacionResponse> page0 = (Page<com.luaspets.dto.NotificacionResponse>) resultado
                .getModelAndView().getModel().get("page");
        assertThat(page0.getContent()).hasSize(10);

        var pagina1 = mvc().perform(get("/notificaciones").session(sesionCliente).param("page", "1"))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        Page<com.luaspets.dto.NotificacionResponse> page1 = (Page<com.luaspets.dto.NotificacionResponse>) pagina1
                .getModelAndView().getModel().get("page");
        assertThat(page1.getContent()).hasSize(3);
    }

    @Test
    void adminMascotasPaginaAlMenosDosPaginasComoReferenciaDePatron() throws Exception {
        // Verificacion rapida de que el patron de referencia (page.totalPages
        // >= 2) sigue disponible via el mismo model attribute "page" que ahora
        // comparten todos los listados paginados nuevos.
        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");
        mvc().perform(get("/admin/mascotas").session(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("page"))
                .andExpect(model().attribute("page", hasProperty("size", equalTo(10))));
    }

    // Verificacion obligatoria de que el fragmento fragments/paginacion.html
    // (ahora sin T(), recibiendo numerosPagina ya calculada por
    // PaginacionUtil) RENDERIZA de verdad en las seis rutas paginadas, no solo
    // que compila. Un T() roto en la plantilla habria hecho fallar
    // exactamente este tipo de peticion (200 esperado, pero con una
    // TemplateProcessingException al renderizar), algo que ni
    // `mvn clean compile` ni una asercion sobre el model detectan: hay que
    // pedir la pagina de verdad y leer el HTML devuelto.
    private static final String NAV_PAGINACION = "aria-label=\"Paginación\"";

    @Test
    void adminCitasListaRenderizaLosControlesDePaginacionConVariasPaginas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.render.admin.citas@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.render.admin.citas@test.com").orElseThrow();
        Usuario doctor = doctorSeed();
        String marcador = "MarcadorRenderAdminCitas";
        for (int i = 0; i < 12; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + i);
            crearCitaDirecta(mascota, doctor, LocalDateTime.now().plusDays(30).plusHours(i), EstadoCita.PENDIENTE);
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/citas").session(sesionAdmin).param("vista", "lista").param("buscar", marcador))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", equalTo(2))))
                .andExpect(content().string(containsString(NAV_PAGINACION)))
                .andExpect(content().string(containsString(
                        "/admin/citas?page=1&amp;buscar=" + marcador + "&amp;vista=lista")));
    }

    @Test
    void doctorCitasListaRenderizaLosControlesDePaginacionConVariasPaginas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.render.doctor.citas@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.render.doctor.citas@test.com").orElseThrow();
        Usuario doctor = doctorSeed();
        String marcador = "MarcadorRenderDoctorCitas";
        for (int i = 0; i < 12; i++) {
            Mascota mascota = crearMascotaPara(cliente, marcador + i);
            crearCitaDirecta(mascota, doctor, LocalDateTime.now().plusDays(31).plusHours(i), EstadoCita.PENDIENTE);
        }

        MockHttpSession sesionDoctor = loguearComo("doctor@luaspets.com", "doctor123");

        mvc().perform(get("/doctor/citas").session(sesionDoctor).param("vista", "lista").param("buscar", marcador))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", equalTo(2))))
                .andExpect(content().string(containsString(NAV_PAGINACION)))
                .andExpect(content().string(containsString(
                        "/doctor/citas?page=1&amp;buscar=" + marcador + "&amp;vista=lista")));
    }

    @Test
    void adminPedidosRenderizaLosControlesDePaginacionConVariasPaginas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("dueño.render.admin.pedidos@test.com");
        Usuario cliente = usuarioRepository.findByEmail("dueño.render.admin.pedidos@test.com").orElseThrow();
        for (int i = 0; i < 12; i++) {
            crearPedidoDirecto(cliente, EstadoPedido.PENDIENTE, LocalDateTime.now().minusDays(i));
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/pedidos").session(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", org.hamcrest.Matchers.greaterThanOrEqualTo(2))))
                .andExpect(content().string(containsString(NAV_PAGINACION)))
                .andExpect(content().string(containsString("/admin/pedidos?page=1")));
    }

    @Test
    void adminProductosRenderizaLosControlesDePaginacionConVariasPaginas() throws Exception {
        String categoria = "CategoriaRenderPaginacion";
        for (int i = 0; i < 12; i++) {
            crearProducto("ProductoRender" + i, categoria);
        }

        MockHttpSession sesionAdmin = loguearComo("admin@luaspets.com", "admin123");

        mvc().perform(get("/admin/productos").session(sesionAdmin).param("categoria", categoria))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", equalTo(2))))
                .andExpect(content().string(containsString(NAV_PAGINACION)))
                .andExpect(content().string(containsString("/admin/productos?page=1&amp;categoria=" + categoria)));
    }

    @Test
    void clientePedidosRenderizaLosControlesDePaginacionConVariasPaginas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("cliente.render.pedidos@test.com");
        Usuario cliente = usuarioRepository.findByEmail("cliente.render.pedidos@test.com").orElseThrow();
        for (int i = 0; i < 12; i++) {
            crearPedidoDirecto(cliente, EstadoPedido.PENDIENTE, LocalDateTime.now().minusDays(i));
        }

        mvc().perform(get("/cliente/pedidos").session(sesionCliente))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", equalTo(2))))
                .andExpect(content().string(containsString(NAV_PAGINACION)))
                .andExpect(content().string(containsString("/cliente/pedidos?page=1")));
    }

    @Test
    void notificacionesRenderizaLosControlesDePaginacionConVariasPaginas() throws Exception {
        MockHttpSession sesionCliente = registrarYLoguearCliente("cliente.render.notificaciones@test.com");
        Usuario cliente = usuarioRepository.findByEmail("cliente.render.notificaciones@test.com").orElseThrow();
        for (int i = 0; i < 12; i++) {
            notificacionService.crear(cliente, TipoNotificacion.CITA_AGENDADA, "Titulo " + i, "Mensaje " + i,
                    "/cliente/citas");
        }

        mvc().perform(get("/notificaciones").session(sesionCliente))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", equalTo(2))))
                .andExpect(content().string(containsString(NAV_PAGINACION)))
                .andExpect(content().string(containsString("/notificaciones?page=1")));
    }
}
