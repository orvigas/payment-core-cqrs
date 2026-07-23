package com.orvigas.config;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the MongoDB client with standard UUID representation.
 *
 * <p>Without this, Axon's MongoDB event store and the idempotency repository
 * fail to encode/decode UUID values stored in aggregate identifiers and
 * domain objects.
 *
 * @author orvigas@gmail.com
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    /**
     * Sets the UUID representation to STANDARD so that {@code java.util.UUID}
     * values are encoded as BSON binary subtype 0x04 instead of the legacy
     * Java-specific encoding.
     *
     * @return a customizer that applies the standard UUID representation
     */
    @Bean
    MongoClientSettingsBuilderCustomizer uuidRepresentationCustomizer() {
        return builder -> builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
