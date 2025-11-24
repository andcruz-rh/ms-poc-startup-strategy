# PoC: Estrategia de Configuración Dinámica de Jobs con @Startup

## Descripción

Este proyecto implementa una **Prueba de Concepto (PoC)** para validar la **Estrategia 3: Inicialización @Startup para jobs programados dinámicos** en Quarkus. La estrategia permite configurar jobs programados de forma dinámica una sola vez al iniciar la aplicación, obteniendo la configuración desde un servicio externo.

## Objetivo

Configurar jobs programados dinámicamente durante el arranque de la aplicación usando eventos de ciclo de vida (`@Startup`), sin necesidad de métodos anotados con `@Scheduled`.

## Arquitectura

### Stack Tecnológico

- **Framework**: Quarkus 3.20.3 (Red Hat Build)
- **Programación Reactiva**: Mutiny
- **Persistencia**: Hibernate Reactive + Panache
- **Base de Datos**: PostgreSQL (Dev Services con Testcontainers)
- **Scheduler**: Quarkus Scheduler con modo forzado

### Estructura del Proyecto

```
src/main/java/com/empresa/financiera/
├── application/
│   ├── startup/
│   │   └── StartupJobConfigurator.java    # Componente principal
│   ├── service/
│   │   └── AuditService.java
│   └── resource/
│       └── AuditResource.java
├── domain/
│   └── model/
│       ├── JobConfig.java
│       └── AuditLog.java
└── infrastructure/
    ├── repository/
    │   └── AuditRepository.java
    └── service/
        └── MockParameterService.java
```

## Estrategia de Configuración del Scheduler

### 1. Configuración Crítica: `start-mode=forced`

**Archivo**: `src/main/resources/application.properties`

```properties
# Configuración del Scheduler (CRÍTICO para jobs dinámicos)
# En Quarkus 3.20+, el Scheduler no se inicia automáticamente a menos que haya métodos @Scheduled
# o se configure explícitamente con start-mode=forced
quarkus.scheduler.start-mode=forced
```

**¿Por qué es necesario?**

En Quarkus 3.20+, el Scheduler solo se inicia automáticamente si detecta métodos anotados con `@Scheduled`. Para jobs dinámicos creados programáticamente, es **obligatorio** configurar `start-mode=forced` para que el Scheduler se inicie independientemente de la presencia de métodos `@Scheduled`.

### 2. Inicialización con `@Startup`

**Componente**: `StartupJobConfigurator`

```java
@Slf4j
@ApplicationScoped
public class StartupJobConfigurator {

    private final Scheduler scheduler;
    private final Vertx vertx;

    @Startup
    void init() {
        log.info("@Startup: Configurando jobs programados dinámicamente...");
        
        // Pequeño delay para asegurar que el Scheduler esté completamente iniciado
        vertx.setTimer(INITIAL_DELAY_MS, id -> {
            log.info("Iniciando configuración de jobs...");
            configureJob();
        });
    }
}
```

**Características clave:**

- **`@Startup`**: Se ejecuta automáticamente durante el arranque de la aplicación
- **Timer de Vert.x**: Diferir la configuración 1 segundo para asegurar que el Scheduler esté completamente iniciado
- **No bloqueante**: El método `init()` termina inmediatamente, permitiendo que Quarkus complete el arranque

### 3. Verificación del Estado del Scheduler

Antes de crear un job dinámico, se verifica que el Scheduler esté iniciado:

```java
private void createScheduledJob(String interval) {
    // Verificación de seguridad
    if (!scheduler.isStarted()) {
        log.error("Scheduler no está iniciado. Verifica que quarkus.scheduler.start-mode=forced esté configurado.");
        return;
    }

    scheduler.newJob(JOB_NAME)
            .setInterval(interval)
            .setAsyncTask(uni -> {
                return auditService.createLog(JOB_SOURCE);
            })
            .schedule();
}
```

### 4. Uso de `setAsyncTask()` para Operaciones Reactivas

**CRÍTICO**: Para jobs que realizan operaciones de base de datos reactivas, **siempre** usar `.setAsyncTask()`:

```java
scheduler.newJob(JOB_NAME)
    .setInterval(interval)
    .setAsyncTask(uni -> {
        // Operación reactiva que retorna Uni<T>
        return auditService.createLog(JOB_SOURCE);
    })
    .schedule();
```

**¿Por qué es crítico?**

- Evita bloquear el Event Loop de Vert.x
- Permite que el Driver Reactivo de PostgreSQL funcione correctamente
- Sin esto, las operaciones reactivas fallarán con errores de contexto

## Flujo de Ejecución

```
1. Aplicación inicia
   ↓
2. @Startup se ejecuta (StartupJobConfigurator.init())
   ↓
3. Timer de Vert.x difiere configuración (1 segundo)
   ↓
4. Se obtiene configuración desde MockParameterService
   ↓
5. Se verifica que scheduler.isStarted() == true
   ↓
6. Se crea el job dinámico con scheduler.newJob()
   ↓
7. Job se programa y ejecuta periódicamente
```

## Ejemplo de Configuración Dinámica

```java
private void configureJob() {
    mockParameterService.fetchConfig()
            .subscribe().with(
                    config -> {
                        String interval = config.enabled() ? config.interval() : null;
                        if (interval != null) {
                            createScheduledJob(interval);
                        } else {
                            log.warn("Job deshabilitado en configuración.");
                        }
                    },
                    failure -> {
                        log.error("Error al obtener configuración. Usando fallback.");
                        createScheduledJob(FALLBACK_INTERVAL);
                    }
            );
}
```

## Checklist de Implementación

Para implementar esta estrategia en tu proyecto, asegúrate de:

- [ ] Configurar `quarkus.scheduler.start-mode=forced` en `application.properties`
- [ ] Usar `@Startup` en lugar de `@PostConstruct` o `@Observes StartupEvent`
- [ ] Diferir la configuración con `vertx.setTimer()` (1 segundo mínimo)
- [ ] Verificar `scheduler.isStarted()` antes de crear jobs
- [ ] Usar `.setAsyncTask()` para operaciones reactivas de BD
- [ ] Manejar errores y fallbacks apropiadamente

## Testing

Los tests de integración validan:

1. **Creación del job**: Verifica que el job se registra correctamente
2. **Persistencia**: Valida que el job ejecuta y persiste datos en PostgreSQL
3. **API REST**: Confirma que los datos son accesibles vía endpoints REST

**Ejecutar tests:**

```bash
mvn test
```

## Problemas Comunes y Soluciones

### Error: `Scheduler was not started`

**Causa**: El Scheduler no se ha iniciado cuando se intenta crear el job.

**Solución**:
1. Verificar que `quarkus.scheduler.start-mode=forced` esté configurado
2. Aumentar el delay inicial si es necesario
3. Verificar `scheduler.isStarted()` antes de crear el job

### Error: `No current Vertx context found`

**Causa**: Operaciones reactivas ejecutadas fuera de un contexto de Vert.x.

**Solución**: Usar `.setAsyncTask()` para operaciones reactivas en jobs programáticos.

## Referencias

- [Quarkus Scheduler Guide](https://quarkus.io/guides/scheduler)
- [Quarkus Reactive Guide](https://quarkus.io/guides/reactive)
- [Red Hat Build of Quarkus Documentation](https://access.redhat.com/documentation/en-us/red_hat_build_of_quarkus/)

## Licencia

Este proyecto es una Prueba de Concepto (PoC) para validación técnica.

---

**Nota**: Esta estrategia es específica para Quarkus 3.20+ y requiere configuración explícita del Scheduler debido a cambios en el comportamiento por defecto.

