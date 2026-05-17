package com.pocs.properties;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesPocTest {

    @Test
    void create_shouldReturnPropertiesWithExpectedValues() {
        Properties props = PropertiesPoc.create();

        assertEquals("My App", props.getProperty("app.name"));
        assertEquals("1.0", props.getProperty("app.version"));
    }

    @Test
    void create_shouldReturnNullForMissingKey() {
        Properties props = PropertiesPoc.create();

        assertNull(props.getProperty("app.missing"));
    }


    @Test
    void storeAndLoad_shouldPersistAndRetrieveValues() throws Exception {
        Properties props = PropertiesPoc.storeAndLoad("/tmp/test-config.properties");

        assertEquals("localhost", props.getProperty("host"));
        assertEquals("8080", props.getProperty("port"));
    }

    @Test
    void readSystemProperties_shouldNotThrow() {
        assertDoesNotThrow(() -> PropertiesPoc.readSystemProperties());
    }
}
