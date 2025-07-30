package com.luketn.api;

import com.luketn.dataaccess.mongodb.WeatherDataAccess;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.luketn.util.SynchronousSse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;


@RestController
@RequestMapping("/weather")
public class WeatherApi {
    private final WeatherDataAccess weatherDataAccess;

    public WeatherApi(WeatherDataAccess weatherDataAccess) {
        this.weatherDataAccess = weatherDataAccess;
    }

    @GetMapping("/sea/temperature")
    public void streamSeaSurfaceTemperatures(
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "metersRadius", required = false) Double metersRadius,
            HttpServletResponse response) {
        Instant from = fromDate != null ? Instant.parse(fromDate) : null;
        Instant to = toDate != null ? Instant.parse(toDate) : null;
        if ((longitude != null || latitude != null || metersRadius != null) &&
                (longitude == null || latitude == null || metersRadius == null)) {
            throw new IllegalArgumentException("If longitude, latitude or metersRadius are supplied, all three must be supplied.");
        }
        var sse = SynchronousSse.forResponse(response);
        weatherDataAccess.streamSeaTemperatures(from, to, longitude, latitude, metersRadius, sse::sendEvent);
    }

    @GetMapping
    public WeatherReport getReport(@RequestParam(value = "id") String id) {
        return weatherDataAccess.getReport(id);
    }

    @GetMapping( "/list")
    public WeatherReportSummaryList listProducts(
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        Instant from = fromDate != null ? Instant.parse(fromDate) : null;
        Instant to = toDate != null ? Instant.parse(toDate) : null;

        return weatherDataAccess.listReports(from, to, page);
    }
}
