package com.luaspets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpedienteMascota {

    // Cabecera
    private Long id;
    private String codigo;
    private String nombre;
    private String fotoUrl;
    private String especie;
    private String raza;
    private String sexoTexto;
    private String edadTexto;
    private String fechaNacimientoTexto;
    private String pesoTexto;
    private String estadoTexto;
    private String estadoVariante;
    private String propietarioNombre;
    private String propietarioEmail;
    private String propietarioTelefono;

    // Tarjetas de resumen
    private String ultimaVisitaTexto;
    private String ultimaVisitaMotivo;
    private String pesoActualTexto;
    private String variacionPesoTexto;
    private String variacionPesoVariante;
    private String variacionPesoIcono;
    private String alergiasTexto;
    private boolean tieneAlergias;
    private String ultimosMedicamentos;
    private String proximaCitaTexto;
    private String proximaCitaMotivo;
    private Long proximaCitaId;

    // Veterinario tratante
    private String vetNombre;
    private String vetEmail;
    private String vetInicial;

    private int totalConsultas;
}
