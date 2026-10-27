package com.example.summary;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
        String canonicalRecipient = users.users(bearerToken).stream()
                .map(LendBorrowDtos.UserView::username)
                .filter(username -> username.equalsIgnoreCase(recipient))
                .findFirst()
                .orElse(null);
        if (canonicalRecipient == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient must be a registered user");
        }
        return requests.create(currentUser, canonicalRecipient, direction, amount, description == null || description.isBlank() ? null : description);
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
        if (requests.decide(id, "APPROVE".equals(decision) ? "APPROVED" : "REJECTED") != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been decided");
        }
        return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public RepaymentOperation repay(long id, String currentUser, BigDecimal requestedAmount, String idempotencyKey) {
        if (requestedAmount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repayment amount is required");
        }
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key is required");
        }
        BigDecimal amount;
        try {
            amount = requestedAmount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount may have at most two decimal places");
        }
        if (amount.signum() <= 0 || amount.precision() > 19) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repayment must be positive and fit within 17 digits before decimals");
        }

        LendBorrowDtos.RequestView transaction = requests.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!"APPROVED".equals(transaction.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved transactions can be repaid");
        }
        if (!transaction.borrowerUsername().equalsIgnoreCase(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the borrower can record a repayment");
        }

        var existing = requests.repaymentByKey(id, idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().amount().compareTo(amount) != 0
                    || !existing.get().payerUsername().equalsIgnoreCase(currentUser)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This idempotency key was already used for a different repayment");
            }
            return new RepaymentOperation(existing.get(), true);
        }

        BigDecimal outstanding = transaction.amount().subtract(requests.sumRepayments(id));
        if (outstanding.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This transaction is already fully repaid");
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repayment cannot exceed the outstanding balance of " + outstanding.setScale(2));
        }
        return new RepaymentOperation(requests.createRepayment(id, currentUser, amount, idempotencyKey), false);
    }

    @Transactional(readOnly = true)
    public LendBorrowDtos.TransactionView transaction(long id, String currentUser) {
        LendBorrowDtos.RequestView transaction = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!"APPROVED".equals(transaction.status())
                || (!transaction.lenderUsername().equalsIgnoreCase(currentUser)
                && !transaction.borrowerUsername().equalsIgnoreCase(currentUser))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        return transactionView(transaction);
    }

    public List<LendBorrowDtos.RequestView> sent(String currentUser) {
        return requests.sent(currentUser);
    }

    public List<LendBorrowDtos.RequestView> received(String currentUser) {
        return requests.received(currentUser);
    }

    @Transactional(readOnly = true)
    public LendBorrowDtos.SummaryResponse overview(String currentUser) {
        BigDecimal lent = requests.sumApprovedAsLender(currentUser);
        BigDecimal borrowed = requests.sumApprovedAsBorrower(currentUser);
        List<LendBorrowDtos.TransactionView> confirmed = requests.confirmed(currentUser).stream()
                .map(this::transactionView)
                .toList();
        BigDecimal toReceive = BigDecimal.ZERO.setScale(2);
        BigDecimal toPay = BigDecimal.ZERO.setScale(2);
        Map<String, PersonBalance> perPerson = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (LendBorrowDtos.TransactionView transaction : confirmed) {
            BigDecimal balance = transaction.outstandingBalance();
            if (balance.signum() <= 0) continue;
            boolean isLender = transaction.lenderUsername().equalsIgnoreCase(currentUser);
            if (isLender) {
                toReceive = toReceive.add(balance);
                perPerson.computeIfAbsent(transaction.borrowerUsername(), PersonBalance::new).toReceive =
                        perPerson.get(transaction.borrowerUsername()).toReceive.add(balance);
            } else {
                toPay = toPay.add(balance);
                perPerson.computeIfAbsent(transaction.lenderUsername(), PersonBalance::new).toPay =
                        perPerson.get(transaction.lenderUsername()).toPay.add(balance);
            }
        }
        List<LendBorrowDtos.PersonOutstanding> outstandingByPerson = perPerson.values().stream()
                .map(balance -> new LendBorrowDtos.PersonOutstanding(balance.username, balance.toReceive, balance.toPay))
                .toList();
        LendBorrowDtos.Summary summary = new LendBorrowDtos.Summary(lent, borrowed, toReceive, toPay,
                requests.countPendingSent(currentUser), requests.countPendingReceived(currentUser));
        return new LendBorrowDtos.SummaryResponse(summary, requests.pendingSent(currentUser),
                requests.pendingReceived(currentUser), confirmed, outstandingByPerson);
    }

    private LendBorrowDtos.TransactionView transactionView(LendBorrowDtos.RequestView transaction) {
        List<LendBorrowDtos.RepaymentView> history = requests.repayments(transaction.id());
        BigDecimal totalRepaid = history.stream().map(LendBorrowDtos.RepaymentView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstanding = transaction.amount().subtract(totalRepaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String repaymentStatus = outstanding.signum() == 0 ? "FULLY_REPAID"
                : totalRepaid.signum() > 0 ? "PARTIALLY_REPAID" : "PENDING_REPAYMENT";
        return new LendBorrowDtos.TransactionView(transaction.id(), transaction.requesterUsername(),
                transaction.recipientUsername(), transaction.lenderUsername(), transaction.borrowerUsername(),
                transaction.direction(), transaction.amount(), transaction.description(), transaction.status(),
                transaction.createdAt(), transaction.decidedAt(), totalRepaid, outstanding, repaymentStatus, history);
    }

    public record RepaymentOperation(LendBorrowDtos.RepaymentView repayment, boolean duplicate) {
    }

    private static final class PersonBalance {
        private final String username;
        private BigDecimal toReceive = BigDecimal.ZERO.setScale(2);
        private BigDecimal toPay = BigDecimal.ZERO.setScale(2);

        private PersonBalance(String username) {
            this.username = username;
        }
    }
}
