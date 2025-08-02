package com.luketn.seatemperature.datamodel;

public record BoundingBox(
        // Latitude values for the bounds of the query
        Double south, Double north,
        // Longitude values for the bounds of the query
        Double west, Double east
) { }
