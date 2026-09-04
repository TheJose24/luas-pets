package com.luaspets.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CarritoItemResponse {

    private Long productoId;
    private String nombre;
    private String imagenUrl;
    private String precioUnitario;
    private BigDecimal precioUnitarioNumero;
    private Integer cantidad;
    private Integer stockDisponible;
    private String subtotal;
}
