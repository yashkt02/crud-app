package com.example.summary;

import java.time.LocalDate;

public record ExpenseEvent(String eventType, String ownerId, Long id, String description, String category, double amount, LocalDate date) {
	public static ExpenseEvent deleted(String ownerId, long id) {
		return new ExpenseEvent("DELETE", ownerId, id, null, null, 0, null);
	}
}