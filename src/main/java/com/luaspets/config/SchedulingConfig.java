package com.luaspets.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita la infraestructura de tareas programadas (@Scheduled) de Spring.
 *
 * Se aisla en una clase de configuracion propia, separada de
 * LuasPetsApplication, y protegida con @Profile("!test") a proposito: el
 * contexto de Spring de los tests (@SpringBootTest) se cachea y se reutiliza
 * entre TODAS las clases de test de la suite, que en conjunto tardan varios
 * minutos en correr. Si @EnableScheduling estuviera activo durante los tests,
 * el TaskScheduler quedaria vivo todo ese tiempo, y aunque la probabilidad de
 * que el cron de RecordatorioService (08:00 hora de Peru) coincida con la
 * ejecucion de la suite es baja, no es nula — y de coincidir, la tarea
 * correria fuera de la transaccion de cualquier test individual, dejando
 * notificaciones huerfanas en la base de datos compartida de H2 y pruebas
 * inestables dificiles de diagnosticar. Con @Profile("!test") la
 * infraestructura de scheduling directamente no se registra al correr con el
 * perfil "test", asi que el riesgo queda eliminado, no solo mitigado.
 *
 * RecordatorioService sigue siendo un bean normal en el perfil de test (su
 * metodo publico procesarRecordatorios se puede invocar directamente); lo
 * unico que se desactiva es el disparador automatico por cron.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
