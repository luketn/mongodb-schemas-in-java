package com.luketn.datamodel.mongodb;

import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.time.Instant;
import java.util.List;

/**
 * Represents a weather report with various meteorological data.
 * Ref: https://www.mongodb.com/docs/atlas/sample-data/sample-weather/
 */
public record WeatherReport(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID) String id,
    String st,
    Instant ts,
    Position position,
    Integer elevation,
    String callLetters,
    String qualityControlProcess,
    String dataSource,
    String type,
    Measurement airTemperature,
    Measurement dewPoint,
    Measurement pressure,
    Wind wind,
    Visibility visibility,
    SkyCondition skyCondition,
    List<String> sections,
    PrecipitationEstimatedObservation precipitationEstimatedObservation,
    AtmosphericPressureChange atmosphericPressureChange,
    Measurement seaSurfaceTemperature
) {
    public record Position(String type, List<Double> coordinates) {}

    public record Measurement(Double value, String quality) {}

    public record Wind(Direction direction, String type, Speed speed) {
        public record Direction(Integer angle, String quality) {}
        public record Speed(Double rate, String quality) {}
    }

    public record Visibility(Distance distance, Variability variability) {
        public record Distance(Integer value, String quality) {}
        public record Variability(String value, String quality) {}
    }

    public record SkyCondition(CeilingHeight ceilingHeight, String cavok) {
        public record CeilingHeight(Integer value, String quality, String determination) {}
    }

    public record PrecipitationEstimatedObservation(String discrepancy, Integer estimatedWaterDepth) {}

    public record AtmosphericPressureChange(Tendency tendency, Quantity quantity3Hours, Quantity quantity24Hours) {
        public record Tendency(String code, String quality) {}
        public record Quantity(Double value, String quality) {}
    }
}
