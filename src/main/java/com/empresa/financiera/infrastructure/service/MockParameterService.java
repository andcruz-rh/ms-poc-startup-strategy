package com.empresa.financiera.infrastructure.service;

import com.empresa.financiera.domain.model.JobConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio Mock que simula la obtención de parámetros de configuración
 * desde un servicio externo.
 */
@Slf4j
@ApplicationScoped
public class MockParameterService {

    /**
     * Simula una llamada remota para obtener la configuración del job.
     * 
     * @return Uni<JobConfig> con la configuración simulada
     */
    public Uni<JobConfig> fetchConfig() {
        return Uni.createFrom().item(() -> {
            log.debug("Simulando llamada remota para obtener configuración");
            int randomSeconds = 10 + (int) (Math.random() * 21); // entre 10 y 30 segundos (inclusive 10, hasta 30)
            String interval = randomSeconds + "s";
            return new JobConfig(interval, true);
        });
    }
}

