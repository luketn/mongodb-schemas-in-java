package com.luketn.api;

import com.luketn.dataaccess.mongodb.WeatherDataAccess;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
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

    @GetMapping
    public WeatherReport getReport(@RequestParam(value = "id") String id) {
        return weatherDataAccess.getReport(id);
    }

    @GetMapping( "/list")
    public WeatherReportSummaryList listProducts(@RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        return weatherDataAccess.listReports(page);
    }
}
