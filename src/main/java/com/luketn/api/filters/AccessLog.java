package com.luketn.api.filters;

import com.luketn.util.SynchronousSse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class AccessLog extends OncePerRequestFilter {
    private static ThreadLocal<SynchronousSse.SseStats> sseStats = ThreadLocal.withInitial(() -> null);

    private static final Logger requestLogger = LoggerFactory.getLogger("RequestLog");
    private static final Logger accessLogger = LoggerFactory.getLogger("AccessLog");

    public static void updateSseStats(SynchronousSse.SseStats value) {
        sseStats.set(value);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        logPreRequest(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long endTime = System.currentTimeMillis();
            logPostRequest(request, response, endTime - startTime);
        }
    }

    public static void logPostRequest(HttpServletRequest request, HttpServletResponse response, long executionTimeMs) {
        String timeTakenSuffix = "(%dms)".formatted(executionTimeMs);
        int statusCode;
        String suffix;
        if (sseStats.get() != null) {
            int eventCount = sseStats.get().eventCount();
            statusCode = sseStats.get().lastStatusCode().value();
            suffix = "(sse: " + eventCount + " event" + (eventCount == 1 ? "" : "s") + ", status: " + statusCode + ")" + " " + timeTakenSuffix;
        } else {
            statusCode = response.getStatus();
            suffix = "(status: " + statusCode + ") " + timeTakenSuffix;
        }
        Level level;
        if (statusCode >= 400 && statusCode < 500) {
            level = Level.WARN;
        } else if (statusCode >= 500) {
            level = Level.ERROR;
        } else {
            level = Level.INFO;
        }
        logContext(accessLogger, request, suffix, level);
    }

    public static void logPreRequest(HttpServletRequest request) {
        logContext(requestLogger, request, "", Level.INFO);
    }

    public static void logContext(Logger logger, HttpServletRequest request, String suffix, Level level) {
        var uri = request.getRequestURI();
        var query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        var method = request.getMethod();
        logger.atLevel(level).log("{} {}{} {}", method, uri, query, suffix);
    }

}
