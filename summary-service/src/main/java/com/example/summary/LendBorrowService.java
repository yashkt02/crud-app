package com.example.summary;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Service
public class LendBorrowService {
    private static final Set<String> DIRECTIONS = Set.of("LEND", "BORROW");
    private final LendBorrowRepository requests;
    private final UserDirectoryClient users;

    public LendBorrowService(LendBorrowRepository requests, UserDirectoryClient users) {
        this.requests = requests;
        this.users = users;
    }

    public List<LendBorrowDtos.UserView> users(String bearerToken, String currentUser) {
        return users.users(bearerToken).stream().filter(user -> !user.username().equalsIgnoreCase(currentUser)).toList();
    }

    @Transactional
    public LendBorrowDtos.RequestView create(String currentUser, LendBorrowDtos.CreateRequest request, String bearerToken) {
        if (request == null || request.recipientUsername() == null || request.direction() == null || request.amount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient, direction, and amount are required");
        }
        String recipient = request.recipientUsername().trim();
        String direction = request.direction().trim().toUpperCase();
        BigDecimal amount;
        try {
            amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount may have at most two decimal places");
        }
        if (recipient.isBlank() || recipient.equalsIgnoreCase(currentUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose another registered user");
        }
        if (!DIRECTIONS.contains(direction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Direction must be LEND or BORROW");
        }
        if (amount.signum() <= 0 || amount.precision() > 19) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive and fit within 17 digits before decimals");
        }
        String description = request.description() == null ? null : request.description().trim();
        if (description != null && description.length() > 240) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description must be 240 characters or fewer");
        }
        if (users.users(bearerToken).stream().noneMatch(user -> user.username().equalsIgnoreCase(recipient))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient must be a registered user");
        }
        return requests.create(currentUser, recipient, direction, amount, description == null || description.isBlank() ? null : description);
    }

    @Transactional
    public LendBorrowDtos.RequestView decide(long id, String currentUser, String decision) {
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVE or REJECT");
        }
        LendBorrowDtos.RequestView request = requests.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (request.requesterUsername().equalsIgnoreCase(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot decide your own request");
        }
        if (!request.recipientUsername().equalsIgnoreCase(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the intended recipient can decide this request");
        }
        if (!"PENDING".equals(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been decided");
        }
        requests.decide(id, "APPROVE".equals(decision) ? "APPROVED" : "REJECTED");
        return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<LendBorrowDtos.RequestView> sent(String currentUser) {
        return requests.sent(currentUser);
    }

    public List<LendBorrowDtos.RequestView> received(String currentUser) {
        return requests.received(currentUser);
    }

    public LendBorrowDtos.SummaryResponse overview(String currentUser) {
        BigDecimal lent = requests.sumApprovedAsLender(currentUser);
        BigDecimal borrowed = requests.sumApprovedAsBorrower(currentUser);
        LendBorrowDtos.Summary summary = new LendBorrowDtos.Summary(lent, borrowed, lent, borrowed,
                requests.countPendingSent(currentUser), requests.countPendingReceived(currentUser));
        return new LendBorrowDtos.SummaryResponse(summary, requests.pendingSent(currentUser),
                requests.pendingReceived(currentUser), requests.confirmed(currentUser));
    }
}