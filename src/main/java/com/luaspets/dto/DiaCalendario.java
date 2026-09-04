package com.luaspets.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DiaCalendario {

    private String fechaIso;
    private String nombreDia;
    private String numeroDia;
    private boolean esHoy;
}
