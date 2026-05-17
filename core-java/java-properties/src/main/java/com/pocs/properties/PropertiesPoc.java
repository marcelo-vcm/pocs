package com.pocs.properties;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class PropertiesPoc {

    public static void main(String[] args) throws Exception {
        Properties created = create();
        System.out.println("app.name    = " + created.getProperty("app.name"));
        System.out.println("app.version = " + created.getProperty("app.version"));

        System.out.println();
        Properties stored = storeAndLoad("/tmp/config.properties");
        System.out.println("host = " + stored.getProperty("host"));
        System.out.println("port = " + stored.getProperty("port"));

        System.out.println();
        readSystemProperties();
    }

    static Properties create() {
        Properties props = new Properties();
        props.setProperty("app.name", "My App");
        props.setProperty("app.version", "1.0");
        return props;
    }

    static Properties storeAndLoad(String filePath) throws Exception {
        Properties config = new Properties();
        config.setProperty("host", "localhost");
        config.setProperty("port", "8080");
        config.store(new FileOutputStream(filePath), "App config");

        Properties loaded = new Properties();
        loaded.load(new FileInputStream(filePath));
        return loaded;
    }

    static void readSystemProperties() {
        System.out.println("java.version = " + System.getProperty("java.version"));
        System.out.println("os.name      = " + System.getProperty("os.name"));
    }
}
