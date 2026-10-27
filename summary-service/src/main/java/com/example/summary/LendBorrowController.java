package com.example.summary;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestController
@RequestMapping("/api/lend-borrow")
public class LendBorrowController {
    private final LendBorrowService lendBorrow;

    public LendBorrowController(LendBorrowService lendBorrow) {
        this.lendBorrow = lendBorrow;
    }

    @GetMapping("/users")
    public List<LendBorrowDtos.UserView> users(@AuthenticationPrincipal Jwt principal) {
        return lendBorrow.users(bearer(principal), principal.getSubject());
    }

    @PostMapping("/requests")
    public ResponseEntity<LendBorrowDtos.RequestView> create(@RequestBody LendBorrowDtos.CreateRequest request,
                                      @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(lendBorrow.create(principal.getSubject(), request, bearer(principal)));
    }

    @GetMapping("/requests/sent")
    public List<LendBorrowDtos.RequestView> sent(@AuthenticationPrincipal Jwt principal) {
        return lendBorrow.sent(principal.getSubject());
    }

    @GetMapping("/requests/received")
    public List<LendBorrowDtos.RequestView> received(@AuthenticationPrincipal Jwt principal) {
        return lendBorrow.received(principal.getSubject());
    }

    @PostMapping("/requests/{id}/decision")
    public LendBorrowDtos.RequestView decide(@PathVariable long id,
                                              @RequestBody LendBorrowDtos.Decision decision,
                                              @AuthenticationPrincipal Jwt principal) {
        return lendBorrow.decide(id, principal.getSubject(), decision == null ? null : decision.status());
    }

    @GetMapping("/overview")
    public LendBorrowDtos.SummaryResponse overview(@AuthenticationPrincipal Jwt principal) {
        return lendBorrow.overview(principal.getSubject());
    }

    @GetMapping("/requests/{id}")
    public LendBorrowDtos.TransactionView transaction(@PathVariable long id,
                                                       @AuthenticationPrincipal Jwt principal) {
        return lendBorrow.transaction(id, principal.getSubject());
    }

    @PostMapping("/requests/{id}/repayments")
    public ResponseEntity<LendBorrowDtos.RepaymentView> repay(@PathVariable long id,
                                                               @RequestBody LendBorrowDtos.RepaymentRequest request,
                                                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                               @AuthenticationPrincipal Jwt principal) {
        LendBorrowService.RepaymentOperation operation = lendBorrow.repay(id, principal.getSubject(),
                request == null ? null : request.amount(), idempotencyKey);
        return ResponseEntity.status(operation.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(operation.repayment());
    }

    private String bearer(Jwt principal) {
        return "Bearer " + principal.getTokenValue();
    }
}

@RestControllerAdvice
class LendBorrowExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<java.util.Map<String, String>> handleInvalidBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(java.util.Map.of("error", "Request body is invalid or incomplete"));
    }
}
