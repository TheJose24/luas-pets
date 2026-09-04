package com.luaspets.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SemanaCalendario {

    private LocalDate lunes;
    private LocalDate domingo;
    private String tituloSemana;
    private String semanaAnteriorIso;
    private String semanaSiguienteIso;
    private List<DiaCalendario> dias;
    private List<FilaHora> filas;
    private int totalCitasSemana;
}
