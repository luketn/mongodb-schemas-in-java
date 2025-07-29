package com.luketn.datamodel.mongodb;

import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.time.Instant;

public record WeatherReportSummary(
        @BsonId @BsonRepresentation(BsonType.OBJECT_ID) String id,
        Instant ts,
        Double seaSurfaceTemperature,
        Double airTemperature
) {}
