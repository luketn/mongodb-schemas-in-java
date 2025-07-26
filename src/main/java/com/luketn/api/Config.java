package com.luketn.api;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class Config extends ResourceConfig {
    public Config() {
        register(Health.class);
    }
}
