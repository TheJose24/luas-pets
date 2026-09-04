package com.luaspets.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ConsultaExpediente {

    private Long id;
    private String titulo;
    private String mesAbreviado;
    private String fechaTexto;
    private String doctorNombre;
    private String pesoTexto;
    private String diagnostico;
    private String tratamiento;
    private String observaciones;
    private List<String> medicamentosLineas;
    private boolean esReciente;
}
