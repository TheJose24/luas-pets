package com.luaspets.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class NotificacionListaResponse {

    private List<NotificacionResponse> items;
    private long noLeidas;
}
