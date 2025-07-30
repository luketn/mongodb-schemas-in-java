package com.luketn.dataaccess.mongodb;

import com.luketn.datamodel.mongodb.SeaTemperature;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummary;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Facet;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;

@Component
public class WeatherDataAccess {
    private static final int pageSize = 10;

    private final MongoDBProvider mongoDBProvider;

    public WeatherDataAccess(MongoDBProvider mongoDBProvider) {
        this.mongoDBProvider = mongoDBProvider;
    }

    public WeatherReport getReport(String id) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection("data", WeatherReport.class);
        return collection.find(new Document("_id", new ObjectId(id))).first();
    }

    public WeatherReportSummaryList listReports(Instant from, Instant to, int page) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReportSummaryAggregate> collection = database.getCollection("data", WeatherReportSummaryAggregate.class);

        Bson filter;
        if (from != null && to != null) {
            filter = and(
                    gte("ts", from),
                    lte("ts", to)
            );
        } else if (from != null) {
            filter = gte("ts", from);
        } else if (to != null) {
            filter = lte("ts", to);
        } else {
            filter = new Document(); // No filter, match all
        }
        WeatherReportSummaryAggregate result = collection.aggregate(List.of(
                        match(filter),
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

    public void streamSeaTemperatures(Instant from, Instant to, Double longitude, Double latitude, Double metersRadius, Consumer<List<SeaTemperature>> batchConsumer) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection("data", WeatherReport.class);

        List<Bson> filters = new ArrayList<>();
        filters.add(exists("position.coordinates"));
        filters.add(exists("seaSurfaceTemperature.value"));
        if (from != null) {
            filters.add(gte("ts", from));
        }
        if (to != null) {
            filters.add(lte("ts", to));
        }
        if (longitude != null && latitude != null && metersRadius != null) {
            filters.add(geoWithinCenterSphere("position.coordinates", longitude, latitude, metersRadius / 6371000.0)); // Radius in radians
        }
        Bson filter = and(filters);

        try (MongoCursor<WeatherReport> temperaturesRawCursor = collection
                .find(filter)
                .projection(new Document()
                        .append("_id", 0)
                        .append("position.coordinates", 1)
                        .append("seaSurfaceTemperature.value", 1)
                )
                .batchSize(10)
                .cursor()) {

            List<SeaTemperature> batch = new ArrayList<>();
            while (temperaturesRawCursor.hasNext()) {
                WeatherReport weatherReport = temperaturesRawCursor.next();
                SeaTemperature seaTemperature = new SeaTemperature(
                        weatherReport.position().coordinates().get(0),
                        weatherReport.position().coordinates().get(1),
                        weatherReport.seaSurfaceTemperature().value()
                );
                batch.add(seaTemperature);

                if (batch.size() >= pageSize) {
                    batchConsumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
            }
        }
    }
}
