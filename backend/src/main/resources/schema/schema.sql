CREATE TABLE IF NOT EXISTS skill (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(1000),
    package_url       VARCHAR(500),
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    developer         VARCHAR(100),
    download_count    INT DEFAULT 0,
    use_count         INT DEFAULT 0,
    visibility_type   VARCHAR(50)  DEFAULT 'PUBLIC',
    visibility_config VARCHAR(2000),
    is_delisted       BOOLEAN DEFAULT FALSE,
    delisted_reason   VARCHAR(500),
    delisted_at       TIMESTAMP,
    delisted_by       VARCHAR(100),
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP,
    last_published_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS skill_version (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id                    BIGINT NOT NULL,
    version                     VARCHAR(20) NOT NULL,
    package_url                 VARCHAR(500),
    manifest_url                VARCHAR(500),
    changelog                   VARCHAR(500),
    status                      VARCHAR(20) DEFAULT 'DRAFT',
    is_latest                   BOOLEAN DEFAULT FALSE,
    is_rollback                 BOOLEAN DEFAULT FALSE,
    rolled_back_from            VARCHAR(20),
    skill_name_snapshot         VARCHAR(100),
    skill_description_snapshot  VARCHAR(1000),
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS publish_request (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id                    BIGINT,
    skill_name                  VARCHAR(100),
    changelog                   VARCHAR(500),
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applicant                   VARCHAR(100),
    reviewer                    VARCHAR(100),
    reject_reason               VARCHAR(500),
    skill_updated_at_snapshot   TIMESTAMP,
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at                 TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(100),
    event       VARCHAR(50),
    skill_id    BIGINT,
    skill_name  VARCHAR(100),
    message     VARCHAR(1000),
    read        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS skill_change_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_name  VARCHAR(100),
    change_type VARCHAR(50),
    details     VARCHAR(2000),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);