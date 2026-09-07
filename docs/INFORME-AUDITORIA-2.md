# Informe de Segunda Auditoría — Luas Pets

Fecha: 2026-09-03
Alcance: re-auditoría de todo lo cambiado desde `INFORME-AUDITORIA.md`, más re-verificación de lo ya auditado. Metodología: FASE 1 (diagnóstico estático, sin tocar código) → FASE 2 (pruebas HTTP reales contra la app corriendo con `spring.jpa.open-in-view=false` forzado) → FASE 3 (correcciones aprobadas) → FASE 4 (ampliación de tests automatizados + limpieza de datos) → este informe.

---

## 1. Funcionalidades verificadas

**Por inspección estática (FASE 1)**: inventario completo de los 30 templates y sus controladores; cruce de cada `${...}` de Model contra lo que añade cada controlador (con foco especial en los 3 dashboards, por indicación explícita); cruce de cada `action`/`href`/fetch contra el endpoint real y sus parámetros; cobertura de `@EntityGraph` contra cada ruta LAZY usada en las vistas (incluidas rutas anidadas como `cita.mascota.cliente`); barrido de expresiones Thymeleaf frágiles (`T(...)`, `#strings`/`#temporals` en bucles, acceso a propiedades nulificables); estructura HTML (forms anidados, ids duplicados en `th:each`, atributos `form=` colgantes) verificada con dos scripts propios sobre las 30 plantillas.

**Por pruebas automatizadas (`mvn test`, 37 tests, todos en verde)**: reglas de negocio de citas (agendar, doble reserva, reprogramar, cancelar cita atendida rechazada), regla del peso (con y sin `pesoRegistrado`), aislamiento entre clientes (mascotas, citas, pedidos), aislamiento del doctor (ficha de paciente nunca atendido → 403), bloqueo de rol en `/admin/mascotas`, cambio de estado de mascota por admin, paginación del listado admin de mascotas, ubicación correcta en la rejilla del calendario (10:30 → fila 2/columna 1; 07:00 → fila 0 con `fueraDeRango=true`), flujo completo de carrito→pedido (incluido rechazo por stock insuficiente y CSRF obligatorio en la API), bloqueo de rol DOCTOR en la API de carrito, y render sin error de una mascota con `estado`/`sexo`/`alergias` nulos en las tres vistas que la muestran.

**Por pruebas HTTP en vivo (FASE 2, `curl` contra la app real, `open-in-view=false`)**: registro, login, logout, los 3 dashboards, alta/listado/perfil de mascota, alta/reprogramación/cancelación de cita, atender cita con y sin peso (verificado que el peso de la mascota cambia o no cambia según corresponda), catálogo y API de carrito (con y sin token CSRF), checkout completo hasta ver el pedido en el detalle, panel admin completo (mascotas con filtro y cambio de estado, citas en vista calendario y lista, pedidos con cambio de estado, productos y doctores con alta/edición/activación/desactivación), los 9 cruces de bloqueo entre roles, ownership sobre recursos ajenos, página 404 y 403 personalizada (verificada con cabecera `Accept: text/html`, como envía cualquier navegador real), y bloqueo de login para un doctor desactivado. **Cero excepciones** aparecieron en el log de la aplicación en toda la sesión de pruebas en vivo (9115 líneas de log revisadas).

---

## 2. Funcionalidades que fallaban

| # | Hallazgo | Severidad |
|---|---|---|
| 1 | `cliente/dashboard.html` y `doctor/dashboard.html` sin breadcrumb, `admin/dashboard.html` sí lo tenía | MEDIO |
| 2 | Botón "Agregar al carrito" sin respaldo funcional sin JavaScript | MEDIO |
| 3 | Dropdown "Cambiar estado" en `admin/mascotas/lista.html` con riesgo de recorte visual dentro de `.table-responsive` | MEDIO (preventivo, no confirmado visualmente — ver sección 7) |
| 4 | Confirmación de contraseña en registro sin validar en el servidor | MEDIO |
| 5 | Sin mensaje de éxito al actualizar el estado de un pedido (admin) | MEDIO |
| 6 | Credencial documentada `lucio@gmail.com / doctor123` no autentica en la BD local | INFORMATIVO — no es un bug de código (ver causa raíz) |

Ningún hallazgo CRÍTICO ni ALTO sobrevivió la auditoría completa (30 plantillas cruzadas línea por línea contra sus controladores, más las pruebas en vivo).

---

## 3. Causa raíz de cada problema

1. **Breadcrumb inconsistente**: decisión de diseño no documentada — dos de los tres dashboards nunca lo tuvieron; `admin/dashboard.html` lo incluía por copiar el patrón usado en el resto de vistas del panel admin sin considerar que un dashboard es la raíz de navegación de cada rol.
2. **Botón sin fallback**: [cliente/tienda/catalogo.html:91-94](src/main/resources/templates/cliente/tienda/catalogo.html) (antes del fix) usaba `<button type="button">` puro sin `<form>`, dependiente 100% de `carrito.js`. El endpoint `POST /cliente/tienda/agregar` (`TiendaController.agregar`) seguía existiendo pero sin ningún punto de entrada en la UI.
3. **Dropdown recortado**: `.dropdown-menu` anidado dentro de `.table-responsive` (`overflow-x:auto` en [admin/mascotas/lista.html:39](src/main/resources/templates/admin/mascotas/lista.html)), un problema conocido de Bootstrap cuando el posicionamiento de Popper usa `strategy: 'absolute'` (el valor por defecto).
4. **Confirmación de contraseña**: [AuthController.java:48](src/main/java/com/luaspets/controller/AuthController.java#L48) recibía `@ModelAttribute Usuario usuario` y nunca leía el parámetro `confirmPassword` del formulario — la validación de coincidencia era exclusivamente JavaScript ([registro.html:178-185](src/main/resources/templates/registro.html#L178-L185)).
5. **Sin feedback en pedidos**: [AdminPedidoController.java:38-42](src/main/java/com/luaspets/controller/AdminPedidoController.java#L38-L42) redirigía a `/admin/pedidos` sin flash attribute ni query param, y la plantilla no tenía ninguna alerta de éxito (a diferencia de `admin/productos/lista.html` y `admin/doctores/lista.html`, que sí siguen ese patrón).
6. **Credencial de lucio**: [DataSeeder.java:123](src/main/java/com/luaspets/config/DataSeeder.java#L123) es idempotente (`if (!usuarioRepository.existsByEmail(...))`) — solo fija la contraseña la primera vez que crea la cuenta. La fila ya existía en la BD local de este entorno (probablemente de una sesión de desarrollo anterior a esta auditoría) con una contraseña distinta a `doctor123`, y el seeder nunca la resincroniza en arranques posteriores. No es un defecto del código de producción.

---

## 4. Correcciones realizadas

1. **Breadcrumb**: eliminado de `admin/dashboard.html` (y alineado su `<div class="container-fluid">` al mismo `p-4` sin `pt-2` que usan los otros dos dashboards). Criterio documentado: **los dashboards no llevan breadcrumb; el resto de vistas sí.**
2. **Degradación elegante del carrito**: cada tarjeta de producto ahora envuelve el botón en `<form method="post" th:action="@{/cliente/tienda/agregar}">` con `productoId` oculto (CSRF inyectado automáticamente por `th:action`) y el botón pasó a `type="submit"`. `carrito.js` intercepta el evento `submit` del formulario con `preventDefault()` y sigue haciendo la llamada AJAX de siempre; si el JS no carga, el formulario se envía de forma tradicional al endpoint existente. Se verificó que no se generaron formularios anidados (script de comprobación sobre el HTML resultante: OK). También se añadieron las alertas `${exito}`/`${error}` a `catalogo.html`, que antes no se mostraban pese a que el controlador ya las fijaba.
3. **Dropdown**: añadido `data-bs-boundary="viewport"` al toggle, más un script que inicializa el dropdown con `popperConfig` forzando `strategy: 'fixed'` — esto es lo que realmente evita el recorte por `overflow-x:auto` (cambiar solo el `boundary` no basta, porque un elemento `position: absolute` sigue siendo recortado por el `overflow` del ancestro; `strategy: 'fixed'` lo saca de ese flujo). No se tocó la estructura de la tabla.
4. **Validación de contraseña en servidor**: `AuthController.registrar` ahora recibe `@RequestParam(required = false) String confirmPassword`, valida longitud mínima de 8 caracteres y coincidencia exacta con la contraseña antes de llamar a `usuarioService.registrarCliente`; en caso de fallo devuelve la vista `"registro"` (no un redirect) conservando los datos ya escritos, gracias al comportamiento estándar de Spring MVC que repuebla el Model con el `@ModelAttribute` recibido. La validación de JavaScript se mantuvo intacta.
5. **Feedback en pedidos**: `AdminPedidoController.actualizarEstado` ahora usa `RedirectAttributes` con flash attribute `"exito"` = *"Estado del pedido actualizado correctamente."*, mostrado en `admin/pedidos/lista.html` con el mismo estilo `lp-alert-success` que usan productos y doctores.

**Efecto colateral gestionado**: los 6 tests existentes que hacían `POST /registro` sin `confirmPassword` se actualizaron para incluirlo (de lo contrario habrían empezado a fallar con el nuevo comportamiento).

`mvn clean compile` ejecutado tras todas las correcciones: **BUILD SUCCESS**, sin advertencias.

---

## 5. Pruebas ejecutadas y resultado

- `mvn test` (perfil `test`, base de datos H2 en memoria, aislada de la BD local de MySQL): **37 tests, 0 fallos, 0 errores** — `BUILD SUCCESS`.
  - 4 tests nuevos añadidos esta auditoría: `unaCitaDeLunesA0700QuedaFueraDeRangoYSeUbicaEnLaPrimeraFila`, `registrarHistorialSinPesoRegistradoNoModificaElPesoDeLaMascota`, `mascotaConCamposOpcionalesNulosSeRenderizaSinErrorEnLasTresVistas`, `adminMascotasPaginaCorrectamenteConMasDeUnaPaginaDeResultados`. El resto de los 13 escenarios pedidos ya existían de trabajo previo (verificado leyendo cada archivo de test antes de duplicar nada).
- Las 5 correcciones de FASE 3 se re-probaron en vivo tras aplicarlas (con la app reiniciada por Spring DevTools): breadcrumb ausente confirmado, alta al carrito sin JS confirmado (con mensaje de éxito visible), registro con contraseña corta y con confirmación no coincidente ambos rechazados con el mensaje correcto y los datos del formulario preservados, registro exitoso (caso feliz) confirmado sin regresión, y mensaje de éxito al cambiar estado de un pedido confirmado.
- Limpieza de datos de prueba en MySQL ejecutada al final, respetando el orden de claves foráneas (`detalle_pedido` → `pedido` → `historial_medico` → `cita` → `mascota` → `usuario`, más `producto`), y restaurado el stock de "Alimento para gato" (se le sumaron los 2 units que mi propia compra de prueba había descontado, sin tocar el descuento de 3 unidades de la compra preexistente de `adolfo@gmail.com`).

**Conteo final de filas — idéntico al conteo previo a esta auditoría en cada tabla:**

| Tabla | Antes | Después |
|---|---|---|
| usuario | 5 | 5 |
| mascota | 2 | 2 |
| cita | 1 | 1 |
| historial_medico | 0 | 0 |
| producto | 8 | 8 |
| pedido | 1 | 1 |
| detalle_pedido | 1 | 1 |

Los 5 usuarios seedeados/preexistentes (`admin@luaspets.com`, `doctor@luaspets.com`, `adolfo@gmail.com`, `lucio@gmail.com`, `cliente@luaspets.com`) y todos sus datos asociados quedaron intactos.

---

## 6. Problemas de UX/consistencia encontrados y cuáles se corrigieron

Corregidos (ver sección 4): breadcrumb inconsistente entre dashboards, fallback sin JS del carrito, feedback ausente al actualizar estado de pedido, validación de contraseña solo-cliente.

**Encontrado pero no corregido** (bajo impacto, documentado como recomendación): el dropdown "Activar" de doctores/productos no pide confirmación mientras que "Desactivar" sí — es un patrón consistente en toda la app (no una inconsistencia puntual), así que no se tocó sin instrucción explícita.

---

## 7. Funcionalidades no verificadas y cómo probarlas manualmente

Sé honesto aquí porque el entorno de esta auditoría no incluye un navegador real, solo HTTP vía `curl`/PowerShell:

- **Hallazgo 3 (recorte del dropdown)**: se aplicó la corrección preventiva (`popperConfig` con `strategy: 'fixed'` + `data-bs-boundary="viewport"`), pero **no pude confirmar visualmente** si el problema existía antes ni si la corrección se ve bien en pantalla. Para verificarlo: abrir `/admin/mascotas` como admin en una ventana de 375px de ancho (o con la tabla desplazada horizontalmente), hacer clic en "Cambiar estado" en una fila cerca del borde derecho, y confirmar que el menú se ve completo y no queda cortado por el borde de la tabla.
- **Responsive a 375px en general**: todas las plantillas usan el grid de Bootstrap y `.table-responsive` de forma consistente con el resto de la app (sin cambios en esta auditoría), pero no se verificó visualmente ningún layout a ese ancho. Recomiendo revisar al menos los 3 dashboards, el catálogo de tienda y el offcanvas del carrito en un viewport real de 375px.
- **Recarga completa de cada página buscando errores de render**: se hizo GET a la gran mayoría de rutas principales de los 3 roles y se confirmó 200 + contenido esperado + cero excepciones en el log, pero no se recargó exhaustivamente *cada* variante de filtro/parámetro de *cada* vista (por ejemplo, no se probaron todas las combinaciones de filtros del catálogo de tienda, ni el detalle de pedido en estado ENTREGADO).
- **Credencial de `lucio@gmail.com`**: sigue sin funcionar en esta base de datos local (`doctor123` no coincide con el hash almacenado). No se modificó la base de datos para "arreglarlo" porque no se pidió explícitamente y no es un bug de la aplicación — si quieres esa cuenta operativa para pruebas futuras, dímelo y actualizo su contraseña.

---

## 8. Recomendaciones finales (no implementadas), ordenadas por impacto

1. **`DashboardController.adminDashboard` hace 4 consultas separadas** (`listarPorRol` ×3 + `listarDoctoresActivos`) donde una sola consulta agregada bastaría. Con los volúmenes actuales no es un problema real; vale la pena revisarlo si la base de usuarios crece significativamente.
2. **Las sobrecargas `findById` con `@EntityGraph` global** en `CitaRepository` y `PedidoRepository` cargan joins de más en contextos que no los necesitan (por ejemplo, cualquier `buscarPorId` trae `mascota.cliente` aunque la vista destino no lo use). No se tocó por el riesgo de romper flujos que hoy funcionan; si se aborda, debe hacerse con pruebas de regresión exhaustivas sobre cada vista que dependa de esas rutas de carga.
3. **Consolidar el patrón "Activar sin confirmación / Desactivar con confirmación"** en `admin/doctores/lista.html` y `admin/productos/lista.html` de forma intencional (documentarlo como decisión de diseño, o añadir confirmación simétrica) — es cosmético, bajo impacto.
4. **Refrescar la contraseña de `lucio@gmail.com`** en la base de datos local si se planea usar esa cuenta para pruebas de flujos con dos doctores activos.
