# Luas Pets - Sistema de Gestión Veterinaria

Aplicación web para la gestión integral de una veterinaria: registro y autenticación de usuarios por rol, administración de mascotas y citas médicas, tienda con carrito de compras, y un panel de administración con estadísticas y gestión de doctores, productos, citas y pedidos.

## Stack tecnológico

- Java 21
- Spring Boot 4
- Spring Security 7 (autenticación y autorización por roles)
- Spring Data JPA / Hibernate
- Thymeleaf (con thymeleaf-extras-springsecurity6)
- Bootstrap 5 (vía CDN)
- MySQL 8
- Maven
- Lombok

## Funcionalidades por rol

### Cliente
- Registro de cuenta e inicio de sesión.
- Registro y consulta de sus mascotas.
- Agendar, reprogramar y cancelar citas veterinarias.
- Consultar la tienda, filtrar por categoría o buscar por nombre.
- Carrito de compras en sesión y confirmación de pedidos (compra simulada, sin pago real).
- Consultar el historial y detalle de sus pedidos.

### Doctor
- Consultar su agenda de citas asignadas.
- Atender citas: registrar diagnóstico, tratamiento y observaciones (historial médico), lo que marca la cita como atendida automáticamente.

### Admin
- Dashboard con estadísticas generales (clientes, doctores activos, citas pendientes/atendidas, productos activos, pedidos pendientes).
- Gestión de doctores: registrar, activar y desactivar.
- Gestión de productos: crear, editar, activar y desactivar.
- Vista de todas las citas de la clínica, con filtro por estado (solo lectura).
- Vista de todos los pedidos, con filtro por estado y actualización manual del estado del pedido.

## Requisitos previos

- Java 21
- Maven
- MySQL 8 corriendo localmente (o accesible por red)

## Ejecutar el proyecto localmente

La contraseña de MySQL **no** está en `application.properties` (usa la variable de entorno `DB_PASSWORD`, con valor por defecto vacío). Hay dos formas de configurarla para desarrollo local:

### Opción 1: archivo `application-local.properties`

Crea (si no existe) `src/main/resources/application-local.properties` con tu contraseña real de MySQL:

```properties
spring.datasource.password=tu_password_de_mysql
```

Este archivo está en `.gitignore` y nunca se sube al repositorio. Luego arranca la aplicación con el perfil `local`, usando el Maven Wrapper (recomendado, no requiere tener Maven instalado) o Maven global:

**Con Maven Wrapper:**

```bash
# Linux/Mac
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Windows
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

**Con Maven instalado globalmente:**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Opción 2: variable de entorno

En lugar del archivo anterior, puedes exportar la variable de entorno antes de arrancar:

```bash
export DB_PASSWORD=tu_password_de_mysql
mvn spring-boot:run
```

(En Windows PowerShell: `$env:DB_PASSWORD = "tu_password_de_mysql"`)

Otras variables de entorno disponibles, todas con valores por defecto para desarrollo local: `DB_HOST` (localhost), `DB_PORT` (3306), `DB_NAME` (luas_pets), `DB_USER` (root), `SERVER_PORT` (8080).

La base de datos `luas_pets` se crea automáticamente si no existe (`createDatabaseIfNotExist=true`), y las tablas se generan/actualizan vía `ddl-auto=update`.

La aplicación queda disponible en [http://localhost:8080](http://localhost:8080).

## Usuarios de prueba precargados

Al arrancar la aplicación por primera vez se crean automáticamente (si no existen) los siguientes usuarios:

| Rol     | Email                  | Contraseña  |
|---------|-------------------------|-------------|
| Admin   | admin@luaspets.com      | admin123    |
| Doctor  | doctor@luaspets.com     | doctor123   |
| Doctor  | lucio@gmail.com         | doctor123   |
| Cliente | cliente@luaspets.com    | cliente123  |

También se precargan 8 productos de ejemplo en la tienda, y datos de demostración (mascotas, citas en distintos estados, un historial médico y un pedido entregado) asociados al cliente de demostración, si esas tablas están vacías.

## Despliegue

La aplicación se despliega en **Render** (servicio web, plan free, construido directamente desde este repositorio de GitHub) y la base de datos MySQL está alojada en **Aiven**. La configuración del servicio está versionada en [`render.yaml`](render.yaml), así que Render la detecta automáticamente al conectar el repositorio; no hace falta configurar nada manualmente en la interfaz salvo las variables de entorno con datos sensibles.

Render ya no ofrece un entorno de ejecución nativo para Java, así que el despliegue se hace con el [`Dockerfile`](Dockerfile) del repositorio (`runtime: docker` en `render.yaml`): una construcción multi-etapa que compila el proyecto con la imagen oficial de Maven y luego copia únicamente el `.jar` resultante a una imagen liviana con solo el JRE. Como el Dockerfile ya define tanto la construcción como el comando de arranque, **no se configura ningún build command ni start command en el panel de Render**.

### Perfil activo

En producción se arranca con `-Dspring.profiles.active=prod`, que activa `application-prod.properties` (caché de plantillas, `open-in-view` deshabilitado, logging reducido, pool de conexiones acotado y compresión de respuestas).

### Variables de entorno a configurar en Render

Al crear el servicio Aiven te entrega un host, puerto, nombre de base de datos, usuario y contraseña (visibles en el panel de tu servicio MySQL en Aiven, sección "Connection details" o en el `Service URI`). Esos cinco valores se cargan en el panel de Render, en la sección **Environment** del servicio (no van en `render.yaml`, que solo declara que existen mediante `sync: false`):

| Variable      | De dónde sale                                             |
|---------------|-------------------------------------------------------------|
| `DB_HOST`     | Host del servicio MySQL en Aiven                             |
| `DB_PORT`     | Puerto del servicio MySQL en Aiven (normalmente no es 3306)  |
| `DB_NAME`     | Nombre de la base de datos ya creada en Aiven                |
| `DB_USER`     | Usuario que entrega Aiven                                    |
| `DB_PASSWORD` | Contraseña que entrega Aiven                                 |

El resto de variables ya están fijadas en `render.yaml` y no requieren acción: `DB_SSL=true` y `DB_REQUIRE_SSL=true` (Aiven exige conexión cifrada), `DB_CREATE=false` (el usuario de Aiven no tiene permiso para crear bases de datos; la base ya existe de antemano). La versión de Java (21) la fija la imagen base del Dockerfile, no una variable de entorno. Render asigna el puerto HTTP automáticamente mediante su propia variable `PORT`, que la aplicación ya lee (`server.port=${PORT:${SERVER_PORT:8080}}`).

### Monitoreo y disponibilidad

El plan free de Render suspende el servicio tras 15 minutos sin tráfico. El endpoint público **`GET /health`** (sin autenticación, no consulta la base de datos) existe para que un servicio externo de ping lo llame periódicamente y mantenga la aplicación despierta, y también sirve como chequeo de disponibilidad general.

### ⚠️ Advertencia de seguridad

La contraseña de la base de datos **nunca** debe escribirse en ningún archivo del repositorio (ni en `application.properties`, ni en `render.yaml`, ni en ningún commit). Solo debe existir como variable de entorno `DB_PASSWORD` configurada directamente en el panel de Render. Para desarrollo local, la contraseña va únicamente en `application-local.properties`, que está excluido por `.gitignore`.
