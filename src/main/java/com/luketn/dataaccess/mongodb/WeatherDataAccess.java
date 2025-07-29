package com.luketn.dataaccess.mongodb;

import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummary;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Facet;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.mongodb.client.model.Aggregates.*;

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
