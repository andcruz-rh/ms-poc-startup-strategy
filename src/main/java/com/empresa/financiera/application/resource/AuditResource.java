package com.empresa.financiera.application.resource;

import com.empresa.financiera.infrastructure.repository.AuditRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Recurso REST para operaciones de auditoría.
 */
@ApplicationScoped
@Path("/api/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    private final AuditRepository repository;

    /**
     * Constructor con inyección de dependencias (STRICT MODE).
     * 
     * @param repository Repositorio de auditoría
     */
    public AuditResource(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene todos los logs de auditoría.
     * 
     * @return Lista de logs de auditoría
     */
    @GET
    public Uni<Response> getAllLogs() {
        return repository.listAll()
                .map(logs -> Response.ok(logs).build());
    }

    /**
     * Obtiene el conteo de logs de auditoría.
     * 
     * @return Conteo de logs
     */
    @GET
    @Path("/count")
    public Uni<Response> getCount() {
        return repository.count()
                .map(count -> Response.ok(new CountResponse(count)).build());
    }

    /**
     * DTO para la respuesta de conteo.
     */
    public record CountResponse(Long count) {
    }
}

