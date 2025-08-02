package com.luketn.api;

import com.luketn.dataaccess.mongodb.MongoDBProvider;
import com.luketn.util.JsonUtil;
import com.luketn.util.SynchronousSse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Simulate a database error during the streaming of sea surface temperatures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ExtendWith(SpringExtension.class)
@Testcontainers
public class WeatherApiStreamErrorTest {

    @MockitoBean
    MongoDBProvider mongoDBProvider;

    @LocalServerPort
    protected int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void streamSeaSurfaceTemperatures_database_error() throws IOException, InterruptedException {
        // given a bounding box that covers a subset of the data
        // http://localhost:8086/weather/sea/temperature?queryType=BoundingBox&south=50.7362718024489&west=0.8490318059921266&north=51.25485822311229&east=2.496981024742127
        double south = 50.7362718024489;
        double west = 0.8490318059921266;
        double north = 51.25485822311229;
        double east = 2.496981024742127;

        // and a mock MongoDB provider that simulates a database error
        when(mongoDBProvider.getMongoDatabase())
                .thenThrow(new RuntimeException("Simulated database error"));

        // when the sea surface temperatures are requested
        var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(
                    "http://localhost:" + port + "/weather/sea/temperature?" +
                    "south=" + south +
                    "&west=" + west +
                    "&north=" + north +
                    "&east=" + east
                ))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then should return SSE error event
        assertEquals(200, response.statusCode(), "Expected HTTP status code 400");
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        //trim off SSE prefix
        if (body.startsWith("data: ")) {
            body = body.substring(6);
        }
        SynchronousSse.ErrorEvent errorEvent = JsonUtil.fromJson(body, SynchronousSse.ErrorEvent.class);
        assertNotNull(errorEvent, "Error event should not be null");
        assertNotNull(errorEvent.id(), "Error event ID should not be null");
        assertEquals("An unexpected error occurred while streaming sea surface temperatures.", errorEvent.error(),
                "Expected error message for missing parameters");
    }

    @Test
    public void testMissingParameters() throws IOException, InterruptedException {
        // given a request with missing query string parameters
        var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/weather/sea/temperature"))
                .GET()
                .build();

        // when the request is sent
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then should return 400 Bad Request
        assertEquals(200, response.statusCode(), "Expected HTTP status code 400");
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        //trim off SSE prefix
        if (body.startsWith("data: ")) {
            body = body.substring(6);
        }
        SynchronousSse.ErrorEvent errorEvent = JsonUtil.fromJson(body, SynchronousSse.ErrorEvent.class);
        assertNotNull(errorEvent, "Error event should not be null");
        assertNotNull(errorEvent.id(), "Error event ID should not be null");
        assertEquals("For BoundingBox query type, north, south, east, and west must all be supplied.", errorEvent.error(),
                "Expected error message for missing parameters");
    }
}