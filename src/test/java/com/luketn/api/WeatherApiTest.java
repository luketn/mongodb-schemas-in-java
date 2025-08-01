package com.luketn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummary;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static com.luketn.dataaccess.mongodb.MongoDBProvider.createClientSettings;
import static com.luketn.dataaccess.mongodb.WeatherDataAccess.COLLECTION_NAME;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@ExtendWith(SpringExtension.class)
@Testcontainers
public class WeatherApiTest {
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongodb/mongodb-community-server:8.0.12-ubi9");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("mongodb-schema.dataaccess.mongodb.connection-string", mongoDBContainer::getConnectionString);
        registry.add("mongodb-schema.dataaccess.mongodb.database-name", ()->"testdb");
    }

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @LocalServerPort
    protected int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private MongoDatabase testDatabase;

    @BeforeEach
    void setup() {
        MongoClient mongoClient = MongoClients.create(createClientSettings(mongoDBContainer.getConnectionString()));
        testDatabase = mongoClient.getDatabase("testdb");
        testDatabase.getCollection(COLLECTION_NAME).drop();
        testDatabase.getCollection(COLLECTION_NAME).createIndex(new Document("position", "2dsphere"));
    }

    @Test
    void getReport() throws IOException, InterruptedException {
        // given
        WeatherReport testReport = createTestReport("688b5a0628ebb91a42ce2979", "2025-07-31T11:56:54.859Z", 151.2093, -33.8688, 25.0, 15.0);
        testDatabase.getCollection(COLLECTION_NAME, WeatherReport.class).insertOne(testReport);

        // when
        var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/weather/report?id=688b5a0628ebb91a42ce2979"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertEquals(200, response.statusCode(), "Expected HTTP status code 200");
        String body = response.body();
        WeatherReport fetchedReport = objectMapper.readValue(body, WeatherReport.class);
        assertEquals(testReport, fetchedReport);
    }

    @Test
    void listReports() throws IOException, InterruptedException {
        // given
        WeatherReport testReport1 = createTestReport("688b5a0628ebb91a42ce2977", "2025-07-31T11:56:54.857Z", 151.2091, -33.8688, 23.0, 10.0);
        WeatherReport testReport2 = createTestReport("688b5a0628ebb91a42ce2978", "2025-07-31T11:56:54.858Z", 151.2092, -33.8688, 24.0, 11.0);
        WeatherReport testReport3 = createTestReport("688b5a0628ebb91a42ce2979", "2025-07-31T11:56:54.859Z", 151.2093, -33.8688, 25.0, 12.0);
        testDatabase.getCollection(COLLECTION_NAME, WeatherReport.class).insertMany(List.of(
                testReport1,
                testReport2,
                testReport3
        ));

        // when
        var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/weather/report/list"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertEquals(200, response.statusCode(), "Expected HTTP status code 200");
        String body = response.body();
        WeatherReportSummaryList fetchedReportList = objectMapper.readValue(body, WeatherReportSummaryList.class);
        assertEquals(new WeatherReportSummaryList(
                List.of(
                        new WeatherReportSummary(testReport1.id(), testReport1.ts(), testReport1.seaSurfaceTemperature().value(), testReport1.airTemperature().value()),
                        new WeatherReportSummary(testReport2.id(), testReport2.ts(), testReport2.seaSurfaceTemperature().value(), testReport2.airTemperature().value()),
                        new WeatherReportSummary(testReport3.id(), testReport3.ts(), testReport3.seaSurfaceTemperature().value(), testReport3.airTemperature().value())
                ),
                0, 1
        ), fetchedReportList);
    }

    private static @NotNull WeatherReport createTestReport(String reportId, String isoDate, double longitude, double latitude, double airTemperatureDegreesCelcius, double seaTemperatureDegreesCelcius) {
        return new WeatherReport(reportId, "Test Report",
                Instant.parse(isoDate),
                new WeatherReport.Position("Point", List.of(longitude, latitude)),
                null, null, null, null, null,
                new WeatherReport.Measurement(airTemperatureDegreesCelcius, "9"),
                null, null, null, null, null, null, null, null,
                new WeatherReport.Measurement(seaTemperatureDegreesCelcius, "9")
        );
    }
}