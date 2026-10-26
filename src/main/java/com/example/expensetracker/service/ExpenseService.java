package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseRequest;
import com.example.expensetracker.model.ExpenseEvent;
import com.example.expensetracker.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class ExpenseService {
    private final ExpenseRepository repository;
    private final ExpenseEventPublisher eventPublisher;

    public ExpenseService(ExpenseRepository repository, ExpenseEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public List<Expense> findAll(String ownerId) {
        return repository.findAll(ownerId);
    }

    public Optional<Expense> findById(String ownerId, long id) {
        return repository.findById(ownerId, id);
    }

    public Expense create(String ownerId, ExpenseRequest request) {
        validate(request);
        Expense created = repository.create(ownerId, toExpense(null, ownerId, request));
        eventPublisher.publish(ExpenseEvent.upsert(created));
        return created;
    }

    public Optional<Expense> update(String ownerId, long id, ExpenseRequest request) {
        validate(request);
        return repository.update(ownerId, id, toExpense(id, ownerId, request))
                .map(updated -> {
                    eventPublisher.publish(ExpenseEvent.upsert(updated));
                    return updated;
                });
    }

    public boolean delete(String ownerId, long id) {
        boolean deleted = repository.deleteById(ownerId, id);
        if (deleted) {
            eventPublisher.publish(ExpenseEvent.deleted(ownerId, id));
        }
        return deleted;
    }

    private Expense toExpense(Long id, String ownerId, ExpenseRequest request) {
        return new Expense(id, ownerId, request.description().trim(), request.category().trim(), request.amount(), request.date());
    }

    private void validate(ExpenseRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()
                || request.category() == null || request.category().isBlank()
                || !Double.isFinite(request.amount()) || request.amount() <= 0
                || request.date() == null || request.date().isAfter(LocalDate.now(ZoneId.of("Asia/Kolkata")))) {
            throw new IllegalArgumentException("Enter a description, category, positive amount, and a valid date not in the future.");
        }
    }
}