CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    user_role VARCHAR(50) NOT NULL DEFAULT 'BEFORE_AGREED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_provider_account UNIQUE (provider, provider_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

CREATE TABLE IF NOT EXISTS median_incomes (
    id BIGSERIAL PRIMARY KEY,
    year INT NOT NULL CHECK (year > 0),
    household_size INT NOT NULL CHECK (household_size > 0),
    earn_percent INT NOT NULL CHECK (earn_percent > 0),
    monthly_income INT NOT NULL CHECK (monthly_income >= 0),
    CONSTRAINT uq_year_household_size_earn_percent UNIQUE (year, household_size, earn_percent)
);

CREATE TABLE IF NOT EXISTS terms (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    is_required BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS term_versions (
    id BIGSERIAL PRIMARY KEY,
    term_id BIGINT NOT NULL,
    major_version INTEGER NOT NULL,
    minor_version INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_term_version_term FOREIGN KEY (term_id) REFERENCES terms(id) ON DELETE CASCADE,
    CONSTRAINT uq_term_versions_version UNIQUE (term_id, major_version, minor_version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_term_version_current_per_term
    ON term_versions(term_id) WHERE is_current = TRUE;

CREATE TABLE IF NOT EXISTS user_term_agreements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    term_version_id BIGINT NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_term_agreement_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_term_agreements_version FOREIGN KEY (term_version_id) REFERENCES term_versions(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_term_agreements_user_version UNIQUE (user_id, term_version_id),
    CONSTRAINT chk_user_term_agreements_agreed_at CHECK (
        (agreed = TRUE AND agreed_at IS NOT NULL) OR (agreed = FALSE)
    )
);

CREATE TABLE IF NOT EXISTS category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS category_option (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES category(id),
    value VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS product_source (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL
);

INSERT INTO product_source (code, name) VALUES
('FSS', '금융감독원'),
('ONTONG', '온통청년')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

CREATE TABLE IF NOT EXISTS provider (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES product_source(id),
    code VARCHAR(100),
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS product (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES product_source(id),
    type VARCHAR(20) NOT NULL,
    product_code VARCHAR(100),
    product_name VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_properties (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES provider(id),
    base_rate DECIMAL(5,2),
    max_rate DECIMAL(5,2),
    gov_contribution_rate DECIMAL(5,2),
    min_monthly_limit BIGINT,
    max_monthly_limit BIGINT,
    min_age INT,
    max_age INT,
    earn_max_amt BIGINT,
    earn_percent INT,
    min_tenure_months INT,
    requires_homeless BOOLEAN NOT NULL DEFAULT FALSE,
    requires_householder BOOLEAN NOT NULL DEFAULT FALSE,
    is_joinable BOOLEAN NOT NULL DEFAULT TRUE,
    apply_url VARCHAR(500),
    intr_rate_type VARCHAR(30),
    save_trm INT
);

DROP INDEX IF EXISTS uk_product_properties_bank_scrape;

ALTER TABLE product_properties
    DROP COLUMN IF EXISTS property_origin,
    DROP COLUMN IF EXISTS join_target,
    DROP COLUMN IF EXISTS join_period_text,
    DROP COLUMN IF EXISTS join_amount_text,
    DROP COLUMN IF EXISTS join_method,
    DROP COLUMN IF EXISTS base_rate_text,
    DROP COLUMN IF EXISTS max_rate_text,
    DROP COLUMN IF EXISTS preferential_condition,
    DROP COLUMN IF EXISTS depositor_protection_text,
    DROP COLUMN IF EXISTS source_hash,
    DROP COLUMN IF EXISTS scraped_at;

CREATE TABLE IF NOT EXISTS product_property_keyword (
    id BIGSERIAL PRIMARY KEY,
    product_property_id BIGINT NOT NULL REFERENCES product_properties(id) ON DELETE CASCADE,
    keyword_code VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS product_raw (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    type VARCHAR(20) NOT NULL,
    external_id VARCHAR(150) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    raw_json TEXT NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    normalized_at TIMESTAMP WITH TIME ZONE,
    normalizer_version INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_product_raw_source_external_id UNIQUE (source, external_id)
);

CREATE INDEX IF NOT EXISTS idx_product_raw_source ON product_raw(source);
CREATE INDEX IF NOT EXISTS idx_product_raw_normalized ON product_raw(source, normalized_at);
