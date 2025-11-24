package com.empresa.financiera.domain.model;

/**
 * DTO de configuración para jobs programados.
 * 
 * @param interval Intervalo de ejecución (ej: "5s", "1m", "30s")
 * @param enabled Indica si el job está habilitado
 */
public record JobConfig(String interval, boolean enabled) {
}

