package com.luaspets.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CitaCalendario {

    private Long id;
    private String horaTexto;
    private String mascotaNombre;
    private String mascotaInicial;
    private String mascotaFotoUrl;
    private String especieRaza;
    private String propietarioNombre;
    private String doctorNombre;
    private String motivo;
    private String estadoTexto;
    private String estadoVariante;
    private String url;
    private boolean fueraDeRango;
}
