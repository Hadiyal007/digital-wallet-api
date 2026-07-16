-- V1__initial_schema.sql (MySQL — used by the "dev" profile)
--
-- Hand-written to exactly match the current state of every @Entity class,
-- as a snapshot of what ddl-auto=update had already built incrementally
-- over Tasks 1-10. From this point forward, ddl-auto=validate (see
-- application-dev.properties) means Hibernate only CHECKS the schema
-- matches the entities - it never creates or alters tables again. Every
-- future schema change must be a new Flyway migration (V2__..., V3__...),
-- never a live edit to this file.

CREATE TABLE users (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255),
    username  VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL,
    password  VARCHAR(255),
    role      VARCHAR(20),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE wallets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_number VARCHAR(255) NOT NULL,
    balance       DECIMAL(19,2),
    status        VARCHAR(20),
    created_at    DATETIME,
    version       BIGINT,
    user_id       BIGINT,
    CONSTRAINT uk_wallets_wallet_number UNIQUE (wallet_number),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id), -- enforces the @OneToOne
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE transactions (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount                     DECIMAL(19,2) NOT NULL,
    type                       VARCHAR(20) NOT NULL,
    description                VARCHAR(255),
    created_at                 DATETIME NOT NULL,
    status                     VARCHAR(20),
    reversal_of_transaction_id BIGINT,
    sender_wallet_id           BIGINT,
    receiver_wallet_id         BIGINT,
    CONSTRAINT fk_tx_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_tx_receiver_wallet FOREIGN KEY (receiver_wallet_id) REFERENCES wallets(id)
) ENGINE=InnoDB;

CREATE TABLE beneficiaries (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                    BIGINT NOT NULL,
    beneficiary_wallet_number  VARCHAR(255) NOT NULL,
    beneficiary_name           VARCHAR(255) NOT NULL,
    nickname                   VARCHAR(255),
    added_at                   DATETIME,
    CONSTRAINT fk_beneficiary_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username                    VARCHAR(255) NOT NULL,
    action                      VARCHAR(20) NOT NULL,
    wallet_number               VARCHAR(255) NOT NULL,
    amount                      VARCHAR(255),
    counterparty_wallet_number  VARCHAR(255),
    status                      VARCHAR(20) NOT NULL,
    failure_reason              VARCHAR(500),
    timestamp                   DATETIME NOT NULL,
    ip_address                  VARCHAR(255)
) ENGINE=InnoDB;

-- Matches AuditLog's @Table(indexes = {...}) exactly
CREATE INDEX idx_audit_username ON audit_logs (username);
CREATE INDEX idx_audit_timestamp ON audit_logs (timestamp);

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    token       VARCHAR(255) NOT NULL,
    user_id     BIGINT NOT NULL,
    expiry_date DATETIME NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE transfer_otps (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_id            VARCHAR(255) NOT NULL,
    username                VARCHAR(255) NOT NULL,
    sender_wallet_id        BIGINT NOT NULL,
    receiver_wallet_number  VARCHAR(255) NOT NULL,
    amount                  DECIMAL(19,2) NOT NULL,
    description             VARCHAR(255),
    otp_code                VARCHAR(255) NOT NULL,
    expiry_date             DATETIME NOT NULL,
    attempts                INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfer_otps_reference_id UNIQUE (reference_id)
) ENGINE=InnoDB;
