package com.luaspets.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ProductoCard {

    private Long id;
    private String nombre;
    private String descripcionCorta;
    private String categoria;
    private String categoriaMayuscula;
    private String imagenUrl;
    private String precioTexto;
    private BigDecimal precioNumero;
    private Integer stock;
    private boolean disponible;
    private String etiquetaStock;
    private String varianteStock;
}
