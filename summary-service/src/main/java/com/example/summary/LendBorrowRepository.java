package com.example.summary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class LendBorrowRepository {
    private static final RowMapper<LendBorrowDtos.RequestView> REQUEST_MAPPER = LendBorrowRepository::mapRequest;
    private final JdbcTemplate jdbc;

    public LendBorrowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LendBorrowDtos.RequestView create(String requester, String recipient, String direction, BigDecimal amount, String description) {
        String lender = "LEND".equals(direction) ? requester : recipient;
        String borrower = "LEND".equals(direction) ? recipient : requester;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO lend_borrow_requests (requester_username, recipient_username, lender_username, borrower_username, direction, amount, description, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')", new String[]{"id"});
            statement.setString(1, requester);
            statement.setString(2, recipient);
            statement.setString(3, lender);
            statement.setString(4, borrower);
            statement.setString(5, direction);
            statement.setBigDecimal(6, amount);
            statement.setString(7, description);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        return findById(id.longValue()).orElseThrow();
    }

    public Optional<LendBorrowDtos.RequestView> findForUpdate(long id) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE id = ? FOR UPDATE", REQUEST_MAPPER, id).stream().findFirst();
    }

    public void decide(long id, String status) {
        jdbc.update("UPDATE lend_borrow_requests SET status = ?, decided_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'PENDING'", status, id);
    }

    public Optional<LendBorrowDtos.RequestView> findById(long id) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE id = ?", REQUEST_MAPPER, id).stream().findFirst();
    }

    public List<LendBorrowDtos.RequestView> sent(String username) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE requester_username = ? ORDER BY created_at DESC, id DESC", REQUEST_MAPPER, username);
    }

    public List<LendBorrowDtos.RequestView> received(String username) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE recipient_username = ? ORDER BY created_at DESC, id DESC", REQUEST_MAPPER, username);
    }

    public List<LendBorrowDtos.RequestView> pendingSent(String username) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE requester_username = ? AND status = 'PENDING' ORDER BY created_at DESC, id DESC", REQUEST_MAPPER, username);
    }

    public List<LendBorrowDtos.RequestView> pendingReceived(String username) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE recipient_username = ? AND status = 'PENDING' ORDER BY created_at DESC, id DESC", REQUEST_MAPPER, username);
    }

    public List<LendBorrowDtos.RequestView> confirmed(String username) {
        return jdbc.query("SELECT * FROM lend_borrow_requests WHERE status = 'APPROVED' AND (lender_username = ? OR borrower_username = ?) ORDER BY decided_at DESC, id DESC", REQUEST_MAPPER, username, username);
    }

    public BigDecimal sumApprovedAsLender(String username) {
        return sum("SELECT COALESCE(SUM(amount), 0) FROM lend_borrow_requests WHERE lender_username = ? AND status = 'APPROVED'", username);
    }

    public BigDecimal sumApprovedAsBorrower(String username) {
        return sum("SELECT COALESCE(SUM(amount), 0) FROM lend_borrow_requests WHERE borrower_username = ? AND status = 'APPROVED'", username);
    }

    public long countPendingSent(String username) {
        return count("SELECT COUNT(*) FROM lend_borrow_requests WHERE requester_username = ? AND status = 'PENDING'", username);
    }

    public long countPendingReceived(String username) {
        return count("SELECT COUNT(*) FROM lend_borrow_requests WHERE recipient_username = ? AND status = 'PENDING'", username);
    }

    private BigDecimal sum(String sql, String username) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, username);
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private long count(String sql, String username) {
        Long value = jdbc.queryForObject(sql, Long.class, username);
        return value == null ? 0 : value;
    }

    private static LendBorrowDtos.RequestView mapRequest(ResultSet row, int index) throws SQLException {
        return new LendBorrowDtos.RequestView(row.getLong("id"), row.getString("requester_username"),
                row.getString("recipient_username"), row.getString("lender_username"), row.getString("borrower_username"),
                row.getString("direction"), row.getBigDecimal("amount"), row.getString("description"), row.getString("status"),
                row.getTimestamp("created_at").toLocalDateTime(),
                row.getTimestamp("decided_at") == null ? null : row.getTimestamp("decided_at").toLocalDateTime());
    }
}