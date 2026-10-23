package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseRequest;
import com.example.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ExpenseService {
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public List<Expense> findAll() {
        return repository.findAll();
    }

    public Optional<Expense> findById(long id) {
        return repository.findById(id);
    }

    public Expense create(ExpenseRequest request) {
        validate(request);
        return repository.save(toExpense(null, request));
    }

    public Optional<Expense> update(long id, ExpenseRequest request) {
        validate(request);
        if (repository.findById(id).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(repository.save(toExpense(id, request)));
    }

    public boolean delete(long id) {
        return repository.deleteById(id);
    }

    private Expense toExpense(Long id, ExpenseRequest request) {
        return new Expense(id, request.description().trim(), request.category().trim(), request.amount(), request.date());
    }

    private void validate(ExpenseRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()
                || request.category() == null || request.category().isBlank()
                || !Double.isFinite(request.amount()) || request.amount() <= 0
                || request.date() == null || request.date().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Enter a description, category, positive amount, and a valid date not in the future.");
        }
    }
}