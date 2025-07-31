package com.luketn.seatemperature.datamodel;

/**
 * Represents sea temperature data at a specific latitude and longitude.
 */
public record SeaTemperature(
        double lon,
        double lat,
        double temp
) {}
