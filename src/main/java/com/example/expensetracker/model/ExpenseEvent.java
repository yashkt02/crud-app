package com.example.expensetracker.model;

import java.time.LocalDate;

public record ExpenseEvent(String eventType, String ownerId, Long id, String description, String category, double amount, LocalDate date) {
    public static ExpenseEvent upsert(Expense expense) {
        return new ExpenseEvent("UPSERT", expense.ownerId(), expense.id(), expense.description(), expense.category(), expense.amount(), expense.date());
    }

    public static ExpenseEvent deleted(String ownerId, long id) {
        return new ExpenseEvent("DELETE", ownerId, id, null, null, 0, null);
    }
}