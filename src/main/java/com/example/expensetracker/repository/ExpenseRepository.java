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

    public List<Expense> findAll() {
        return expenses.values().stream()
                .sorted(Comparator.comparing(Expense::date).reversed()
                    .thenComparing(Expense::id, Comparator.reverseOrder()))
                .toList();
    }

    public Optional<Expense> findById(long id) {
        return Optional.ofNullable(expenses.get(id));
    }

    public Expense save(Expense expense) {
        long id = expense.id() == null ? nextId.getAndIncrement() : expense.id();
        Expense saved = new Expense(id, expense.description(), expense.category(), expense.amount(), expense.date());
        expenses.put(id, saved);
        return saved;
    }

    public boolean deleteById(long id) {
        return expenses.remove(id) != null;
    }
}