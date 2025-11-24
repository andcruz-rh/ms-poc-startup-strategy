package com.empresa.financiera.application.startup;

import com.empresa.financiera.application.service.AuditService;
import com.empresa.financiera.infrastructure.service.MockParameterService;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduler;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurador de jobs programados que se ejecuta al iniciar la aplicación.
 * Implementa la Estrategia 3: Inicialización @Startup para jobs dinámicos.
 */
@Slf4j
@ApplicationScoped
public class StartupJobConfigurator {

    private static final String JOB_NAME = "pg-startup-job";
    private static final String FALLBACK_INTERVAL = "1m";
    private static final String JOB_SOURCE = "StartupJobConfigurator";

    private static final long INITIAL_DELAY_MS = 1000L;

    private final MockParameterService mockParameterService;
    private final AuditService auditService;
    private final Scheduler scheduler;
    private final Vertx vertx;

    /**
     * Constructor con inyección de dependencias (STRICT MODE).
     * 
     * @param mockParameterService Servicio para obtener configuración
     * @param auditService Servicio de auditoría
     * @param scheduler Scheduler de Quarkus para crear jobs dinámicos
     * @param vertx Instancia de Vert.x para timers asíncronos
     */
    public StartupJobConfigurator(
            MockParameterService mockParameterService,
            AuditService auditService,
            Scheduler scheduler,
            Vertx vertx) {
        this.mockParameterService = mockParameterService;
        this.auditService = auditService;
        this.scheduler = scheduler;
        this.vertx = vertx;
    }

    /**
     * Se ejecuta al iniciar la aplicación usando @Startup de Quarkus.
     * Con quarkus.scheduler.start-mode=forced, el Scheduler se inicia automáticamente.
     * Usa un pequeño delay con Timer de Vert.x para asegurar que el Scheduler esté completamente listo.
     */
    @Startup
    void init() {
        log.info("@Startup: Configurando jobs programados dinámicamente...");

        // Pequeño delay para asegurar que el Scheduler esté completamente iniciado
        vertx.setTimer(INITIAL_DELAY_MS, id -> {
            log.info("Iniciando configuración de jobs...");
            configureJob();
        });
    }

    /**
     * Configura el job programado obteniendo la configuración y creándolo si está habilitado.
     */
    private void configureJob() {
        mockParameterService.fetchConfig()
                .subscribe().with(
                        config -> {
                            String interval = config.enabled() ? config.interval() : null;
                            if (interval != null) {
                                log.info("Configuración obtenida: intervalo={}, enabled={}. Creando job dinámico...",
                                        interval, config.enabled());
                                createScheduledJob(interval);
                            } else {
                                log.warn("Job deshabilitado en configuración. No se creará el job programado.");
                            }
                        },
                        failure -> {
                            log.error("Error al obtener configuración. Usando fallback: intervalo={}", FALLBACK_INTERVAL, failure);
                            createScheduledJob(FALLBACK_INTERVAL);
                        }
                );
    }

    /**
     * Crea un job programado con el intervalo especificado.
     * Usa .setAsyncTask() para operaciones reactivas de BD (CRÍTICO para evitar bloqueo del Event Loop).
     * 
     * @param interval Intervalo de ejecución (ej: "4s", "1m")
     */
    private void createScheduledJob(String interval) {
        // Verificación de seguridad: asegurar que el Scheduler esté iniciado
        if (!scheduler.isStarted()) {
            log.error("Scheduler no está iniciado. No se puede crear el job '{}'. " +
                    "Verifica que quarkus.scheduler.start-mode=forced esté configurado.", JOB_NAME);
            return;
        }

        log.info("Configurando job '{}' con intervalo: {}", JOB_NAME, interval);

        scheduler.newJob(JOB_NAME)
                .setInterval(interval)
                .setAsyncTask(uni -> {
                    log.debug("Ejecutando tarea asíncrona del job '{}'", JOB_NAME);
                    return auditService.createLog(JOB_SOURCE);
                })
                .schedule();

        log.info("Job '{}' programado exitosamente con intervalo: {}", JOB_NAME, interval);
    }
}
