package com.example.expensetracker.model;

import java.time.LocalDate;

public record ExpenseRequest(String description, String category, double amount, LocalDate date) {
}