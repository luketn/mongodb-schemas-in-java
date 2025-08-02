package com.luketn.util;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;

public class SynchronousSse {
    private static final Logger log = LoggerFactory.getLogger(SynchronousSse.class);

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
            response.getWriter().print("data: " + JsonUtil.toJson(data) + "\n\n");
            response.flushBuffer();
        } catch (IOException e) {
            throw new SseBrokenPipe();
        }
    }

    public void error(HttpStatusCode statusCode, String clientMessage) {
        error(statusCode, null, clientMessage);
    }

    public void error(HttpStatusCode statusCode, Exception e, String clientMessage) {
        // create a UID for the error, write this to the log with the full error stack trace. then write to the SSE output a general error message including the uid
        String errorUid = java.util.UUID.randomUUID().toString();
        log.error("SSE Error UID: {}", errorUid, e);

        ErrorEvent errorEvent = new ErrorEvent(statusCode.toString(), errorUid, clientMessage);
        try {
            sendEvent(errorEvent);
        } catch (Exception _) {} // swallow errors trying to send the error event to the client, as we are already in an error state and the socket may be broken
    }

    public record ErrorEvent(String status, String id, String error) {}

    public static class SseBrokenPipe extends RuntimeException {}
}
