package com.luaspets.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.luaspets.model.Cita;
import com.luaspets.model.DetallePedido;
import com.luaspets.model.EstadoCita;
import com.luaspets.model.EstadoMascota;
import com.luaspets.model.EstadoPedido;
import com.luaspets.model.HistorialMedico;
import com.luaspets.model.Mascota;
import com.luaspets.model.Notificacion;
import com.luaspets.model.Pedido;
import com.luaspets.model.Producto;
import com.luaspets.model.Rol;
import com.luaspets.model.Sexo;
import com.luaspets.model.TipoNotificacion;
import com.luaspets.model.Usuario;
import com.luaspets.repository.CitaRepository;
import com.luaspets.repository.HistorialMedicoRepository;
import com.luaspets.repository.MascotaRepository;
import com.luaspets.repository.NotificacionRepository;
import com.luaspets.repository.PedidoRepository;
import com.luaspets.repository.ProductoRepository;
import com.luaspets.repository.UsuarioRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductoRepository productoRepository;
    private final MascotaRepository mascotaRepository;
    private final CitaRepository citaRepository;
    private final HistorialMedicoRepository historialMedicoRepository;
    private final PedidoRepository pedidoRepository;
    private final NotificacionRepository notificacionRepository;

    public DataSeeder(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            ProductoRepository productoRepository, MascotaRepository mascotaRepository,
            CitaRepository citaRepository, HistorialMedicoRepository historialMedicoRepository,
            PedidoRepository pedidoRepository, NotificacionRepository notificacionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.productoRepository = productoRepository;
        this.mascotaRepository = mascotaRepository;
        this.citaRepository = citaRepository;
        this.historialMedicoRepository = historialMedicoRepository;
        this.pedidoRepository = pedidoRepository;
        this.notificacionRepository = notificacionRepository;
    }

    @Override
    public void run(String... args) {
        if (!usuarioRepository.existsByEmail("admin@luaspets.com")) {
            Usuario admin = new Usuario();
            admin.setNombre("Admin");
            admin.setApellido("Luas Pets");
            admin.setEmail("admin@luaspets.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRol(Rol.ADMIN);
            admin.setActivo(true);
            admin.setFechaRegistro(LocalDateTime.now());
            usuarioRepository.save(admin);
        }

        if (!usuarioRepository.existsByEmail("doctor@luaspets.com")) {
            Usuario doctor = new Usuario();
            doctor.setNombre("Carlos");
            doctor.setApellido("Mendoza");
            doctor.setEmail("doctor@luaspets.com");
            doctor.setPassword(passwordEncoder.encode("doctor123"));
            doctor.setRol(Rol.DOCTOR);
            doctor.setActivo(true);
            doctor.setFechaRegistro(LocalDateTime.now());
            usuarioRepository.save(doctor);
        }

        if (productoRepository.count() == 0) {
            productoRepository.save(crearProducto("Alimento para perro adulto",
                    "Bolsa de 15kg de alimento balanceado para perros adultos de todas las razas.",
                    new BigDecimal("189.90"), 30, "Alimento",
                    "/images/alimento-perro.jpg"));

            productoRepository.save(crearProducto("Alimento para gato",
                    "Bolsa de 7.5kg de alimento balanceado para gatos adultos.",
                    new BigDecimal("109.90"), 25, "Alimento",
                    "/images/alimento-gato.jpg"));

            productoRepository.save(crearProducto("Pelota de goma",
                    "Pelota de goma resistente para juego y masticado.",
                    new BigDecimal("15.50"), 50, "Juguetes",
                    "/images/pelota-goma.jpg"));

            productoRepository.save(crearProducto("Hueso de juguete",
                    "Hueso de nylon resistente para masticar, ayuda a la limpieza dental.",
                    new BigDecimal("22.00"), 40, "Juguetes",
                    "/images/hueso-juguete.jpg"));

            productoRepository.save(crearProducto("Shampoo antipulgas",
                    "Shampoo antipulgas y garrapatas de 500ml para perros y gatos.",
                    new BigDecimal("35.90"), 20, "Higiene",
                    "/images/shampoo-antipulgas.jpg"));

            productoRepository.save(crearProducto("Cortaúñas para mascotas",
                    "Cortaúñas de acero inoxidable, ergonómico y seguro.",
                    new BigDecimal("18.00"), 35, "Higiene",
                    "/images/cortaunas.jpg"));

            productoRepository.save(crearProducto("Collar ajustable",
                    "Collar ajustable de nylon con hebilla de seguridad, varias tallas.",
                    new BigDecimal("25.00"), 45, "Accesorios",
                    "/images/collar-ajustable.jpg"));

            productoRepository.save(crearProducto("Comedero doble de acero",
                    "Comedero doble de acero inoxidable con base antideslizante.",
                    new BigDecimal("42.00"), 10, "Accesorios",
                    "/images/comedero-acero.jpg"));
        }

        if (!usuarioRepository.existsByEmail("lucio@gmail.com")) {
            Usuario doctorSecundario = new Usuario();
            doctorSecundario.setNombre("Lucio");
            doctorSecundario.setApellido("Ramírez");
            doctorSecundario.setEmail("lucio@gmail.com");
            doctorSecundario.setPassword(passwordEncoder.encode("doctor123"));
            doctorSecundario.setTelefono("987654321");
            doctorSecundario.setRol(Rol.DOCTOR);
            doctorSecundario.setActivo(true);
            doctorSecundario.setFechaRegistro(LocalDateTime.now());
            usuarioRepository.save(doctorSecundario);
        }

        if (!usuarioRepository.existsByEmail("cliente@luaspets.com")) {
            Usuario clienteDemo = new Usuario();
            clienteDemo.setNombre("Adolfo");
            clienteDemo.setApellido("Vargas");
            clienteDemo.setEmail("cliente@luaspets.com");
            clienteDemo.setPassword(passwordEncoder.encode("cliente123"));
            clienteDemo.setTelefono("912345678");
            clienteDemo.setRol(Rol.CLIENTE);
            clienteDemo.setActivo(true);
            clienteDemo.setFechaRegistro(LocalDateTime.now());
            usuarioRepository.save(clienteDemo);
        }

        // A partir de aquí, los bloques dependen de los usuarios anteriores (recién
        // creados o ya existentes), por eso se releen siempre por email.
        Usuario clienteDemo = usuarioRepository.findByEmail("cliente@luaspets.com").orElse(null);
        Usuario doctorPrincipal = usuarioRepository.findByEmail("doctor@luaspets.com").orElse(null);
        Usuario doctorSecundario = usuarioRepository.findByEmail("lucio@gmail.com").orElse(null);

        Mascota luna = null;
        Mascota michi = null;

        if (mascotaRepository.count() == 0 && clienteDemo != null) {
            luna = new Mascota();
            luna.setNombre("Luna");
            luna.setEspecie("Perro");
            luna.setRaza("Golden Retriever");
            luna.setFechaNacimiento(LocalDate.now().minusYears(3));
            luna.setPeso(new BigDecimal("28.50"));
            luna.setFotoUrl("/images/luna.jpg");
            luna.setCliente(clienteDemo);
            luna.setEstado(EstadoMascota.ACTIVO);
            luna.setSexo(Sexo.HEMBRA);
            luna = mascotaRepository.save(luna);

            michi = new Mascota();
            michi.setNombre("Michi");
            michi.setEspecie("Gato");
            michi.setRaza("Siamés");
            michi.setFechaNacimiento(LocalDate.now().minusYears(2));
            michi.setPeso(new BigDecimal("4.20"));
            michi.setFotoUrl("/images/michi.jpg");
            michi.setCliente(clienteDemo);
            michi.setEstado(EstadoMascota.EN_TRATAMIENTO);
            michi.setSexo(Sexo.MACHO);
            michi.setAlergias("Sensibilidad a la proteína de pollo (dermatitis leve)");
            michi = mascotaRepository.save(michi);
        }

        if (citaRepository.count() == 0 && clienteDemo != null && doctorPrincipal != null
                && doctorSecundario != null) {
            // Si las mascotas ya existían de una siembra anterior (bloque de arriba
            // saltado), se releen por si acaso para no depender de una variable local nula.
            if (luna == null || michi == null) {
                List<Mascota> mascotasCliente = mascotaRepository.findByClienteId(clienteDemo.getId());
                luna = mascotasCliente.stream().filter(m -> "Luna".equals(m.getNombre())).findFirst().orElse(null);
                michi = mascotasCliente.stream().filter(m -> "Michi".equals(m.getNombre())).findFirst().orElse(null);
            }

            if (luna != null && michi != null) {
                LocalDateTime fechaPendiente = LocalDateTime.now().plusDays(3)
                        .withHour(10).withMinute(0).withSecond(0).withNano(0);
                citaRepository.save(crearCita(luna, doctorPrincipal, fechaPendiente,
                        "Vacunación anual", EstadoCita.PENDIENTE));

                LocalDateTime fechaConfirmada = LocalDateTime.now().plusDays(5)
                        .withHour(16).withMinute(0).withSecond(0).withNano(0);
                citaRepository.save(crearCita(michi, doctorSecundario, fechaConfirmada,
                        "Control de peso", EstadoCita.CONFIRMADA));

                LocalDateTime fechaAtendida = LocalDateTime.now().minusDays(10)
                        .withHour(9).withMinute(0).withSecond(0).withNano(0);
                Cita citaAtendida = citaRepository.save(crearCita(luna, doctorSecundario, fechaAtendida,
                        "Revisión general", EstadoCita.ATENDIDA));

                LocalDateTime fechaCancelada = LocalDateTime.now().minusDays(4)
                        .withHour(11).withMinute(0).withSecond(0).withNano(0);
                citaRepository.save(crearCita(michi, doctorPrincipal, fechaCancelada,
                        "Consulta por alergia", EstadoCita.CANCELADA));

                if (historialMedicoRepository.count() == 0) {
                    HistorialMedico historial = new HistorialMedico();
                    historial.setCita(citaAtendida);
                    historial.setDiagnostico("Mascota en buen estado general, sin signos de enfermedad.");
                    historial.setTratamiento(
                            "No se requiere tratamiento; continuar con alimentación y ejercicio habituales.");
                    historial.setObservaciones("Se recomienda control de rutina en 6 meses.");
                    historial.setFecha(fechaAtendida);
                    historial.setPesoRegistrado(new BigDecimal("28.30"));
                    historial.setMedicamentos(
                            "Vitamina C masticable — 1 tableta, 1 vez al día por 10 días\n"
                                    + "Suplemento articular — 1 sobre con la comida, 1 vez al día por 30 días");
                    historialMedicoRepository.save(historial);
                }
            }
        }

        if (pedidoRepository.count() == 0 && clienteDemo != null) {
            Producto alimentoGato = productoRepository
                    .findByNombreContainingIgnoreCaseAndActivoTrue("Alimento para gato")
                    .stream().findFirst().orElse(null);
            Producto collar = productoRepository
                    .findByNombreContainingIgnoreCaseAndActivoTrue("Collar ajustable")
                    .stream().findFirst().orElse(null);

            if (alimentoGato != null && collar != null) {
                Pedido pedido = new Pedido();
                pedido.setCliente(clienteDemo);
                pedido.setFecha(LocalDateTime.now().minusDays(7));
                pedido.setEstado(EstadoPedido.ENTREGADO);

                DetallePedido detalleAlimento = new DetallePedido();
                detalleAlimento.setPedido(pedido);
                detalleAlimento.setProducto(alimentoGato);
                detalleAlimento.setCantidad(1);
                detalleAlimento.setPrecioUnitario(alimentoGato.getPrecio());

                DetallePedido detalleCollar = new DetallePedido();
                detalleCollar.setPedido(pedido);
                detalleCollar.setProducto(collar);
                detalleCollar.setCantidad(2);
                detalleCollar.setPrecioUnitario(collar.getPrecio());

                pedido.getDetalles().add(detalleAlimento);
                pedido.getDetalles().add(detalleCollar);

                BigDecimal total = detalleAlimento.getSubtotal().add(detalleCollar.getSubtotal());
                pedido.setTotal(total);

                pedidoRepository.save(pedido);
            }
        }

        // Backfill: cualquier mascota preexistente (de antes de que este campo
        // existiera) queda sin estado; se normaliza a ACTIVO. Idempotente: en
        // corridas siguientes no habrá ninguna con estado null.
        List<Mascota> mascotasSinEstado = mascotaRepository.findByEstadoIsNull();
        if (!mascotasSinEstado.isEmpty()) {
            for (Mascota mascota : mascotasSinEstado) {
                mascota.setEstado(EstadoMascota.ACTIVO);
            }
            mascotaRepository.saveAll(mascotasSinEstado);
        }

        // Idempotente: cualquier producto sin imagen, o con una URL externa antigua
        // (placehold.co o unsplash.com), se migra a su fotografia local por nombre.
        // En corridas siguientes no quedara ninguno en ese estado.
        List<Producto> productosPorMigrar = productoRepository.findAll().stream()
                .filter(p -> p.getImagenUrl() == null || p.getImagenUrl().isBlank()
                        || p.getImagenUrl().contains("placehold.co") || p.getImagenUrl().contains("unsplash.com"))
                .toList();
        for (Producto producto : productosPorMigrar) {
            String nuevaImagen = imagenPorNombreProducto(producto.getNombre());
            if (nuevaImagen != null) {
                producto.setImagenUrl(nuevaImagen);
                productoRepository.save(producto);
            }
        }

        // Idempotente: cualquier mascota con fotoUrl de Unsplash se migra a su
        // fotografia local si es Luna o Michi; el resto queda en null para que la
        // vista aplique el fallback por especie (ImagenUtil). En corridas
        // siguientes no quedara ninguna mascota con ese dominio.
        List<Mascota> mascotasConUnsplash = mascotaRepository.findAll().stream()
                .filter(m -> m.getFotoUrl() != null && m.getFotoUrl().contains("unsplash.com"))
                .toList();
        for (Mascota mascota : mascotasConUnsplash) {
            mascota.setFotoUrl(rutaLocalPorNombreMascota(mascota.getNombre()));
        }
        if (!mascotasConUnsplash.isEmpty()) {
            mascotaRepository.saveAll(mascotasConUnsplash);
        }

        // Datos de demostracion para que la campana de notificaciones no se vea
        // vacia en un despliegue nuevo. Idempotente: solo se ejecuta si la tabla
        // esta completamente vacia.
        if (notificacionRepository.count() == 0) {
            Usuario clienteParaNotificar = usuarioRepository.findByEmail("cliente@luaspets.com").orElse(null);
            Usuario doctorParaNotificar = usuarioRepository.findByEmail("doctor@luaspets.com").orElse(null);
            Usuario adminParaNotificar = usuarioRepository.findByEmail("admin@luaspets.com").orElse(null);

            if (clienteParaNotificar != null) {
                notificacionRepository.save(Notificacion.builder()
                        .destinatario(clienteParaNotificar)
                        .tipo(TipoNotificacion.CITA_CONFIRMADA)
                        .titulo("Tu cita fue confirmada")
                        .mensaje("La cita de Luna del "
                                + LocalDateTime.now().plusDays(3).withHour(10).withMinute(0)
                                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                + " ha sido confirmada.")
                        .url("/cliente/citas")
                        .leida(true)
                        .fechaCreacion(LocalDateTime.now().minusHours(20))
                        .build());

                notificacionRepository.save(Notificacion.builder()
                        .destinatario(clienteParaNotificar)
                        .tipo(TipoNotificacion.CITA_ATENDIDA)
                        .titulo("Historial médico disponible")
                        .mensaje("Luna fue atendida por Dr. Carlos Mendoza. Ya puedes consultar el diagnóstico.")
                        .url("/cliente/mascotas")
                        .leida(false)
                        .fechaCreacion(LocalDateTime.now().minusHours(2))
                        .build());
            }

            if (doctorParaNotificar != null) {
                notificacionRepository.save(Notificacion.builder()
                        .destinatario(doctorParaNotificar)
                        .tipo(TipoNotificacion.CITA_AGENDADA)
                        .titulo("Nueva cita agendada")
                        .mensaje("Michi tiene una cita próximamente. Motivo: Control de peso")
                        .url("/doctor/citas")
                        .leida(false)
                        .fechaCreacion(LocalDateTime.now().minusHours(5))
                        .build());
            }

            if (adminParaNotificar != null) {
                notificacionRepository.save(Notificacion.builder()
                        .destinatario(adminParaNotificar)
                        .tipo(TipoNotificacion.CLIENTE_NUEVO)
                        .titulo("Nuevo cliente registrado")
                        .mensaje("Adolfo Vargas creó una cuenta.")
                        .url("/admin/mascotas")
                        .leida(false)
                        .fechaCreacion(LocalDateTime.now().minusHours(1))
                        .build());
            }
        }
    }

    private String imagenPorNombreProducto(String nombre) {
        return switch (nombre) {
            case "Alimento para perro adulto" -> "/images/alimento-perro.jpg";
            case "Alimento para gato" -> "/images/alimento-gato.jpg";
            case "Pelota de goma" -> "/images/pelota-goma.jpg";
            case "Hueso de juguete" -> "/images/hueso-juguete.jpg";
            case "Shampoo antipulgas" -> "/images/shampoo-antipulgas.jpg";
            case "Cortaúñas para mascotas" -> "/images/cortaunas.jpg";
            case "Collar ajustable" -> "/images/collar-ajustable.jpg";
            case "Comedero doble de acero" -> "/images/comedero-acero.jpg";
            default -> null;
        };
    }

    private String rutaLocalPorNombreMascota(String nombre) {
        return switch (nombre) {
            case "Luna" -> "/images/luna.jpg";
            case "Michi" -> "/images/michi.jpg";
            default -> null;
        };
    }

    private Producto crearProducto(String nombre, String descripcion, BigDecimal precio, Integer stock,
            String categoria, String imagenUrl) {
        Producto producto = new Producto();
        producto.setNombre(nombre);
        producto.setDescripcion(descripcion);
        producto.setPrecio(precio);
        producto.setStock(stock);
        producto.setCategoria(categoria);
        producto.setImagenUrl(imagenUrl);
        producto.setActivo(true);
        return producto;
    }

    private Cita crearCita(Mascota mascota, Usuario doctor, LocalDateTime fechaHora, String motivo,
            EstadoCita estado) {
        Cita cita = new Cita();
        cita.setMascota(mascota);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaHora);
        cita.setMotivo(motivo);
        cita.setEstado(estado);
        cita.setFechaCreacion(fechaHora.minusDays(2));
        return cita;
    }
}
