package com.example.summary;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/summaries")
public class SummaryController {
    private final SummaryService summaries;

    public SummaryController(SummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/categories")
    public List<SummaryDtos.CategorySummary> categories(
            @RequestParam(required = false) @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @AuthenticationPrincipal Jwt principal) {
        return summaries.categories(principal.getSubject(), month);
    }

    @GetMapping("/monthly")
    public SummaryDtos.MonthlySummary monthly(
            @RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
            @AuthenticationPrincipal Jwt principal) {
        return summaries.monthly(principal.getSubject(), month);
    }
}