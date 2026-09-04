package com.luaspets.dto;

import com.luaspets.model.EstadoMascota;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MascotaAdminRow {

    private Long id;
    private String codigo;
    private String nombre;
    private String especie;
    private String raza;
    private String fotoUrl;
    private String edadTexto;
    private String fechaNacimientoTexto;
    private String pesoTexto;
    private String propietarioNombre;
    private String propietarioTelefono;
    private String ultimaConsultaTexto;
    private EstadoMascota estado;
    private String estadoTexto;
    private String estadoVariante;
}
