package com.luketn.api;

import jakarta.ws.rs.ApplicationPath;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * This configures a package of classes to be scanned for JAX-RS annotated resources and configured
 * with a Jersey filter.
 *
 * Using Jersey as a filter allows us to use JAX-RS annotations like @Path, @GET, @POST, etc.
 * whilst still using Spring Boot's dependency injection and other features.
 *
 * The inclusion of the property "jersey.config.server.response.setStatusOverSendError" ensures that
 * the HTTP status code is set correctly when an error occurs, e.g. when Spring Boot's auth filter
 * returns a 401 Unauthorized response.
 *
 * ref: https://docs.spring.io/spring-boot/how-to/jersey.html
 */
@Component
public class ResourceConfig extends org.glassfish.jersey.server.ResourceConfig {
    public ResourceConfig() {
        packages("com.luketn.api");

        setProperties(Collections.singletonMap("jersey.config.server.response.setStatusOverSendError", true));
    }
}
