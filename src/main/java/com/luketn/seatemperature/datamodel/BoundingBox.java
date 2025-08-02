package com.luketn.seatemperature.datamodel;

public record BoundingBox(
        // Latitude values for the bounds of the query
        Double north,
        Double south,
        // Longitude values for the bounds of the query
        Double east,
        Double west
) { }
