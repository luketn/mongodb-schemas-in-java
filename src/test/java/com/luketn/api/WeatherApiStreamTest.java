package com.luketn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.luketn.seatemperature.datamodel.SeaTemperature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static com.luketn.dataaccess.mongodb.WeatherDataAccess.COLLECTION_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.apache.commons.lang3.ArrayUtils.toArray;

/**
 * Tests the Weather API endpoints for streaming sea surface temperatures.
 * (loads test data from the seed-data directory into the MongoDB container)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ExtendWith(SpringExtension.class)
@Testcontainers
public class WeatherApiStreamTest {
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongodb/mongodb-community-server:8.0.12-ubi9")
            .withClasspathResourceMapping("/seed-data","/tmp/seed-data", BindMode.READ_ONLY);

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeAll
    static void init() throws IOException, InterruptedException {
        ExecResult execResult = mongoDBContainer.execInContainer(
                ExecConfig.builder()
                        .workDir("/tmp/seed-data")
                        .command(
                                toArray(
                                    "mongoimport",
                                    "-d", "testdb",
                                    "-c", COLLECTION_NAME,
                                    "--jsonArray",
                                    "sample_weatherdata.data.json"
                                 )
                        ).build()
        );
        int exitCode = execResult.getExitCode();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to import seed data into MongoDB container, exit code: %d\nstdout: %s\nstderr: %s".formatted(exitCode, execResult.getStdout(), execResult.getStderr()));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("mongodb-schema.dataaccess.mongodb.connection-string", mongoDBContainer::getConnectionString);
        registry.add("mongodb-schema.dataaccess.mongodb.database-name", ()->"testdb");
    }

    @LocalServerPort
    protected int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void streamSeaSurfaceTemperatures_bounding_small() throws IOException, InterruptedException {
        // given a bounding box that covers a subset of the data
        // http://localhost:8086/weather/sea/temperature?queryType=BoundingBox&south=50.7362718024489&west=0.8490318059921266&north=51.25485822311229&east=2.496981024742127
        double south = 50.7362718024489;
        double west = 0.8490318059921266;
        double north = 51.25485822311229;
        double east = 2.496981024742127;

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

        // then should return 82 sea surface temperature events
        assertEquals(200, response.statusCode(), "Expected HTTP status code 200");
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        String[] sseEvents = body.split("\n\n");

        assertEquals(1, sseEvents.length, "Expected 1 SSE event in the response");
        int countTotalMeasurements = 0;
        for (String sseEvent : sseEvents) {
            //strip the line starting with "data: "
            if (sseEvent.startsWith("data: ")) {
                sseEvent = sseEvent.substring(6);
            }
            List<SeaTemperature> seaTemperatures = objectMapper.readValue(sseEvent, objectMapper.getTypeFactory().constructCollectionType(List.class, SeaTemperature.class));
            for (SeaTemperature seaTemperature : seaTemperatures) {
                countTotalMeasurements++;

                //confirm that the sea surface temperature is within the expected bounded box
                assertTrue(seaTemperature.lat() >= south && seaTemperature.lat() <= north,
                        "Latitude %f should be within bounds [%f, %f]".formatted(seaTemperature.lat(), south, north));
                assertTrue(seaTemperature.lon() >= west && seaTemperature.lon() <= east,
                        "Longitude %f should be within bounds [%f, %f]".formatted(seaTemperature.lon(), west, east));
            }
        }
        assertEquals(6, countTotalMeasurements, "Expected 6 sea surface temperature measurements in total");
    }

    @Test
    void streamSeaSurfaceTemperatures_bounding_large() throws IOException, InterruptedException {
        // given a bounding box that covers a subset of the data
        double south = 48.85387273165656;
        double west = -12.980690002441408;
        double north = 56.791853873960605;
        double east = 13.386497497558596;

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

        // then should return 82 sea surface temperature events
        assertEquals(200, response.statusCode(), "Expected HTTP status code 200");
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        String[] sseEvents = body.split("\n\n");

        assertEquals(24, sseEvents.length, "Expected 2 SSE events in the response");
        int countTotalMeasurements = 0;
        for (String sseEvent : sseEvents) {
            //strip the line starting with "data: "
            if (sseEvent.startsWith("data: ")) {
                sseEvent = sseEvent.substring(6);
            }
            List<SeaTemperature> seaTemperatures = objectMapper.readValue(sseEvent, objectMapper.getTypeFactory().constructCollectionType(List.class, SeaTemperature.class));
            for (SeaTemperature seaTemperature : seaTemperatures) {
                countTotalMeasurements++;

                //confirm that the sea surface temperature is within the expected bounded box
                assertTrue(seaTemperature.lat() >= south && seaTemperature.lat() <= north,
                        "Latitude %f should be within bounds [%f, %f]".formatted(seaTemperature.lat(), south, north));
                assertTrue(seaTemperature.lon() >= west && seaTemperature.lon() <= east,
                        "Longitude %f should be within bounds [%f, %f]".formatted(seaTemperature.lon(), west, east));
            }
        }
        assertEquals(238, countTotalMeasurements, "Expected 558 sea surface temperature measurements in total");
    }

    @Test
    void streamSeaSurfaceTemperatures_bounding_earth() throws IOException, InterruptedException {
        // given a bounding box that covers earth
        double south = -90;
        double west = -180;
        double north = 90;
        double east = 180;

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

        // then should return all sea surface temperature events in the sample data
        assertEquals(200, response.statusCode(), "Expected HTTP status code 200");
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        String[] sseEvents = body.split("\n\n");

        assertEquals(63, sseEvents.length, "Expected 63 SSE events in the response");
        int countTotalMeasurements = 0;
        for (String sseEvent : sseEvents) {
            if (sseEvent.startsWith("data: ")) {
                sseEvent = sseEvent.substring(6);
            }
            List<SeaTemperature> seaTemperatures = objectMapper.readValue(sseEvent, objectMapper.getTypeFactory().constructCollectionType(List.class, SeaTemperature.class));
            for (SeaTemperature seaTemperature : seaTemperatures) {
                countTotalMeasurements++;

                //confirm that the sea surface temperature is within the expected bounded box
                assertTrue(seaTemperature.lat() >= south && seaTemperature.lat() <= north,
                        "Latitude %f should be within bounds [%f, %f]".formatted(seaTemperature.lat(), south, north));
                assertTrue(seaTemperature.lon() >= west && seaTemperature.lon() <= east,
                        "Longitude %f should be within bounds [%f, %f]".formatted(seaTemperature.lon(), west, east));
            }
        }
        assertEquals(624, countTotalMeasurements, "Expected 989 sea surface temperature measurements in total");
    }
}