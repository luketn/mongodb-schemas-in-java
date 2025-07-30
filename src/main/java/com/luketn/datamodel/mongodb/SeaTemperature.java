package com.luketn.datamodel.mongodb;

/**
 * Represents sea temperature data at a specific latitude and longitude.
 */
public record SeaTemperature(
        double lon,
        double lat,
        double temp
) {}
