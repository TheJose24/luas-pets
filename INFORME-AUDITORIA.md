# Informe de Auditoría — Luas Pets

**Fecha:** 2026-09-01
**Alcance:** Auditoría funcional y de usabilidad completa, con diagnóstico, corrección y verificación.
**Metodología:** Inspección de código + ejecución real de la aplicación (perfil `local` contra MySQL, y variantes con `spring.jpa.open-in-view=false`) probada con `curl` simulando el navegador (sesión, CSRF, cookies), más una suite de pruebas automatizadas con MockMvc + H2.

---

## 1. Funcionalidades verificadas

| Funcionalidad | Cómo se verificó |
|---|---|
| Registro de cliente + login exitoso | Prueba automatizada (`AuthenticationIntegrationTest`) + HTTP real |
| Login fallido con credenciales incorrectas (`/login?error`) | Prueba automatizada + HTTP real |
| Acceso a ruta protegida sin autenticar → redirect a `/login` | Prueba automatizada + HTTP real |
| Cliente autenticado recibe 403 en `/admin/dashboard` y `/doctor/citas` | Prueba automatizada + HTTP real |
| Logo "Luas Pets" redirige al dashboard del rol si hay sesión activa | HTTP real (`GET /` autenticado → `Location: /cliente/dashboard`, `/admin/dashboard`, etc.) |
| Registrar mascota y asociación correcta al cliente | Prueba automatizada + HTTP real |
| Agendar cita (caso feliz) | Prueba automatizada + HTTP real |
| Rechazo de doble cita del mismo doctor en el mismo horario | Prueba automatizada (mensaje de negocio exacto verificado) |
| Reprogramar cita (solo PENDIENTE/CONFIRMADA) | Prueba automatizada + HTTP real |
| Rechazo de cancelación sobre cita ATENDIDA | Prueba automatizada |
| Registrar historial médico → cita pasa a ATENDIDA | Prueba automatizada + **HTTP real** (este flujo estaba roto, ver sección 2) |
| Cliente no puede ver/reprogramar cita de otro cliente (403) | Prueba automatizada |
| Agregar producto al carrito (contador y total correctos) | Prueba automatizada + HTTP real |
| Confirmar pedido: persistencia, detalles y descuento de stock | Prueba automatizada + HTTP real |
| Rechazo de pedido con cantidad mayor al stock disponible | Prueba automatizada |
| Cliente no puede ver pedido de otro cliente (403) | Prueba automatizada |
| Listados de citas/pedidos (admin, doctor, cliente) sin `LazyInitializationException` ni N+1, bajo `open-in-view=false` | HTTP real, con inspección del log SQL (ver sección 5) |
| CSRF funcional en todos los formularios tras eliminar el campo duplicado | HTTP real (login, registro, mascota, cita, carrito, admin) |
| No hay formularios anidados ni ids duplicados en la misma página | Inspección de código (script automatizado sobre las 25 plantillas) |
| Rutas de `SecurityConfig` alineadas con los prefijos reales de los controladores | Inspección de código |
| `@Transactional` presente en todos los métodos de escritura de los servicios | Inspección de código |

## 2. Funcionalidades que fallaban

| # | Descripción | Severidad |
|---|---|---|
| 1 | `GET /cliente/citas/nueva` (agendar cita) devolvía **HTTP 500** siempre — la página nunca llegaba a renderizar | CRÍTICO |
| 2 | `GET /cliente/citas/{id}/reprogramar` tenía el mismo problema — también 500 | CRÍTICO |
| 3 | `LazyInitializationException` en `/admin/citas`, `/admin/pedidos`, `/admin/pedidos/{id}`, `/doctor/citas`, `/cliente/pedidos` bajo `spring.jpa.open-in-view=false` (la configuración de `application-prod.properties`, la que se usará en el despliegue real) | CRÍTICO |
| 4 | Un fallo de CSRF (u otro error) durante una acción anónima (ej. el propio login) se enmascaraba como una redirección silenciosa a `/login`, sin ningún mensaje — indistinguible de "se cerró la sesión" | CRÍTICO |
| 5 | **`POST /doctor/citas/{id}/atender` (registrar historial médico) fallaba siempre** con `ObjectOptimisticLockingFailureException` — descubierto por las pruebas automatizadas de la Fase 3, no estaba entre los bugs reportados ni se detectó en las pruebas manuales de la Fase 1 | CRÍTICO |
| 6 | `CitaController.nueva()` no validaba que el `doctorId` recibido correspondiera realmente a un usuario con rol DOCTOR | ALTO |
| 7 | Cada formulario tenía un campo `_csrf` oculto duplicado (uno automático de Spring, uno manual) | MEDIO |
| 8 | Wording "Agregar" vs "Agregar al carrito" ligeramente inconsistente en el catálogo | BAJO |

Los tres bugs originalmente reportados por el usuario quedan así:
- **Bug #2 ("no se pueden agendar citas")** → corresponde exactamente al hallazgo #1.
- **Bug #3 ("el logo lleva a iniciar sesión de nuevo")** → la redirección por rol ya estaba corregida de un paso anterior; la causa estructural más profunda es el hallazgo #4.
- **Bug #1 ("no se pueden agregar artículos al carrito")** → no se pudo reproducir de forma determinista ni por HTTP directo ni por inspección de código; la hipótesis de mayor confianza es que el usuario experimentó el mismo síntoma del hallazgo #4 (un fallo de CSRF disfrazado de "sesión cerrada"), ya corregido. Se recomienda verificación manual del usuario (ver sección 7).

## 3. Causa de cada problema

1. **`cliente/citas/formulario.html:64`** — `th:attr="min=${#temporals.format(T(java.time.LocalDateTime).now(), ...)}"`. Thymeleaf prohíbe, por seguridad, el uso de `T(...)` (referencias a clases estáticas) dentro de expresiones `${...}` estándar. La plantilla ni siquiera lograba parsearse (`TemplateProcessingException`).
2. **`cliente/citas/reprogramar.html:48`** — exactamente el mismo patrón prohibido.
3. Los métodos de repositorio usados por esas 5 vistas (`CitaRepository.findAllByOrderByFechaHoraDesc`, `findByEstado`, `findByDoctorIdOrderByFechaHoraAsc`, `PedidoRepository.findAllByOrderByFechaDesc`, `findByEstado`, `findByClienteIdOrderByFechaDesc`, `findById`) no cargaban las relaciones `@ManyToOne`/`@OneToMany` que las plantillas recorren (`cita.mascota.cliente`, `pedido.detalles`, `detalle.producto`, etc.). Con `open-in-view=true` (el valor por defecto que usa el perfil `local`) esto queda oculto porque la sesión de Hibernate sigue abierta durante el renderizado; con `open-in-view=false` (perfil `prod`) no hay sesión activa al renderizar y el acceso a cualquier relación LAZY revienta.
4. **`SecurityConfig.java:38-41`** — la ruta `/error` no estaba en `permitAll()`. Cualquier error de un usuario anónimo (típicamente un CSRF inválido, por ejemplo de una pestaña vieja o un reenvío del navegador) se reenvía internamente a `/error`; como esa ruta exige autenticación, el filtro de seguridad trata el reenvío como "acceso denegado de un anónimo" y redirige otra vez a `/login`, sin mostrar el error real.
5. **`DoctorCitaController.java:51`** (antes del fix) — el método tenía `@PathVariable Long id` (el id de la Cita) junto a `@ModelAttribute HistorialMedico historial`. La entidad `HistorialMedico` también tiene un campo `id`. El *data binder* de Spring MVC, al resolver el `@ModelAttribute`, incorpora las variables de la plantilla de ruta (path variables) como fuente de valores para el *binding* — como el nombre coincide (`id`), el id de la Cita terminaba asignado a `historial.setId(...)`. Con un id no nulo, `HistorialMedicoRepository.save()` interpreta la entidad como "existente" y ejecuta un `merge()` (UPDATE) en vez de un `persist()` (INSERT); como no existe ninguna fila con ese id en `historial_medico`, Hibernate lanza `StaleObjectStateException` → `ObjectOptimisticLockingFailureException`. Esto ocurre igual en producción real (no es un artefacto de MockMvc): lo confirmé con una petición HTTP real contra la app corriendo.
6. **`CitaController.java:66`** (antes del fix) — `usuarioService.buscarPorId(doctorId)` no comprobaba `rol == DOCTOR`, permitiendo asociar como "doctor" el id de cualquier usuario.
7. **`fragments/layout.html` + 20 líneas repetidas en 15 plantillas** — se agregó manualmente un `<input type="hidden" th:name="${_csrf.parameterName}" ...>` en cada formulario, sin saber que Spring ya inyecta automáticamente ese campo en todo `<form th:action="...">` (vía `thymeleaf-extras-springsecurity6` + `RequestDataValueProcessor`). Confirmé por HTML renderizado que ambos campos siempre traían el mismo valor, por lo que no rompía nada, pero era redundante y un riesgo latente.

## 4. Correcciones realizadas

| Archivo | Cambio | Motivo |
|---|---|---|
| `CitaController.java` | Se agregó `model.addAttribute("minFechaHora", LocalDateTime.now().format(...))` en `nuevaForm()` y `reprogramarForm()`; se agregó validación `doctor.getRol() != Rol.DOCTOR` en `nueva()` | Elimina el `T(...)` de las plantillas; corrige la validación faltante |
| `cliente/citas/formulario.html`, `cliente/citas/reprogramar.html` | `th:attr="min=${#temporals.format(T(...))}"` → `th:min="${minFechaHora}"` | Corrige el 500 al agendar/reprogramar |
| `SecurityConfig.java` | `/error` agregado a `permitAll()` | Evita que los errores anónimos se enmascaren como cierre de sesión |
| `CitaRepository.java` | `@EntityGraph` en `findByMascotaClienteIdOrderByFechaHoraDesc`, `findByDoctorIdOrderByFechaHoraAsc`, `findAllByOrderByFechaHoraDesc`, `findByEstado` (rutas exactas por vista, ver detalle abajo) | Resuelve `LazyInitializationException` sin N+1, bajo `open-in-view=false` (decisión "Opción C" del director) |
| `PedidoRepository.java` | `@EntityGraph` en `findByClienteIdOrderByFechaDesc`, `findAllByOrderByFechaDesc`, `findByEstado`; `findById` sobreescrito con `@EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})` | Ídem, para pedidos (lista y detalle) |
| `DoctorCitaController.java` | `@PathVariable Long id` → `@PathVariable("id") Long citaId` en `atender()` | Elimina la colisión de nombres que causaba el `ObjectOptimisticLockingFailureException` |
| `HistorialMedicoService.java` | `historial.setId(null);` antes de guardar | Defensa adicional: aunque ya no ocurre el *binding* accidental, el servicio nunca debe confiar en un id que llega desde la capa web |
| `AdminProductoController.java` | `@PathVariable Long id` → `@PathVariable("id") Long productoId` en `editar()` | Mismo patrón de riesgo detectado (no estaba roto porque `ProductoService.actualizarProducto` nunca lee `datos.getId()`, pero se corrigió preventivamente) |
| 15 plantillas (`login.html`, `registro.html`, `fragments/layout.html`, formularios de mascota/cita/doctor/producto, `cliente/carrito/ver.html`, listas con acciones) | Eliminado el `<input type="hidden" th:name="${_csrf.parameterName}"...>` manual (20 ocurrencias) | Elimina la duplicación; Spring sigue inyectando el campo automáticamente |

**Detalle de los `@EntityGraph` aplicados** (rutas exactas detectadas leyendo el HTML de cada plantilla, no asumidas):

| Método de repositorio | Vista(s) que lo usan | `attributePaths` |
|---|---|---|
| `CitaRepository.findByMascotaClienteIdOrderByFechaHoraDesc` | `cliente/citas/lista.html` | `mascota`, `doctor` |
| `CitaRepository.findByDoctorIdOrderByFechaHoraAsc` | `doctor/citas/agenda.html` | `mascota`, `mascota.cliente` |
| `CitaRepository.findAllByOrderByFechaHoraDesc` | `admin/citas/lista.html` (sin filtro) | `mascota`, `mascota.cliente`, `doctor` |
| `CitaRepository.findByEstado` | `admin/citas/lista.html` (filtrado) *y* `admin/dashboard.html` (conteo) | `mascota`, `mascota.cliente`, `doctor` |
| `PedidoRepository.findByClienteIdOrderByFechaDesc` | `cliente/pedidos/lista.html` | `detalles` |
| `PedidoRepository.findAllByOrderByFechaDesc` | `admin/pedidos/lista.html` (sin filtro) | `cliente`, `detalles` |
| `PedidoRepository.findByEstado` | `admin/pedidos/lista.html` (filtrado) *y* `admin/dashboard.html` (conteo) | `cliente`, `detalles` |
| `PedidoRepository.findById` (override) | `admin/pedidos/detalle.html`, `cliente/pedidos/detalle.html` | `cliente`, `detalles`, `detalles.producto` |

No se presentó ningún caso de `MultipleBagFetchException`: ninguna entidad tiene más de una colección `List` en su grafo (`Cita` no tiene colecciones; `Pedido` solo tiene `detalles`), así que no hizo falta el mecanismo de "una sola colección en el grafo + carga separada" previsto como plan B.

**Nota transparente sobre un efecto secundario menor**: `findByEstado` (tanto de `Cita` como de `Pedido`) se usa tanto para las listas filtradas del admin como para los contadores del dashboard (`citaService.listarPorEstado(...).size()`). Al anotarlo con `@EntityGraph`, los conteos del dashboard también incurren en los `LEFT JOIN` adicionales, aunque no los necesiten. Es un costo marginal (no afecta corrección), y separar el método en una variante "solo conteo" no fue parte de lo solicitado — lo dejo señalado por si se prefiere optimizarlo más adelante.

## 5. Pruebas ejecutadas

Se agregó H2 (`com.h2database:h2`, scope `test`) al `pom.xml`, un perfil `test` (`src/test/resources/application-test.properties`, `ddl-auto=create-drop`) y 4 clases de prueba en `src/test/java/com/luaspets/` (además de activar el perfil `test` en `LuasPetsApplicationTests`). **Resultado de `mvn test`: 16/16 pruebas verdes, `BUILD SUCCESS`.**

| Clase | Prueba | Verifica | Resultado |
|---|---|---|---|
| `AuthenticationIntegrationTest` | `registroYLoginExitoso` | Registro + login exitoso, redirect a `/cliente/dashboard` | ✅ |
| | `loginFallidoConCredencialesIncorrectas` | Redirect a `/login?error` | ✅ |
| | `usuarioNoAutenticadoEsRedirigidoAlLoginEnRutaProtegida` | Redirect a `/login` sin sesión | ✅ |
| | `clienteRecibe403EnDashboardAdminYEnCitasDeDoctor` | 403 en `/admin/dashboard` y `/doctor/citas` para un CLIENTE | ✅ |
| `MascotaIntegrationTest` | `registrarMascotaQuedaPersistidaYAsociadaAlClienteCorrecto` | Persistencia y asociación correcta al cliente | ✅ |
| `CitaIntegrationTest` | `agendarCitaCasoFeliz` | Cita creada en estado PENDIENTE | ✅ |
| | `dosCitasMismoDoctorMismaHoraLaSegundaFalla` | Rechazo con el mensaje de negocio exacto | ✅ |
| | `reprogramarCitaCambiaLaFecha` | `fechaHora` actualizada en BD | ✅ |
| | `cancelarCitaAtendidaEsRechazada` | Rechazo con mensaje exacto; estado no cambia | ✅ |
| | `registrarHistorialMedicoMarcaLaCitaComoAtendida` | Historial persistido, cita → ATENDIDA (bug real detectado y corregido aquí) | ✅ |
| | `clienteNoPuedeAccederALaCitaDeOtroCliente` | 403 al intentar reprogramar la cita de otro cliente | ✅ |
| `TiendaCarritoPedidoIntegrationTest` | `agregarProductoAlCarritoActualizaContadorYTotal` | Contador y total del carrito | ✅ |
| | `confirmarPedidoPersisteDetallesYDescuentaStock` | Pedido + detalles persistidos; stock descontado exactamente | ✅ |
| | `confirmarPedidoConCantidadMayorAlStockEsRechazado` | Rechazo con mensaje exacto; ni pedido ni stock se modifican | ✅ |
| | `clienteNoPuedeVerElPedidoDeOtroCliente` | 403 al pedir el pedido de otro cliente | ✅ |
| `LuasPetsApplicationTests` | `contextLoads` | El contexto de Spring levanta correctamente contra H2 | ✅ |

Nota metodológica: durante la escritura de las pruebas encontré y corregí dos bugs *en mi propio código de test* (uso incorrecto de `redirectedUrlPattern` para URLs literales, que no es válido en Ant-pattern cuando no hay comodines) — no eran bugs de la aplicación. Los diferencié cuidadosamente del bug real (`ObjectOptimisticLockingFailureException`) aislándolo primero con una llamada directa al servicio (sin HTTP) para confirmar que el fallo estaba específicamente en el *binding* HTTP → controlador, no en la lógica de negocio.

## 6. Problemas de UX/usabilidad encontrados

Reutilizando la evaluación de la Fase 1 (no cambió nada nuevo relevante en Fase 2, que fue estrictamente correctiva):

| Hallazgo | Severidad | Estado |
|---|---|---|
| Redirección de errores anónimos disfrazada de cierre de sesión (hallazgo #4) | CRÍTICO | **Corregido** |
| Wording "Agregar" (catálogo) vs "Agregar al carrito"/"Agregar mascota" en otras vistas | BAJO | No corregido — es un ajuste cosmético de espacio en una card pequeña, se deja como recomendación |
| Usabilidad a 375px (sidebar colapsable, tablas con scroll, formularios en una columna) | — | **Requiere verificación manual** — ver sección 7 |
| Resto de la evaluación UX (breadcrumbs, botones "Volver", estados vacíos, feedback de éxito/error, ausencia de callejones sin salida, consistencia visual) | — | Verificado por inspección de código en la revisión de diseño de un paso anterior; sin cambios desde entonces |

## 7. Funcionalidades que no pudieron verificarse

- **Bug #1 original ("no se pueden agregar artículos al carrito")**: no se logró reproducir mediante HTTP directo ni inspección de código; el flujo probado funciona correctamente de forma consistente. **Pido al usuario que lo vuelva a probar en el navegador** ahora que el hallazgo #4 (enmascaramiento de errores anónimos como cierre de sesión) está corregido — si el síntoma original era justamente eso, ya debería estar resuelto. Si persiste, por favor abrir las herramientas de desarrollador del navegador (pestaña Network) al hacer clic en "Agregar" y compartir el código de estado HTTP y la respuesta exacta.
- **Renderizado visual y responsive (375px de ancho)**: no dispongo de un navegador real ni de capacidad de captura de pantalla en este entorno; toda la verificación de layout, colapso del sidebar, scroll horizontal de tablas, etc. fue por inspección del CSS/HTML (clases Bootstrap `row-cols-1`, `table-responsive`, media query `max-width: 991.98px` del sidebar), no por observación directa. **Instrucción para el usuario**: abrir la app en Chrome/Firefox, activar las herramientas de desarrollador, seleccionar un dispositivo de ~375px de ancho (ej. iPhone SE) y recorrer manualmente: login, registro, catálogo, carrito, y al menos una tabla larga (citas o pedidos del admin) para confirmar que no hay scroll horizontal de página y que el botón hamburguesa abre/cierra el sidebar correctamente.
- **Comportamiento exacto de `spring.jpa.open-in-view=false` bajo carga concurrente real**: verifiqué que no hay `LazyInitializationException` ni N+1 con un usuario a la vez; no se hicieron pruebas de concurrencia/carga (fuera del alcance de esta auditoría).
- **Envío de credenciales/contraseña por MySQL en el servidor Oracle Cloud real**: la auditoría se hizo contra MySQL local; no se verificó el comportamiento contra el servidor de producción real (aún no desplegado).

## 8. Recomendaciones finales

Ordenadas por impacto estimado; ninguna fue implementada, quedan a criterio del usuario.

1. **(Impacto alto / esfuerzo bajo)** Separar `findByEstado` en dos métodos: uno con `@EntityGraph` para las vistas de lista del admin, y uno plano (o un `countByEstado`) para los contadores del dashboard — evita el join innecesario señalado en la sección 4. Esfuerzo: ~15 minutos.
2. **(Impacto medio / esfuerzo bajo)** Aplicar el mismo patrón de `@EntityGraph` a `CitaRepository.findByDoctorIdAndEstado` y a cualquier otro método de listado que se agregue en el futuro y alimente una vista — dejar como convención del equipo "todo método de repositorio que alimenta una plantilla debe declarar explícitamente sus `attributePaths`". Esfuerzo: depende de cuántos métodos nuevos se agreguen.
3. **(Impacto medio / esfuerzo bajo)** Revisar sistemáticamente el resto de controladores en busca del patrón `@PathVariable Long id` + `@ModelAttribute <Entidad con campo id>` (el mismo mecanismo que rompía "atender cita"). Ya se corrigieron los dos casos encontrados (`DoctorCitaController`, `AdminProductoController`), pero vale la pena una regla de estilo del equipo: nombrar siempre el `@PathVariable` de forma explícita y distinta al campo `id` de la entidad que se vaya a *bindear* en el mismo método (ej. `@PathVariable("id") Long citaId`). Esfuerzo: bajo, es un hábito de codificación.
4. **(Impacto medio / esfuerzo medio)** Agregar pruebas de UI automatizadas con un navegador headless (Playwright/Selenium) para cubrir lo que esta auditoría no pudo verificar: renderizado visual, responsive, y comportamiento de JavaScript (modales, validación HTML5 del lado cliente). Esfuerzo: 1-2 días para dejar un esqueleto básico.
5. **(Impacto bajo / esfuerzo bajo)** Unificar el wording de los botones de "agregar" (catálogo dice "Agregar", el resto dice "Registrar X"/"Agendar X") — puramente cosmético. Esfuerzo: minutos.
6. **(Impacto bajo / esfuerzo bajo)** Limpiar los datos de prueba que quedaron en la base de datos MySQL local durante esta auditoría (usuario `test.auditoria2@luaspets.com` y `live.atender@test.com`, sus mascotas, citas y pedidos asociados) si se desea una base de datos de desarrollo limpia. No se borraron automáticamente porque modificar datos de la base de datos del usuario sin pedir confirmación no correspondía al alcance de esta tarea.
7. **(Impacto bajo / esfuerzo medio)** Considerar mover la generación del valor `minFechaHora` (fecha/hora actual para el atributo `min` del `datetime-local`) a un `@ControllerAdvice`/`@ModelAttribute` global si en el futuro se necesita en más formularios con fecha, para no repetir el patrón en cada controlador.
