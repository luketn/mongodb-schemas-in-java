package com.luketn.seatemperature.datamodel;

public record DistanceFromCentre(
        Double longitude,
        Double latitude,
        Double metersRadius
) implements SeaTemperatureFilter {
}
