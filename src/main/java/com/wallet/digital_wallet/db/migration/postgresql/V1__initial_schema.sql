-- V1__initial_schema.sql (PostgreSQL — used by the "prod" profile)
--
-- Same schema as db/migration/mysql/V1__initial_schema.sql, translated to
-- Postgres syntax (BIGSERIAL instead of AUTO_INCREMENT, NUMERIC instead
-- of DECIMAL, TIMESTAMP instead of DATETIME). Both files exist because
-- spring.flyway.locations=classpath:db/migration/{vendor} - Flyway
-- automatically picks the folder matching the active database engine, so
-- dev (MySQL) and prod (Postgres) each run the syntax that's actually
-- valid for them, while sharing the same version number and staying
-- conceptually in sync.

CREATE TABLE users (
    id        BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255),
    username  VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL,
    password  VARCHAR(255),
    role      VARCHAR(20),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE wallets (
    id            BIGSERIAL PRIMARY KEY,
    wallet_number VARCHAR(255) NOT NULL,
    balance       NUMERIC(19,2),
    status        VARCHAR(20),
    created_at    TIMESTAMP,
    version       BIGINT,
    user_id       BIGINT,
    CONSTRAINT uk_wallets_wallet_number UNIQUE (wallet_number),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id), -- enforces the @OneToOne
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
    id                         BIGSERIAL PRIMARY KEY,
    amount                     NUMERIC(19,2) NOT NULL,
    type                       VARCHAR(20) NOT NULL,
    description                VARCHAR(255),
    created_at                 TIMESTAMP NOT NULL,
    status                     VARCHAR(20),
    reversal_of_transaction_id BIGINT,
    sender_wallet_id           BIGINT,
    receiver_wallet_id         BIGINT,
    CONSTRAINT fk_tx_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_tx_receiver_wallet FOREIGN KEY (receiver_wallet_id) REFERENCES wallets(id)
);

CREATE TABLE beneficiaries (
    id                         BIGSERIAL PRIMARY KEY,
    user_id                    BIGINT NOT NULL,
    beneficiary_wallet_number  VARCHAR(255) NOT NULL,
    beneficiary_name           VARCHAR(255) NOT NULL,
    nickname                   VARCHAR(255),
    added_at                   TIMESTAMP,
    CONSTRAINT fk_beneficiary_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE audit_logs (
    id                          BIGSERIAL PRIMARY KEY,
    username                    VARCHAR(255) NOT NULL,
    action                      VARCHAR(20) NOT NULL,
    wallet_number               VARCHAR(255) NOT NULL,
    amount                      VARCHAR(255),
    counterparty_wallet_number  VARCHAR(255),
    status                      VARCHAR(20) NOT NULL,
    failure_reason              VARCHAR(500),
    timestamp                   TIMESTAMP NOT NULL,
    ip_address                  VARCHAR(255)
);

-- Matches AuditLog's @Table(indexes = {...}) exactly
CREATE INDEX idx_audit_username ON audit_logs (username);
CREATE INDEX idx_audit_timestamp ON audit_logs (timestamp);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL,
    user_id     BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transfer_otps (
    id                      BIGSERIAL PRIMARY KEY,
    reference_id            VARCHAR(255) NOT NULL,
    username                VARCHAR(255) NOT NULL,
    sender_wallet_id        BIGINT NOT NULL,
    receiver_wallet_number  VARCHAR(255) NOT NULL,
    amount                  NUMERIC(19,2) NOT NULL,
    description             VARCHAR(255),
    otp_code                VARCHAR(255) NOT NULL,
    expiry_date             TIMESTAMP NOT NULL,
    attempts                INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_transfer_otps_reference_id UNIQUE (reference_id)
);
