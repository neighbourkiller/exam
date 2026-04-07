package com.ekusys.exam.common.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayLayoutTest {

    @Test
    void baseApplicationShouldUseFlywayAsOnlyInitializationEntry() throws Exception {
        String yaml = new String(
            new ClassPathResource("application.yaml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );

        assertTrue(yaml.contains("mode: never"));
        assertTrue(yaml.contains("locations: classpath:db/migration"));
        assertTrue(yaml.contains("baseline-version: 3"));
    }

    @Test
    void devProfileShouldAppendDevSeedLocation() throws Exception {
        String yaml = new String(
            new ClassPathResource("application-dev.yaml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );

        assertTrue(yaml.contains("classpath:db/migration,classpath:db/dev-seed"));
    }

    @Test
    void legacySqlFilesShouldBeMarkedAsReferenceOnly() throws Exception {
        String schema = new String(
            new ClassPathResource("schema.sql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        String data = new String(
            new ClassPathResource("data.sql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );

        assertTrue(schema.contains("Legacy reference only."));
        assertTrue(data.contains("Legacy reference only."));
    }
}
