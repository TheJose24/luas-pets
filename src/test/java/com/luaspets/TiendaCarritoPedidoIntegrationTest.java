package com.luaspets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.luaspets.model.Pedido;
import com.luaspets.model.Producto;
import com.luaspets.model.Usuario;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.repository.UsuarioRepository;

/**
 * Cubre: agregar al carrito (contador y total), confirmar pedido (persistencia,
 * detalles y descuento de stock), rechazo por stock insuficiente, y aislamiento
 * entre clientes al ver pedidos (403).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TiendaCarritoPedidoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

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

    private Producto crearProducto(String nombre, String precio, int stock) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion("Producto de prueba");
        producto.setPrecio(new BigDecimal(precio));
        producto.setStock(stock);
        producto.setCategoria("Accesorios");
        producto.setImagenUrl("/images/collar-ajustable.jpg");
        producto.setActivo(true);
        return productoRepository.save(producto);
    }

    @Test
    void agregarProductoAlCarritoActualizaContadorYTotal() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("carrito.contador@test.com");
        Producto producto = crearProducto("Correa para perro", "25.00", 10);

        mvc().perform(post("/cliente/tienda/agregar").with(csrf()).session(session)
                        .param("productoId", producto.getId().toString())
                        .param("cantidad", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/tienda"));

        mvc().perform(get("/cliente/tienda").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("itemsCarrito", 3));

        mvc().perform(get("/cliente/carrito").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("total", new BigDecimal("75.00")));
    }

    @Test
    void confirmarPedidoPersisteDetallesYDescuentaStock() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("confirma.pedido@test.com");
        Usuario cliente = usuarioRepository.findByEmail("confirma.pedido@test.com").orElseThrow();
        Producto producto = crearProducto("Shampoo para gatos", "30.00", 10);

        mvc().perform(post("/cliente/tienda/agregar").with(csrf()).session(session)
                .param("productoId", producto.getId().toString())
                .param("cantidad", "4"));

        mvc().perform(post("/cliente/carrito/confirmar").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/pedidos?exito"));

        var pedidos = pedidoRepository.findByClienteIdOrderByFechaDesc(cliente.getId());
        assertThat(pedidos).hasSize(1);
        Pedido pedido = pedidos.get(0);
        assertThat(pedido.getDetalles()).hasSize(1);
        assertThat(pedido.getDetalles().get(0).getCantidad()).isEqualTo(4);
        assertThat(pedido.getDetalles().get(0).getPrecioUnitario()).isEqualByComparingTo("30.00");
        assertThat(pedido.getTotal()).isEqualByComparingTo("120.00");

        Producto productoActualizado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(productoActualizado.getStock()).isEqualTo(6);
    }

    @Test
    void confirmarPedidoConCantidadMayorAlStockEsRechazado() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("stock.insuficiente@test.com");
        Usuario cliente = usuarioRepository.findByEmail("stock.insuficiente@test.com").orElseThrow();
        Producto producto = crearProducto("Juguete escaso", "15.00", 5);

        mvc().perform(post("/cliente/tienda/agregar").with(csrf()).session(session)
                .param("productoId", producto.getId().toString())
                .param("cantidad", "5"));

        producto.setStock(2);
        productoRepository.save(producto);

        mvc().perform(post("/cliente/carrito/confirmar").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente/carrito"))
                .andExpect(flash().attribute("error", "Stock insuficiente para el producto: Juguete escaso"));

        assertThat(pedidoRepository.findByClienteIdOrderByFechaDesc(cliente.getId())).isEmpty();
        assertThat(productoRepository.findById(producto.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void clienteNoPuedeVerElPedidoDeOtroCliente() throws Exception {
        MockHttpSession sesionA = registrarYLoguearCliente("comprador.a@test.com");
        Producto producto = crearProducto("Cama para mascota", "80.00", 10);

        mvc().perform(post("/cliente/tienda/agregar").with(csrf()).session(sesionA)
                .param("productoId", producto.getId().toString())
                .param("cantidad", "1"));
        mvc().perform(post("/cliente/carrito/confirmar").with(csrf()).session(sesionA));

        Usuario clienteA = usuarioRepository.findByEmail("comprador.a@test.com").orElseThrow();
        Pedido pedidoDeA = pedidoRepository.findByClienteIdOrderByFechaDesc(clienteA.getId()).get(0);

        MockHttpSession sesionB = registrarYLoguearCliente("comprador.b@test.com");

        mvc().perform(get("/cliente/pedidos/{id}", pedidoDeA.getId()).session(sesionB))
                .andExpect(status().isForbidden());
    }

    @Test
    void agregarPorApiConCantidadMayorAlStockNoModificaElCarrito() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("carrito.api.stock@test.com");
        Producto producto = crearProducto("Correa reforzada", "40.00", 3);

        mvc().perform(post("/cliente/api/carrito/agregar").with(csrf()).session(session)
                        .param("productoId", producto.getId().toString())
                        .param("cantidad", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.mensaje").value("Solo quedan 3 unidades de Correa reforzada"))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.vacio").value(true));

        mvc().perform(get("/cliente/api/carrito").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.vacio").value(true));
    }

    @Test
    void agregarPorApiSinTokenCsrfEsRechazadoCon403() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("carrito.api.csrf@test.com");
        Producto producto = crearProducto("Plato antideslizante", "20.00", 10);

        mvc().perform(post("/cliente/api/carrito/agregar").session(session)
                        .param("productoId", producto.getId().toString())
                        .param("cantidad", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoYListaDePedidosSeRenderizanCorrectamente() throws Exception {
        MockHttpSession session = registrarYLoguearCliente("render.tienda@test.com");

        mvc().perform(get("/cliente/tienda").session(session))
                .andExpect(status().isOk());

        mvc().perform(get("/cliente/tienda").session(session)
                        .param("categoria", "Alimento")
                        .param("orden", "precio_desc")
                        .param("buscar", "alimento"))
                .andExpect(status().isOk());

        mvc().perform(get("/cliente/pedidos").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void doctorRecibe403AlAccederALaApiDeCarritoDelCliente() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc().perform(post("/login").with(csrf()).session(session)
                .param("username", "doctor@luaspets.com")
                .param("password", "doctor123"));

        mvc().perform(get("/cliente/api/carrito").session(session))
                .andExpect(status().isForbidden());
    }
}
