package com.luketn.datamodel.mongodb;

import java.util.List;

public record WeatherReportSummaryList(
        List<WeatherReportSummary> reports,
        int page,
        int totalPages
) { }
