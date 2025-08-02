package com.luketn.api;

import com.luketn.seatemperature.SeaTemperatureService;
import com.luketn.seatemperature.datamodel.BoundingBox;
import com.luketn.seatemperature.datamodel.SeaTemperature;
import com.luketn.util.JsonUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
public class WeatherApiStreamSeedDataTest {
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongodb/mongodb-community-server:8.0.12-ubi9")
            .withClasspathResourceMapping("/seed-data","/tmp/seed-data", BindMode.READ_ONLY);

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
            List<SeaTemperature> seaTemperatures = JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class);
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
            List<SeaTemperature> seaTemperatures = JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class);
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
            List<SeaTemperature> seaTemperatures = JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class);
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

    @Test
    void streamSeaSurfaceTemperatures_clientDisconnect_midStream() throws Exception {
        // given a bounding box that will return many events
        double south = -90;
        double west = -180;
        double north = 90;
        double east = 180;

        var request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "http://localhost:" + port + "/weather/sea/temperature?" +
                                "south=" + south +
                                "&west=" + west +
                                "&north=" + north +
                                "&east=" + east
                ))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        // Open the connection and start reading the stream
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode(), "Expected HTTP status code 200");

        InputStream inputStream = response.body();

        //Create a line reader over the input stream and read the first sse event (line)
        List<SeaTemperature> seaTemperatures;
        try (var reader = new BufferedReader(new InputStreamReader(inputStream), 256)) { // read from the stream 256 bytes at a time
            String sseEvent = reader.readLine();
            assertTrue(sseEvent.startsWith("data: "), "Expected SSE event to start with 'data: '");
            sseEvent = sseEvent.substring(6); // Remove "data: " prefix
            seaTemperatures = JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class);
        }
        // Simulate client disconnect
        inputStream.close();

        // Wait a bit to allow the server to attempt writing and hit the broken pipe
        TimeUnit.MILLISECONDS.sleep(250);

        assertEquals(SeaTemperatureService.batch_size, seaTemperatures.size());
    }

    private static final Logger clientLogger = LoggerFactory.getLogger("ClientTestLog");
    @Test
    void streamSeaSurfaceTemperatures_concurrentClients_partialAndFullReads() throws Exception {
        List<BoundingBox> batches = List.of(
                new BoundingBox(-90.0, 90.0, -180.0, 180.0),
                new BoundingBox(50.7362718024489, 51.25485822311229, 0.8490318059921266, 2.496981024742127), // small bounding box
                new BoundingBox(48.85387273165656, 56.791853873960605, -12.980690002441408, 13.386497497558596) // large bounding box
        );

        // Define the number of concurrent clients and request spread
        final int client_count = 50;
        final double millis_request_spread = 100d;

        Instant startTime = Instant.now();
        AtomicLong dataReceived = new AtomicLong(0);
        AtomicInteger requestCount = new AtomicInteger(0);

        for (BoundingBox batch : batches) {

            // given a bounding box that covers a subset of the data
            double south = batch.south();
            double west = batch.west();
            double north = batch.north();
            double east = batch.east();

            var uri = URI.create(
                    "http://localhost:" + port + "/weather/sea/temperature?" +
                    "south=" + south +
                    "&north=" + north +
                    "&west=" + west +
                    "&east=" + east
            );

            // call the API to warm up the server and ensure the data is loaded
            var warmupRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            var warmupResponse = client.send(warmupRequest, HttpResponse.BodyHandlers.ofString());
            var warmupBody = warmupResponse.body();
            assertEquals(200, warmupResponse.statusCode(), "Expected HTTP status code 200 for warmup request");
            assertNotNull(warmupBody, "Warmup response body should not be null");

            // Use ExecutorService with virtual threads (Java 21+ stable)
            List<Future<List<SeaTemperature>>> futures = new java.util.ArrayList<>(client_count);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int threadNum = 0; threadNum < client_count; threadNum++) {
                    final int tn = threadNum;
                    futures.add(executor.submit(() -> {
                        Thread.currentThread().setName("sse-client-vthread-" + tn);
                        try {
                            int sleepMillis = ((int) (tn / (double) client_count * millis_request_spread)) + RandomUtils.nextInt(50, 100); //spread out the requests over 1 second + some random jitter
                            TimeUnit.MILLISECONDS.sleep(sleepMillis);
                            var request = HttpRequest.newBuilder()
                                    .uri(uri)
                                    .timeout(Duration.ofSeconds(30))
                                    .GET()
                                    .build();
                            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                            assertEquals(200, response.statusCode(), "Expected HTTP status code 200");

                            InputStream inputStream = response.body();
                            List<SeaTemperature> allSeaTemperatures = new java.util.ArrayList<>();
                            try (var reader = new BufferedReader(new InputStreamReader(inputStream), 256)) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        dataReceived.addAndGet(line.length());

                                        String sseEvent = line.substring(6);
                                        List<SeaTemperature> seaTemperatures = JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class);
                                        allSeaTemperatures.addAll(seaTemperatures);

                                        // Odd threads disconnect after first batch
                                        if (tn % 2 == 1) break;
                                    }
                                }
                            }
                            inputStream.close();

                            requestCount.incrementAndGet();
                            clientLogger.info("Thread {} slept for {} before receiving {} sea temperatures", tn, sleepMillis, allSeaTemperatures.size());
                            return allSeaTemperatures;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }
            }

            List<List<SeaTemperature>> results = new java.util.ArrayList<>(client_count);
            for (Future<List<SeaTemperature>> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            // Fetch the expected full set of sea temperatures for this bounding box
            var expectedRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            var expectedResponse = client.send(expectedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, expectedResponse.statusCode());
            String[] sseEvents = expectedResponse.body().split("\n\n");
            List<SeaTemperature> expectedAll = new java.util.ArrayList<>();
            for (String sseEvent : sseEvents) {
                if (sseEvent.startsWith("data: ")) {
                    sseEvent = sseEvent.substring(6);
                }
                expectedAll.addAll(JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class));
            }
            int expectedTotal = expectedAll.size();

            // Fetch the expected first batch
            List<SeaTemperature> expectedFirstBatch = null;
            for (String sseEvent : sseEvents) {
                if (sseEvent.startsWith("data: ")) {
                    sseEvent = sseEvent.substring(6);
                }
                expectedFirstBatch = JsonUtil.fromJsonArray(sseEvent, SeaTemperature.class);
                break;
            }
            int expectedBatchSize = expectedFirstBatch.size();

            // Now check each thread's results
            for (int i = 0; i < results.size(); i++) {
                List<SeaTemperature> actual = results.get(i);
                if (i % 2 == 0) {
                    // Even threads: should have all results
                    assertEquals(expectedTotal, actual.size(), "Thread " + i + " should have all sea temperatures");
                    assertEquals(expectedAll, actual, "Thread " + i + " should have the same sea temperatures as expected");
                } else {
                    // Odd threads: should have only the first batch
                    assertEquals(expectedBatchSize, actual.size(), "Thread " + i + " should have only the first batch");
                    assertEquals(expectedFirstBatch, actual, "Thread " + i + " should have the same first batch as expected");
                }
            }
        }
        Duration duration = Duration.between(startTime, Instant.now());
        Duration durationPerRequest = duration.dividedBy(requestCount.get());
        clientLogger.info("---------------------------------");
        clientLogger.info("------ Client Test Summary ------");
        clientLogger.info("---------------------------------");
        clientLogger.info("Performed {} total requests in {}ms ({}ms/request avg) using {} concurrent clients in {} batches (each with different URIs)", requestCount, duration.toMillis(), durationPerRequest.toMillis(), client_count, batches.size());
        clientLogger.info("Total data received across all clients: {}KB", dataReceived.get() / 1024);
        clientLogger.info("---------------------------------");
    }
}

