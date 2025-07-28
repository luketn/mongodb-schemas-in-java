package com.luketn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

@SpringBootApplication
public class MongoSchemasMain {
    private static final Logger log = LoggerFactory.getLogger(MongoSchemasMain.class);

    public static void main(String[] args) {
        log.info("Mongo Schemas Demo API is launching! (java version = {})", System.getProperty("java.version"));

        SpringApplication app = new SpringApplication(MongoSchemasMain.class);

        //These properties are important, should not be overridden and must be set before the application starts
        app.setDefaultProperties(Map.of(
                "spring.threads.virtual.enabled", true
        ));

        ConfigurableApplicationContext runningAppContext = app.run();

        int port = runningAppContext.getEnvironment().getProperty("server.port", Integer.class, 8080);
        String contextPath = runningAppContext.getEnvironment().getProperty("server.servlet.context-path", "");
        log.info("Mongo Schemas Demo API is running! (http://localhost:{}{}/health)", port, contextPath);
    }
}