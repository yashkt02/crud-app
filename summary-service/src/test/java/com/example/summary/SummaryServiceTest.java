package com.example.summary;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryServiceTest {
    private final SummaryService service = new SummaryService();

    @Test
    void aggregatesByCategoryAndMonthAndAppliesUpdatesAndDeletes() {
        service.onExpenseEvent(new ExpenseEvent("UPSERT", "alice", 1L, "Lunch", "Food", 12.50, LocalDate.of(2025, 3, 1)));
        service.onExpenseEvent(new ExpenseEvent("UPSERT", "alice", 2L, "Train", "Travel", 7.25, LocalDate.of(2025, 3, 2)));
        service.onExpenseEvent(new ExpenseEvent("UPSERT", "alice", 3L, "Dinner", "Food", 20.00, LocalDate.of(2025, 4, 1)));

        assertEquals(2, service.categories("alice", YearMonth.of(2025, 3)).size());
        assertEquals("19.75", service.monthly("alice", YearMonth.of(2025, 3)).total().toPlainString());
        assertEquals(2, service.monthly("alice", YearMonth.of(2025, 3)).count());

        service.onExpenseEvent(new ExpenseEvent("UPSERT", "alice", 1L, "Lunch", "Travel", 10.00, LocalDate.of(2025, 3, 1)));
        assertEquals("17.25", service.monthly("alice", YearMonth.of(2025, 3)).total().toPlainString());

        service.onExpenseEvent(ExpenseEvent.deleted("alice", 2L));
        assertEquals("10.00", service.monthly("alice", YearMonth.of(2025, 3)).total().toPlainString());
        assertEquals(1, service.monthly("alice", YearMonth.of(2025, 3)).count());
        }

        @Test
        void sameExpenseIdsAreAggregatedAndDeletedPerOwner() {
        service.onExpenseEvent(new ExpenseEvent("UPSERT", "alice", 1L, "Food", "Food", 500, LocalDate.of(2025, 3, 1)));
        service.onExpenseEvent(new ExpenseEvent("UPSERT", "bob", 1L, "Shopping", "Shopping", 1000, LocalDate.of(2025, 3, 2)));
        service.onExpenseEvent(new ExpenseEvent("UPSERT", "bob", 2L, "Food", "Food", 300, LocalDate.of(2025, 4, 2)));

        assertEquals("500.00", service.monthly("alice", YearMonth.of(2025, 3)).total().toPlainString());
        assertEquals("1000.00", service.monthly("bob", YearMonth.of(2025, 3)).total().toPlainString());
        assertEquals("1300.00", service.monthly("bob", YearMonth.of(2025, 4)).total()
            .add(service.monthly("bob", YearMonth.of(2025, 3)).total()).toPlainString());
        assertEquals("1000.00", service.categories("bob", YearMonth.of(2025, 3)).get(0).total().toPlainString());

        service.onExpenseEvent(ExpenseEvent.deleted("alice", 1L));
        assertEquals("0.00", service.monthly("alice", YearMonth.of(2025, 3)).total().toPlainString());
        assertEquals("1000.00", service.monthly("bob", YearMonth.of(2025, 3)).total().toPlainString());
    }
}