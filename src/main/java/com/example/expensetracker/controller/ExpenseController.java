package com.example.expensetracker.controller;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseRequest;
import com.example.expensetracker.service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public List<Expense> findAll(@AuthenticationPrincipal Jwt principal) {
        return service.findAll(principal.getSubject());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Expense> findById(@PathVariable long id, @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.of(service.findById(principal.getSubject(), id));
    }

    @PostMapping
    public ResponseEntity<Expense> create(@RequestBody ExpenseRequest request, @AuthenticationPrincipal Jwt principal) {
        Expense created = service.create(principal.getSubject(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Expense> update(@PathVariable long id, @RequestBody ExpenseRequest request,
                                          @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.of(service.update(principal.getSubject(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Jwt principal) {
        return service.delete(principal.getSubject(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidExpense(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", exception.getMessage()));
    }
}