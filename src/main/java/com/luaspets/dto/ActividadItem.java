package com.luaspets.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ActividadItem {

    private String icono;
    private String colorVariante;
    private String descripcion;
    private String detalle;
    private String etiqueta;
    private LocalDateTime fecha;
}
