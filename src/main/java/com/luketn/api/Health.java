package com.luketn.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.springframework.stereotype.Service;

@Service
@Path("/health")
public class Health {
    @GET
    @Produces("text/plain")
    public String check() {
        return "OK";
    }
}
