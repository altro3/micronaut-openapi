package io.micronaut.openapi.quarkus;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.event.Observes;

public class StaticResources {

    void installRoute(@Observes StartupEvent startupEvent, Router router) {
        router.route()
            .path("/swagger-ui/*")
            .handler(StaticHandler.create("META-INF/swagger/views/swagger-ui/"));

        router.route()
            .path("/swagger/*")
            .handler(StaticHandler.create("META-INF/swagger"));
    }
}