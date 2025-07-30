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


@RestController
@RequestMapping("/weather")
public class WeatherApi {
    private final WeatherDataAccess weatherDataAccess;

    public WeatherApi(WeatherDataAccess weatherDataAccess) {
        this.weatherDataAccess = weatherDataAccess;
    }

    @GetMapping("/sea/temperature")
    public void streamSeaSurfaceTemperatures(HttpServletResponse response) {
        SynchronousSse sse = new SynchronousSse(response);
        weatherDataAccess.streamSeaTemperatures(sse::sendEvent);
    }

    @GetMapping
    public WeatherReport getReport(@RequestParam(value = "id") String id) {
        return weatherDataAccess.getReport(id);
    }

    @GetMapping( "/list")
    public WeatherReportSummaryList listProducts(@RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        return weatherDataAccess.listReports(page);
    }
}
