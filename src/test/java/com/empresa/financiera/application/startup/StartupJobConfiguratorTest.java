package com.empresa.financiera.application.startup;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Test de integración para StartupJobConfigurator.
 * Valida que el job se crea correctamente y persiste datos en PostgreSQL.
 * 
 * Nota: Quarkus iniciará automáticamente un contenedor PostgreSQL usando Testcontainers
 * gracias a Dev Services configurado en application.properties.
 */
@QuarkusTest
@DisplayName("Tests de integración para StartupJobConfigurator")
class StartupJobConfiguratorTest {

    private static final String JOB_NAME = "pg-startup-job";
    private static final String EXPECTED_MESSAGE_PREFIX = "Log desde: StartupJobConfigurator";
    private static final Duration MAX_WAIT_TIME = Duration.ofSeconds(15); // Aumentado para dar tiempo al job (intervalo 4s + delay inicial)
    private static final String API_AUDIT_PATH = "/api/audit";
    private static final String API_AUDIT_COUNT_PATH = "/api/audit/count";

    @Inject
    Scheduler scheduler;

    @Test
    @DisplayName("Debe verificar que el job programado existe y persiste datos en PostgreSQL")
    void testPostgresPersistence() {
        // Verificación de Job: Espera a que el job se configure (puede tomar hasta 1-2 segundos por el delay)
        // En Quarkus 3.x, usamos getScheduledJobs() para obtener todos los jobs programados
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    List<Trigger> scheduledJobs = scheduler.getScheduledJobs();
                    assertThat(scheduledJobs)
                            .as("Debe existir al menos un job programado")
                            .isNotEmpty();
                    
                    // Verifica que el job con el nombre esperado está en la lista
                    boolean jobExists = scheduledJobs.stream()
                            .anyMatch(trigger -> JOB_NAME.equals(trigger.getId()));
                    assertThat(jobExists)
                            .as("El job '%s' debe estar programado", JOB_NAME)
                            .isTrue();
                });

        // Verificación de Datos usando REST API (evita problemas con contexto de Vert.x)
        // Espera hasta 15 segundos para que el job ejecute y persista datos
        Awaitility.await()
                .atMost(MAX_WAIT_TIME)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Response response = given()
                            .when()
                            .get(API_AUDIT_COUNT_PATH);
                    
                    // Log the response body explicitly
                    System.out.println("Response Body: " + response.getBody().asString());
                    System.out.println("Response Status: " + response.getStatusCode());
                    
                    response.then()
                            .statusCode(200)
                            .body("count", greaterThan(0));
                });

        // Extra: Recupera los logs y verifica el mensaje usando REST API
        given()
                .when()
                .get(API_AUDIT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0))
                .body("[0].id", notNullValue())
                .body("[0].message", notNullValue())
                .body("[0].createdAt", notNullValue())
                .body("[0].message", org.hamcrest.Matchers.startsWith(EXPECTED_MESSAGE_PREFIX));
    }

    @Test
    @DisplayName("Debe obtener logs de auditoría mediante REST API usando Rest Assured")
    void testGetAuditLogsViaRestApi() {
        // Espera a que el job ejecute y persista al menos un log
        Awaitility.await()
                .atMost(MAX_WAIT_TIME)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    given()
                            .when()
                            .get(API_AUDIT_COUNT_PATH)
                            .then()
                            .statusCode(200)
                            .body("count", greaterThan(0)); // Usar int en lugar de Long para compatibilidad con JSON
                });

        // Verifica que el endpoint GET /api/audit retorna una lista de logs
        given()
                .when()
                .get(API_AUDIT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0))
                .body("[0].id", notNullValue())
                .body("[0].message", notNullValue())
                .body("[0].createdAt", notNullValue());
    }

    @Test
    @DisplayName("Debe obtener el conteo de logs mediante REST API usando Rest Assured")
    void testGetAuditLogsCountViaRestApi() {
        // Espera a que el job ejecute y persista al menos un log
        Awaitility.await()
                .atMost(MAX_WAIT_TIME)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    given()
                            .when()
                            .get(API_AUDIT_COUNT_PATH)
                            .then()
                            .statusCode(200)
                            .body("count", greaterThan(0)); // Usar int en lugar de Long para compatibilidad con JSON
                });

        // Verifica el endpoint de conteo
        given()
                .when()
                .get(API_AUDIT_COUNT_PATH)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("count", greaterThan(0)); // Usar int en lugar de Long para compatibilidad con JSON
    }
}

