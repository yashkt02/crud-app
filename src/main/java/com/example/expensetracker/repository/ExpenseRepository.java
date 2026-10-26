package com.example.expensetracker.repository;

import com.example.expensetracker.model.Expense;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class ExpenseRepository {
    private final Map<Long, Expense> expenses = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public List<Expense> findAll(String ownerId) {
        return expenses.values().stream()
                .filter(expense -> expense.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(Expense::date).reversed()
                    .thenComparing(Expense::id, Comparator.reverseOrder()))
                .toList();
    }

    public Optional<Expense> findById(String ownerId, long id) {
        Expense expense = expenses.get(id);
        return expense != null && expense.ownerId().equals(ownerId) ? Optional.of(expense) : Optional.empty();
    }

    public Expense create(String ownerId, Expense expense) {
        long id = expense.id() == null ? nextId.getAndIncrement() : expense.id();
        Expense saved = new Expense(id, ownerId, expense.description(), expense.category(), expense.amount(), expense.date());
        expenses.put(id, saved);
        return saved;
    }

    public synchronized Optional<Expense> update(String ownerId, long id, Expense expense) {
        Expense existing = expenses.get(id);
        if (existing == null || !existing.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        Expense updated = new Expense(id, ownerId, expense.description(), expense.category(), expense.amount(), expense.date());
        expenses.put(id, updated);
        return Optional.of(updated);
    }

    public synchronized boolean deleteById(String ownerId, long id) {
        Expense existing = expenses.get(id);
        return existing != null && existing.ownerId().equals(ownerId) && expenses.remove(id, existing);
    }
}