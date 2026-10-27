CREATE TABLE IF NOT EXISTS lend_borrow_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester_username VARCHAR(80) NOT NULL,
    recipient_username VARCHAR(80) NOT NULL,
    lender_username VARCHAR(80) NOT NULL,
    borrower_username VARCHAR(80) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(240),
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at TIMESTAMP(6) NULL,
    CONSTRAINT chk_lend_borrow_direction CHECK (direction IN ('LEND', 'BORROW')),
    CONSTRAINT chk_lend_borrow_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_lend_borrow_amount CHECK (amount > 0),
    INDEX idx_lend_borrow_requester (requester_username, created_at),
    INDEX idx_lend_borrow_recipient (recipient_username, created_at),
    INDEX idx_lend_borrow_lender (lender_username, status),
    INDEX idx_lend_borrow_borrower (borrower_username, status)
);

CREATE TABLE IF NOT EXISTS lend_borrow_repayments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    payer_username VARCHAR(80) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_lend_borrow_repayment_amount CHECK (amount > 0),
    CONSTRAINT fk_lend_borrow_repayment_transaction FOREIGN KEY (transaction_id)
        REFERENCES lend_borrow_requests (id) ON DELETE CASCADE,
    CONSTRAINT uq_lend_borrow_repayment_idempotency UNIQUE (transaction_id, idempotency_key),
    INDEX idx_lend_borrow_repayment_transaction (transaction_id, created_at),
    INDEX idx_lend_borrow_repayment_payer (payer_username, created_at)
);
