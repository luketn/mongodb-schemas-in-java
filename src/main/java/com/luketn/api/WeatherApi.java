package com.luketn.api;

import com.luketn.dataaccess.mongodb.WeatherDataAccess;
import com.luketn.seatemperature.SeaTemperatureService;
import com.luketn.datamodel.mongodb.WeatherReport;
import com.luketn.datamodel.mongodb.WeatherReportSummaryList;
import com.luketn.seatemperature.datamodel.BoundingBox;
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
            @RequestParam(value = "north", required = false) Double north,
            @RequestParam(value = "south", required = false) Double south,
            @RequestParam(value = "east", required = false) Double east,
            @RequestParam(value = "west", required = false) Double west,
            HttpServletResponse response) {
        var sse = SynchronousSse.forResponse(response);

        if (north == null || south == null || east == null || west == null) {
            sse.error("For BoundingBox query type, north, south, east, and west must all be supplied.");
        }

        try {
            BoundingBox boundingBox = new BoundingBox(north, south, east, west);
            seaTemperatureService.streamSeaTemperatures(boundingBox, sse::sendEvent);
        } catch (SynchronousSse.SseBrokenPipe _) { // ignore broken pipes in SSE
        } catch (Exception e) {
            sse.error(e, "An error occurred while streaming sea surface temperatures.");
        }
    }

    @GetMapping("/report")
    public WeatherReport getReport(@RequestParam(value = "id") String id) {
        WeatherReport report = weatherDataAccess.getReport(id);
        return report;
    }

    @GetMapping("/report/list")
    public WeatherReportSummaryList listReports(@RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        WeatherReportSummaryList weatherReportList = weatherDataAccess.listReports(page);
        return weatherReportList;
    }
}
