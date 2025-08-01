package com.luketn.seatemperature;

import com.luketn.dataaccess.mongodb.WeatherDataAccess;
import com.luketn.seatemperature.datamodel.BoundingBox;
import com.luketn.seatemperature.datamodel.SeaTemperature;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles validation and transformation of weather reports into sea temperature data.
 * Batches and returns only unique coordinate sea surface temperature reports within a specified bounding box.
 */
@Service
public class SeaTemperatureService {
    public static final int batch_size = 10;

    private final WeatherDataAccess weatherDataAccess;

    public SeaTemperatureService(WeatherDataAccess weatherDataAccess) {
        this.weatherDataAccess = weatherDataAccess;
    }

    public void streamSeaTemperatures(BoundingBox boundingBox, Consumer<List<SeaTemperature>> seaTemperatureConsumer) {
        List<SeaTemperature> seaTemperaturesBatch = new ArrayList<>();

        record Coordinates(Double longitude, Double latitude) {}
        Set<Coordinates> uniqueCoordinates = new HashSet<>();

        weatherDataAccess.streamSeaTemperatures(boundingBox, weatherReport -> {
            if (weatherReport.seaSurfaceTemperature() == null) {
                return; // Skip reports without sea surface temperature
            }
            Double longitude = weatherReport.position().coordinates().get(0);
            Double latitude = weatherReport.position().coordinates().get(1);
            Double seaSurfaceTemperature = weatherReport.seaSurfaceTemperature().value();

            Coordinates coordinates = new Coordinates(longitude, latitude);
            if (uniqueCoordinates.contains(coordinates)) {
                return; // Skip duplicate coordinates
            }
            uniqueCoordinates.add(coordinates);

            SeaTemperature seaTemperature = new SeaTemperature(longitude, latitude, seaSurfaceTemperature);

            seaTemperaturesBatch.add(seaTemperature);
            if (seaTemperaturesBatch.size() >= batch_size) {
                seaTemperatureConsumer.accept(new ArrayList<>(seaTemperaturesBatch));
                seaTemperaturesBatch.clear(); // Clear the batch after sending
            }
        });
        if (!seaTemperaturesBatch.isEmpty()) {
            seaTemperatureConsumer.accept(seaTemperaturesBatch);
        }
    }
}
