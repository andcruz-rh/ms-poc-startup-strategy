package com.empresa.financiera.application.service;

import com.empresa.financiera.domain.model.AuditLog;
import com.empresa.financiera.infrastructure.repository.AuditRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de negocio para operaciones de auditoría.
 * Maneja la persistencia reactiva de logs de auditoría.
 */
@Slf4j
@ApplicationScoped
public class AuditService {

    private final AuditRepository repository;

    /**
     * Constructor con inyección de dependencias.
     * 
     * @param repository Repositorio de auditoría
     */
    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Crea un log de auditoría de forma reactiva y transaccional.
     * 
     * @param source Origen del log
     * @return Uni<Void> que completa cuando la persistencia finaliza
     */
    public Uni<Void> createLog(String source) {
        log.info("Persistiendo en Postgres desde: [{}]", source);
        
        AuditLog auditLog = new AuditLog("Log desde: " + source);
        
        return Panache.withTransaction(() -> repository.persist(auditLog))
                .replaceWithVoid();
    }
}

