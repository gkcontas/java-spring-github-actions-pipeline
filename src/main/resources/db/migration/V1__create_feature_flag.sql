CREATE TABLE feature_flag (
    flag_key           VARCHAR(64)  PRIMARY KEY,
    description        VARCHAR(255) NOT NULL,
    enabled            BOOLEAN      NOT NULL,
    rollout_percentage INTEGER      NOT NULL CHECK (rollout_percentage BETWEEN 0 AND 100),
    version            BIGINT       NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL
);
