-- =============================================================================
-- V1: platform core - clients (tenants), identity, RBAC and audit.
--
-- Roles and permissions are platform-wide reference data. Which roles a client
-- may use is decided by configuration (project.role.*), not by this data.
-- =============================================================================

CREATE TABLE clients (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_clients_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE roles (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(255) NULL,
    display_order INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(60)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    module_code VARCHAR(30)  NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    client_id            BIGINT       NOT NULL,
    username             VARCHAR(60)  NOT NULL,
    password             VARCHAR(100) NOT NULL,
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(150) NULL,
    mobile               VARCHAR(20)  NULL,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        DATETIME(6)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    created_by           BIGINT       NULL,
    updated_by           BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_client_username UNIQUE (client_id, username),
    CONSTRAINT fk_users_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE audit_logs (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    client_id         BIGINT       NOT NULL,
    entity_type       VARCHAR(60)  NOT NULL,
    entity_id         BIGINT       NULL,
    action            VARCHAR(40)  NOT NULL,
    summary           VARCHAR(500) NOT NULL,
    details           TEXT         NULL,
    performed_by_id   BIGINT       NULL,
    performed_by_name VARCHAR(150) NULL,
    performed_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_logs_entity (client_id, entity_type, entity_id),
    KEY idx_audit_logs_performed_at (client_id, performed_at),
    CONSTRAINT fk_audit_logs_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
