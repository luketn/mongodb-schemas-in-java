package com.luketn.api;

import com.luketn.dataaccess.mongodb.WeatherDataAccess;
import com.luketn.seatemperature.SeaTemperatureService;
import com.luketn.seatemperature.datamodel.SeaTemperatureQueryType;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.luketn.seatemperature.datamodel.BoundingBox;
import com.luketn.seatemperature.datamodel.DistanceFromCentre;
import com.luketn.seatemperature.datamodel.SeaTemperatureFilter;
import com.luketn.util.SynchronousSse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/weather")
public class WeatherApi {
    private final WeatherDataAccess weatherDataAccess;
    private final SeaTemperatureService seaTemperatureService;

    public WeatherApi(WeatherDataAccess weatherDataAccess, SeaTemperatureService seaTemperatureService) {
        this.weatherDataAccess = weatherDataAccess;
        this.seaTemperatureService = seaTemperatureService;
    }

    @GetMapping("/sea/temperature")
    public void streamSeaSurfaceTemperatures(
            @RequestParam(value = "queryType", required = false) SeaTemperatureQueryType queryType,

            // for DistanceFromCentre, these are required
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "metersRadius", required = false) Double metersRadius,

            // for BoundingBox, these are required
            @RequestParam(value = "north", required = false) Double north,
            @RequestParam(value = "south", required = false) Double south,
            @RequestParam(value = "east", required = false) Double east,
            @RequestParam(value = "west", required = false) Double west,

            HttpServletResponse response) {

        SeaTemperatureFilter seaTemperatureFilter = queryType == null ? null : switch (queryType) {
            case BoundingBox -> {
                if (north == null || south == null || east == null || west == null) {
                    throw new IllegalArgumentException("For BoundingBox query type, north, south, east, and west must all be supplied.");
                } else {
                    yield new BoundingBox(north, south, east, west);
                }
            }
            case DistanceFromCentre -> {
                if (longitude == null || latitude == null || metersRadius == null) {
                    throw new IllegalArgumentException("For DistanceFromCentre query type, longitude, latitude, and metersRadius must all be supplied.");
                } else {
                    yield new DistanceFromCentre(longitude, latitude, metersRadius);
                }
            }
        };
        var sse = SynchronousSse.forResponse(response);
        seaTemperatureService.streamSeaTemperatures(seaTemperatureFilter, sse::sendEvent);
    }

    @GetMapping("/report")
    public WeatherReport getReport(@RequestParam(value = "id") String id) {
        WeatherReport report = weatherDataAccess.getReport(id);
        return report;
    }

    @GetMapping( "/report/list")
    public WeatherReportSummaryList listReports(@RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        WeatherReportSummaryList weatherReportList = weatherDataAccess.listReports(page);
        return weatherReportList;
    }
}
