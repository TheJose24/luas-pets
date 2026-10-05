package com.luaspets;

import java.util.TimeZone;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LuasPetsApplication {

	public static void main(String[] args) {
		SpringApplication.run(LuasPetsApplication.class, args);
	}

	// Un contenedor puede usar UTC como zona por defecto. Sin este ajuste, LocalDateTime.now()
	// (usado en todo el sistema: fechaCreacion/fechaRegistro, la validacion de
	// horario y anticipacion de citas, el calculo de "tiempoRelativo" de las
	// notificaciones, y ahora tambien la tarea de recordatorios) devolveria la
	// hora de reloj de pared en UTC, no en Peru: un usuario en Lima veria sus
	// citas desfasadas 5 horas.
	//
	// Se opto por cambiar la zona por defecto de TODA la JVM (en vez de fijar
	// la zona solo en la anotacion @Scheduled del recordatorio) porque el
	// problema no es exclusivo de esa tarea: es sistemico. Limitar el ajuste a
	// @Scheduled(zone = "America/Lima") habria resuelto unicamente el instante
	// en que dispara el cron, dejando el resto de la aplicacion (reservas,
	// timestamps, tiempos relativos) calculando en UTC — una inconsistencia
	// peor y mas dificil de razonar que un cambio global unico y consistente.
	// Ademas, en application.properties las columnas de fecha se guardan como
	// DATETIME (no TIMESTAMP), que MySQL almacena tal cual, sin reinterpretar
	// segun zona horaria; por eso este cambio no corrompe datos ya guardados,
	// solo corrige como se CALCULAN los valores nuevos a partir de ahora.
	//
	// Aun asi, se mantiene tambien zone = "America/Lima" explicito en
	// RecordatorioService como defensa adicional: si en el futuro alguien
	// quitara este PostConstruct (por ejemplo al depurar localmente con otra
	// zona por defecto), el cron seguiria disparando a la hora correcta de
	// Peru sin depender de este ajuste global.
	@PostConstruct
	public void configurarZonaHoraria() {
		TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
	}

}
