package com.example.summary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

public final class SummaryDtos {
    private SummaryDtos() {
    }

    public record CategorySummary(String category, BigDecimal total, long count) {
    }

    public record MonthlySummary(YearMonth month, BigDecimal total, long count, Map<String, BigDecimal> categories) {
    }
}