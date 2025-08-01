package com.luketn.dataaccess.mongodb;

import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummary;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.luketn.seatemperature.datamodel.BoundingBox;
import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Facet;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;

@Component
public class WeatherDataAccess {
    private static final Logger log = LoggerFactory.getLogger(WeatherDataAccess.class);

    public static final String COLLECTION_NAME = "data";

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

        Integer totalReportsMatched = Objects.requireNonNull(result).summary().getFirst().count();
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

    public void streamSeaTemperatures(BoundingBox boundingBox, Consumer<WeatherReport> weatherReportConsumer) {
        MongoDatabase database = mongoDBProvider.getMongoDatabase();
        MongoCollection<WeatherReport> collection = database.getCollection(COLLECTION_NAME, WeatherReport.class);

        Bson filter = and(
                gte("position.coordinates.0", boundingBox.west()),
                lte("position.coordinates.0", boundingBox.east()),
                gte("position.coordinates.1", boundingBox.south()),
                lte("position.coordinates.1", boundingBox.north())
        );

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

        collection
                .find(filter)
                .projection(projection)
                .batchSize(pageSize)
                .forEach(weatherReportConsumer);
    }
}
