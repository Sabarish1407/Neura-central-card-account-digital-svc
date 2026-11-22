-- V1__init_card_banking_schema.sql
-- Flyway baseline migration for Debit Card Banking Digital Service
-- Compatible with both PostgreSQL and H2

CREATE TABLE IF NOT EXISTS accounts (
    account_id VARCHAR(64) NOT NULL PRIMARY KEY,
    account_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id VARCHAR(64) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    available_balance NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cards (
    card_id VARCHAR(64) NOT NULL PRIMARY KEY,
    card_token VARCHAR(64) NOT NULL UNIQUE,
    card_number_hash VARCHAR(64) NOT NULL UNIQUE,
    card_number_encrypted VARCHAR(512) NOT NULL,
    card_number_masked VARCHAR(32) NOT NULL,
    last_four_digits VARCHAR(4) NOT NULL,
    card_holder_name VARCHAR(255) NOT NULL,
    card_type VARCHAR(32) NOT NULL,
    card_network VARCHAR(32) NOT NULL,
    expiry_date VARCHAR(8) NOT NULL,
    cvv_hash VARCHAR(255) NOT NULL,
    pin_hash VARCHAR(255),
    card_status VARCHAR(32) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    daily_limit NUMERIC(19, 2) NOT NULL,
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS card_transactions (
    transaction_id VARCHAR(64) NOT NULL PRIMARY KEY,
    card_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    reference_number VARCHAR(64) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    merchant_name VARCHAR(255),
    authorization_code VARCHAR(32),
    decline_reason VARCHAR(32) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_card_token ON cards(card_token);
CREATE INDEX IF NOT EXISTS idx_card_account_id ON cards(account_id);
CREATE INDEX IF NOT EXISTS idx_txn_card_id ON card_transactions(card_id);
CREATE INDEX IF NOT EXISTS idx_txn_ref_num ON card_transactions(reference_number);
