package com.luketn.seatemperature;

import com.luketn.dataaccess.mongodb.WeatherDataAccess;
import com.luketn.seatemperature.datamodel.SeaTemperature;
import com.luketn.seatemperature.datamodel.SeaTemperatureFilter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Handles validation and transformation of weather reports into sea temperature data.
 */
@Service
public class SeaTemperatureService {
    private final WeatherDataAccess weatherDataAccess;

    public SeaTemperatureService(WeatherDataAccess weatherDataAccess) {
        this.weatherDataAccess = weatherDataAccess;
    }

    public void streamSeaTemperatures(SeaTemperatureFilter seaTemperatureFilter, Consumer<List<SeaTemperature>> seaTemperatureConsumer) {
        weatherDataAccess.streamSeaTemperatures(seaTemperatureFilter,
                batch -> {
                    List<SeaTemperature> seaTemperatures = batch.stream()
                            .filter(weatherReport -> weatherReport.seaSurfaceTemperature() != null)
                            .map(weatherReport -> {
                                Double longitude = weatherReport.position().coordinates().get(0);
                                Double latitude = weatherReport.position().coordinates().get(1);
                                Double seaSurfaceTemperature = weatherReport.seaSurfaceTemperature().value();
                                return new SeaTemperature(longitude, latitude, seaSurfaceTemperature);
                            })
                            .toList();
                    seaTemperatureConsumer.accept(seaTemperatures);
                });
    }
}
