package com.luketn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthApi {
    @GetMapping(produces = "text/plain")
    public String check() {
        return "OK";
    }
}
