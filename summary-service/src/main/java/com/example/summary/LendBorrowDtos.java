package com.example.summary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class LendBorrowDtos {
    private LendBorrowDtos() {
    }

    public record CreateRequest(String recipientUsername, String direction, BigDecimal amount, String description) {
    }

    public record RequestView(long id, String requesterUsername, String recipientUsername, String lenderUsername,
                              String borrowerUsername, String direction, BigDecimal amount, String description,
                              String status, LocalDateTime createdAt, LocalDateTime decidedAt) {
    }

    public record Summary(BigDecimal totalLent, BigDecimal totalBorrowed, BigDecimal outstandingToReceive,
                          BigDecimal outstandingToPay, long pendingSent, long pendingReceived) {
    }

    public record UserView(String username, String role) {
    }

    public record Decision(String status) {
    }

    public record SummaryResponse(Summary summary, List<RequestView> pendingSent, List<RequestView> pendingReceived,
                                  List<RequestView> confirmedHistory) {
    }
}