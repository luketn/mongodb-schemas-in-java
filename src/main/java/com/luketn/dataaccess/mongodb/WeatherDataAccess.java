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
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Updates.combine;

@Component
public class WeatherDataAccess {
    private static final int pageSize = 10;

    private final MongoDBProvider mongoDBProvider;

    public WeatherDataAccess(MongoDBProvider mongoDBProvider) {
        this.mongoDBProvider = mongoDBProvider;
    }

    public void streamSeaTemperatures(Consumer<List<SeaTemperature>> batchConsumer) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection("data", WeatherReport.class);
        try (MongoCursor<WeatherReport> temperaturesRawCursor = collection
                .find(
                    combine(
                        exists("seaSurfaceTemperature.value"),
                        exists("position.coordinates")
                    )
                )
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

    public WeatherReport getReport(String id) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection("data", WeatherReport.class);
        return collection.find(new Document("_id", new ObjectId(id))).first();
    }

    /**
     * Lists weather reports with pagination.
     *
     * @param page the page number to retrieve, starting from 0
     */
    public WeatherReportSummaryList listReports(int page) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReportSummaryAggregate> collection = database.getCollection("data", WeatherReportSummaryAggregate.class);

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
}
