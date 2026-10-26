package com.example.expensetracker.model;

import java.time.LocalDate;

public record Expense(Long id, String ownerId, String description, String category, double amount, LocalDate date) {
}