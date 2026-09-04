package com.luaspets.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CarritoResponse {

    private List<CarritoItemResponse> items;
    private int totalItems;
    private String total;
    private BigDecimal totalNumero;
    private boolean vacio;
    private String mensaje;
    private boolean exito;
}
