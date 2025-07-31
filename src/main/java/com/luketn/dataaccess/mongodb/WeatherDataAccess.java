package com.luketn.dataaccess.mongodb;

import com.luketn.seatemperature.datamodel.SeaTemperature;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummary;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.luketn.seatemperature.datamodel.BoundingBox;
import com.luketn.seatemperature.datamodel.DistanceFromCentre;
import com.luketn.seatemperature.datamodel.SeaTemperatureFilter;
import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.geojson.Polygon;
import com.mongodb.client.model.geojson.Position;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;

@Component
public class WeatherDataAccess {
    public static final int RADIUS_OF_EARTH_METERS = 637_810;
    public static final String COLLECTION_NAME = "data";
    private static Logger log = LoggerFactory.getLogger(WeatherDataAccess.class);

    private static final int pageSize = 10;

    private final MongoDBProvider mongoDBProvider;

    public WeatherDataAccess(MongoDBProvider mongoDBProvider) {
        this.mongoDBProvider = mongoDBProvider;
    }

    public WeatherReport getReport(String id) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection(COLLECTION_NAME, WeatherReport.class);
        return collection.find(new Document("_id", new ObjectId(id))).first();
    }

    public WeatherReportSummaryList listReports(int page) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReportSummaryAggregate> collection = database.getCollection(COLLECTION_NAME, WeatherReportSummaryAggregate.class);

        WeatherReportSummaryAggregate result = collection.aggregate(List.of(
                        facet(
                                new Facet("reports", List.of(
                                        skip(page * pageSize),
                                        limit(pageSize),
                                        project(new Document()
                                                .append("_id", 1)
                                                .append("ts", "$ts")
                                                .append("seaSurfaceTemperature", "$seaSurfaceTemperature.value")
                                                .append("airTemperature", "$airTemperature.value")
                                        )
                                ))
                                ,new Facet("summary", List.of(
                                        count()
                                ))
                        )
                )
        ).first();

        Integer totalReportsMatched = result.summary().getFirst().count();
        if (totalReportsMatched == null) {
            totalReportsMatched = 0;
        }
        int totalPages = (int) Math.ceil((double) totalReportsMatched / pageSize);
        return new WeatherReportSummaryList(
                result.reports(),
                page,
                totalPages
        );
    }

    public record WeatherReportSummaryAggregate(
        List<WeatherReportSummary> reports,
        List<Summary> summary
    ) {
        public record Summary(Integer count) {}
    }

    public void streamSeaTemperatures(SeaTemperatureFilter seaTemperatureFilter, Consumer<List<WeatherReport>> batchConsumer) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection(COLLECTION_NAME, WeatherReport.class);

        Bson filter = seaTemperatureFilter == null ? new Document() : switch (seaTemperatureFilter) {
            case BoundingBox boundingBox -> {
                // Normalize coordinates to handle Leaflet's infinite panning
                double normalizedEast = normalizeLongitude(boundingBox.east());
                double normalizedWest = normalizeLongitude(boundingBox.west());
                double clampedNorth = Math.max(-90, Math.min(90, boundingBox.north()));
                double clampedSouth = Math.max(-90, Math.min(90, boundingBox.south()));

                // Check if bounding box crosses the International Date Line
                if (normalizedWest > normalizedEast) {
                    // Crosses date line - split into two queries
                    if (log.isDebugEnabled()) {
                        log.debug("Bounding box crosses International Date Line: west={}, east={}", normalizedWest, normalizedEast);
                    }

                    Bson westSide = geoWithin("position", new Polygon(List.of(
                            new Position(normalizedWest, clampedSouth),
                            new Position(180.0, clampedSouth),
                            new Position(180.0, clampedNorth),
                            new Position(normalizedWest, clampedNorth),
                            new Position(normalizedWest, clampedSouth)
                    )));

                    Bson eastSide = geoWithin("position", new Polygon(List.of(
                            new Position(-180.0, clampedSouth),
                            new Position(normalizedEast, clampedSouth),
                            new Position(normalizedEast, clampedNorth),
                            new Position(-180.0, clampedNorth),
                            new Position(-180.0, clampedSouth)
                    )));

                    yield or(westSide, eastSide);
                } else {
                    // Normal bounding box - doesn't cross date line
                    yield geoWithin("position", new Polygon(List.of(
                            new Position(normalizedWest, clampedSouth),
                            new Position(normalizedEast, clampedSouth),
                            new Position(normalizedEast, clampedNorth),
                            new Position(normalizedWest, clampedNorth),
                            new Position(normalizedWest, clampedSouth)
                    )));
                }
            }
            case DistanceFromCentre distanceFromCentre -> {
                // Normalize coordinates for center point as well
                double normalizedLongitude = normalizeLongitude(distanceFromCentre.longitude());
                double clampedLatitude = Math.max(-90, Math.min(90, distanceFromCentre.latitude()));

                yield geoWithinCenterSphere("position",
                        normalizedLongitude, clampedLatitude,
                        distanceFromCentre.metersRadius() / RADIUS_OF_EARTH_METERS);
            }
            default -> throw new IllegalArgumentException("Unsupported SeaTemperatureFilter type: " + seaTemperatureFilter.getClass().getName());
        };

        Document projection = new Document()
                .append("_id", 0)
                .append("position.coordinates", 1)
                .append("seaSurfaceTemperature.value", 1);


        if (log.isTraceEnabled()) {
            Document explain = collection
                    .find(filter)
                    .projection(projection)
                    .explain(ExplainVerbosity.EXECUTION_STATS);
            log.trace("MongoDB explain plan for sea surface temperature query:\n{}", explain.toJson(JsonWriterSettings.builder().indent(true).build()));
        }

        try (MongoCursor<WeatherReport> temperaturesRawCursor = collection
                .find(filter)
                .projection(projection)
                .batchSize(10)
                .cursor()) {

            List<WeatherReport> batch = new ArrayList<>();
            while (temperaturesRawCursor.hasNext()) {
                WeatherReport weatherReport = temperaturesRawCursor.next();
                batch.add(weatherReport);

                if (batch.size() >= pageSize) {
                    batchConsumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                batchConsumer.accept(new ArrayList<>(batch));
            }
        }
    }
    /**
     * Normalizes longitude to the valid range [-180, 180]
     * Handles Leaflet's infinite horizontal panning
     */
    private double normalizeLongitude(double longitude) {
        // Handle multiple wraps around the world
        longitude = longitude % 360;
        if (longitude > 180) {
            longitude -= 360;
        } else if (longitude < -180) {
            longitude += 360;
        }
        return longitude;
    }
}
