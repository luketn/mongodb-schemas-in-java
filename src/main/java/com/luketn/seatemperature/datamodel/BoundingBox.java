package com.luketn.seatemperature.datamodel;

public record BoundingBox(
        Double north,
        Double south,
        Double east,
        Double west
) { }
