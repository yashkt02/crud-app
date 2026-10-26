package com.example.summary;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SummaryService {
    private final ConcurrentHashMap<String, ExpenseEvent> expenses = new ConcurrentHashMap<>();

    @KafkaListener(topics = "expense-events")
    public void onExpenseEvent(ExpenseEvent event) {
        if (event == null || event.id() == null || event.ownerId() == null || event.ownerId().isBlank()) {
            return;
        }
        String key = key(event.ownerId(), event.id());
        if ("DELETE".equals(event.eventType())) {
            expenses.remove(key);
        } else if ("UPSERT".equals(event.eventType()) && event.category() != null && event.date() != null) {
            expenses.put(key, event);
        }
    }

    public java.util.List<SummaryDtos.CategorySummary> categories(String ownerId, YearMonth month) {
        return expenses.values().stream()
                .filter(expense -> expense.ownerId().equals(ownerId))
                .filter(expense -> month == null || YearMonth.from(expense.date()).equals(month))
                .collect(Collectors.groupingBy(ExpenseEvent::category))
                .entrySet().stream()
                .map(entry -> new SummaryDtos.CategorySummary(
                        entry.getKey(), sum(entry.getValue().stream().map(ExpenseEvent::amount).toList()), entry.getValue().size()))
                .sorted(Comparator.comparing(SummaryDtos.CategorySummary::category, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public SummaryDtos.MonthlySummary monthly(String ownerId, YearMonth month) {
        Map<String, BigDecimal> categories = categories(ownerId, month).stream()
                .collect(Collectors.toMap(SummaryDtos.CategorySummary::category, SummaryDtos.CategorySummary::total));
        long count = expenses.values().stream()
                .filter(expense -> expense.ownerId().equals(ownerId))
                .filter(expense -> YearMonth.from(expense.date()).equals(month))
                .count();
        return new SummaryDtos.MonthlySummary(month, sum(categories.values()), count, categories);
    }

    private String key(String ownerId, long id) {
        return ownerId + ":" + id;
    }

    private BigDecimal sum(Iterable<Double> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (double amount : amounts) {
            total = total.add(BigDecimal.valueOf(amount));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(java.util.Collection<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}