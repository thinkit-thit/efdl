package com.ffs.efdl.config;

import org.bson.Document;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Sonde de santé MongoDB compatible avec le serveur actuel (MongoDB 4.2) :
 * la sonde standard utilise la commande {@code hello}, inconnue avant MongoDB 4.4.2.
 * Le nom du bean remplace la sonde automatique de Spring Boot.
 */
@Configuration
public class MongoHealthConfig {

    @Bean
    HealthIndicator mongoHealthIndicator(MongoTemplate mongoTemplate) {
        return new AbstractHealthIndicator() {
            @Override
            protected void doHealthCheck(Health.Builder builder) {
                Document result = mongoTemplate.executeCommand(new Document("ping", 1));
                builder.up().withDetail("database", mongoTemplate.getDb().getName()).withDetail("ping", result.get("ok"));
            }
        };
    }
}
