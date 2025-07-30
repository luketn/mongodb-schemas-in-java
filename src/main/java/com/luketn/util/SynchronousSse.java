package com.luketn.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SynchronousSse {
    private static ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final HttpServletResponse response;

    public static SynchronousSse forResponse(HttpServletResponse response) {
        return new SynchronousSse(response);
    }

    private SynchronousSse(HttpServletResponse response) {
        this.response = response;
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Type", "text/event-stream; charset=UTF-8");
        response.addHeader("Cache-Control", "no-cache");
        response.addHeader("X-Accel-Buffering", "no");
        try {
            response.flushBuffer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendEvent(Object data) {
        try {
            response.getWriter().print("data: " + objectMapper.writeValueAsString(data) + "\n\n");
            response.flushBuffer();
        } catch (IOException e) {
            throw new RuntimeException("Error sending SSE event: " + e.getMessage(), e);
        }
    }
}
