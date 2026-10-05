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
export TOTP_ENCRYPTION_KEY=tu_clave_base64_guardada
./mvnw spring-boot:run
```

(En Windows PowerShell: `$env:DB_PASSWORD = "tu_password_de_mysql"`)

Otras variables de entorno disponibles, todas con valores por defecto para desarrollo local: `DB_HOST` (localhost), `DB_PORT` (3306), `DB_NAME` (luas_pets), `DB_USER` (root), `SERVER_PORT` (8080).

Antes de arrancar, configura también `TOTP_ENCRYPTION_KEY` como se explica abajo; es obligatoria en todos los perfiles de ejecución.

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

## Verificación en dos pasos

TOTP agrega un código de seis dígitos después de email y contraseña. Es compatible con Google Authenticator, Microsoft Authenticator, Aegis y 2FAS: intervalo de 30 segundos, SHA-1 y tolerancia de un intervalo anterior o siguiente. Un código aceptado no puede reutilizarse.

### Configurar la clave antes de arrancar

Genera una clave aleatoria de 32 bytes una sola vez:

```bash
openssl rand -base64 32
```

Guarda el resultado en `TOTP_ENCRYPTION_KEY` del `.env` real (ignorado por Git) o en el gestor de secretos del despliegue. No uses la clave de pruebas. La aplicación falla al arrancar si falta la clave, no es Base64 válido o no representa exactamente 32 bytes.

Los secretos TOTP se almacenan cifrados con AES-256-GCM y un nonce aleatorio por cifrado. Conserva una copia segura de la clave junto con tu política de copias de seguridad de la base de datos. **No reemplaces la clave sin una migración de recifrado**: otra clave no permite validar los secretos existentes. Reiniciar la aplicación conserva la misma clave.

### Activar y utilizar

1. CLIENTE/DOCTOR: abre **Mi Perfil → Verificación en dos pasos → Activar** y confirma tu contraseña actual. ADMIN: después del primer login con contraseña, completa directamente la configuración obligatoria.
2. Escanea el QR con tu autenticador o introduce la clave manual. El QR se genera localmente; ningún tercero recibe el secreto.
3. Introduce el primer código de seis dígitos. Solo este paso activa y guarda la configuración; abandonar el proceso no activa 2FA.
4. Guarda los códigos de recuperación que se muestran una sola vez, fuera del dispositivo del autenticador.

| Rol | Política |
|-----|----------|
| CLIENTE | Activación opcional desde Mi Perfil. |
| DOCTOR | Activación opcional desde Mi Perfil. |
| ADMIN | Obligatoria: el primer login, incluso el administrador demo, permite únicamente completar la configuración. No puede desactivarse. |

Con 2FA activa, contraseña correcta lleva a `/2fa`; ningún dashboard, perfil u otra ruta autenticada se concede antes del segundo factor. Los procesos provisionales caducan y cerrar sesión elimina el estado del segundo factor.

El flujo permite cinco solicitudes de verificación por sesión. Si las cinco fallan, el quinto intento todavía muestra el formulario con el error genérico; el sexto POST invalida la sesión antes de comprobar el código y exige iniciar sesión con contraseña nuevamente. Refrescar la página no reinicia el contador. El mismo límite protege la configuración y la administración MFA; completar correctamente el login MFA reinicia el contador.

### Recuperación y cambios sensibles

Si pierdes el autenticador, usa un código de recuperación en `/2fa`. Cada código sirve una sola vez; la base de datos conserva únicamente hashes. El código recupera acceso para esa sesión. Si todavía conservas el autenticador, puedes regenerar un conjunto desde Mi Perfil con contraseña y un TOTP nuevo; regenerar invalida todos los anteriores. Si perdiste el autenticador permanentemente, contacta al responsable para una recuperación operativa con identidad verificada: los códigos de recuperación no sustituyen el TOTP requerido para desactivar o modificar MFA.

Desactivar 2FA para CLIENTE/DOCTOR exige contraseña actual y un TOTP válido nuevo; elimina secreto y códigos. Cambiar contraseña o configuración MFA, o regenerar códigos, revoca las demás sesiones mediante una versión de seguridad en la base de datos. La sesión actual también se revoca al modificar MFA o regenerar códigos: guarda los códigos mostrados antes de navegar e inicia sesión de nuevo. Espera al siguiente intervalo TOTP para no reutilizar el código que acabas de aceptar. Las sesiones antiguas deben iniciar sesión otra vez. Los formularios mantienen protección CSRF.

Si pierdes tanto autenticador como códigos, no hay bypass por URL, código maestro ni desactivación automática. Contacta al responsable del sistema para verificar tu identidad y acordar una recuperación operativa segura. No borres datos ni cambies la clave de cifrado como intento de recuperación.

## Despliegue en Docker / VPS

La configuración definitiva es `docker-compose.yml`: ejecuta la aplicación en perfil `prod` con MySQL 8.0 y el proxy externo Nginx Proxy Manager. Es el único archivo Compose autodetectable; `docker compose` y `docker compose -f docker-compose.yml` seleccionan la misma configuración.

El despliegue oficial actual es:

```text
VPS
├── Nginx Proxy Manager
├── LUAS Pets (Docker)
└── MySQL 8 (Docker)
```

1. Copia `.env.example` a `.env` y completa las contraseñas de MySQL y `TOTP_ENCRYPTION_KEY`.
2. Conserva el volumen `mysql_data` y las copias de seguridad existentes. Hibernate usa `ddl-auto=update` para incorporar columnas MFA y códigos de recuperación sin reinicializar datos; prueba primero con una copia de la BD.
3. Ejecuta la configuración versionada:

```bash
docker compose -f docker-compose.yml up -d --build
```

El servicio `app` recibe `TOTP_ENCRYPTION_KEY` explícitamente desde `.env`; este archivo sirve para interpolación y sus variables no se inyectan automáticamente al contenedor. Sustituye `BASE64_DE_32_BYTES` por la clave generada: el placeholder es deliberadamente inválido. Se conservan los puertos, volúmenes y la configuración de Nginx Proxy Manager. Mantén HTTPS en el proxy.

Producción utiliza el perfil `prod`. `GET /health` permite comprobar disponibilidad. El proceso debe estar activo a las 08:00 de Perú para ejecutar los recordatorios diarios.

### Reloj del VPS

TOTP depende de la hora UTC real, no de la zona horaria de presentación. Mantén NTP activo con `systemd-timesyncd`, chrony o el servicio equivalente del VPS. Comprueba la sincronización con `timedatectl status` donde esté disponible. Sincroniza también el teléfono; no aumentes la ventana de validación para ocultar un reloj incorrecto.

## Pruebas

```bash
./mvnw test
```

Las pruebas usan H2 y una clave fija **exclusivamente de pruebas** en `src/test/resources/application-test.properties`; no requieren la clave del entorno de producción ni acceden al VPS.
