package com.luketn.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;

public class SynchronousSse {
    private static final Logger log = LoggerFactory.getLogger(SynchronousSse.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final HttpServletResponse response;

    public static SynchronousSse forResponse(HttpServletResponse response) {
        return new SynchronousSse(response);
    }

    private SynchronousSse(HttpServletResponse response) {
        this.response = response;
        response.setStatus(HttpStatus.OK.value());
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
            throw new SseBrokenPipe();
        }
    }

    public void error(String clientMessage) {
        error(null, clientMessage);
    }

    public void error(Exception e, String clientMessage) {
        // create a UID for the error, write this to the log with the full error stack trace. then write to the SSE output a general error message including the uid
        String errorUid = java.util.UUID.randomUUID().toString();
        log.error("SSE Error UID: {}", errorUid, e);

        ErrorEvent errorEvent = new ErrorEvent(errorUid, clientMessage);
        sendEvent(errorEvent);
    }

    public record ErrorEvent(String id, String error) {}

    public static class SseBrokenPipe extends RuntimeException {}
}
