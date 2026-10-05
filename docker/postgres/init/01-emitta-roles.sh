#!/usr/bin/env bash

set -Eeuo pipefail

echo "=============================================="
echo "Initializing Emitta PostgreSQL roles"
echo "=============================================="

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=db_name="$POSTGRES_DB" \
  --set=migrator_password="$DB_MIGRATION_PASSWORD" \
  --set=app_password="$DB_APP_PASSWORD" \
  --set=readonly_password="$DB_READONLY_PASSWORD" <<'EOSQL'

-- ============================================================
-- EMITTA DATABASE SECURITY
-- ============================================================

-- ------------------------------------------------------------
-- Roles
-- ------------------------------------------------------------

SELECT 'CREATE ROLE emitta_migrator LOGIN'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'emitta_migrator'
)
\gexec

SELECT 'CREATE ROLE emitta_app LOGIN'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'emitta_app'
)
\gexec

SELECT 'CREATE ROLE emitta_readonly LOGIN'
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = 'emitta_readonly'
)
\gexec


-- ------------------------------------------------------------
-- Passwords
-- ------------------------------------------------------------

ALTER ROLE emitta_migrator PASSWORD :'migrator_password';
ALTER ROLE emitta_app PASSWORD :'app_password';
ALTER ROLE emitta_readonly PASSWORD :'readonly_password';


-- ------------------------------------------------------------
-- Explicit role restrictions
-- ------------------------------------------------------------

ALTER ROLE emitta_migrator
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

ALTER ROLE emitta_app
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

ALTER ROLE emitta_readonly
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;


-- ============================================================
-- DATABASE ACCESS
-- ============================================================

-- PostgreSQL normalmente da CONNECT/TEMP a PUBLIC.
-- Para Emitta queremos control explícito.

REVOKE ALL
ON DATABASE :"db_name"
FROM PUBLIC;

GRANT CONNECT
ON DATABASE :"db_name"
TO
    emitta_migrator,
    emitta_app,
    emitta_readonly;


-- ============================================================
-- PUBLIC SCHEMA HARDENING
-- ============================================================

REVOKE CREATE
ON SCHEMA public
FROM PUBLIC;


-- ============================================================
-- EMITTA SCHEMA
-- ============================================================

CREATE SCHEMA IF NOT EXISTS emitta
    AUTHORIZATION emitta_migrator;

ALTER SCHEMA emitta
    OWNER TO emitta_migrator;


-- ============================================================
-- APPLICATION PERMISSIONS
-- ============================================================

GRANT USAGE
ON SCHEMA emitta
TO emitta_app;

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA emitta
TO emitta_app;

GRANT USAGE, SELECT
ON ALL SEQUENCES IN SCHEMA emitta
TO emitta_app;


-- ============================================================
-- READ ONLY PERMISSIONS
-- ============================================================

GRANT USAGE
ON SCHEMA emitta
TO emitta_readonly;

GRANT SELECT
ON ALL TABLES IN SCHEMA emitta
TO emitta_readonly;


-- ============================================================
-- DEFAULT PRIVILEGES
--
-- Muy importante:
-- Flyway creará tablas nuevas como emitta_migrator.
-- Estas reglas dan permisos automáticamente a la aplicación.
-- ============================================================

ALTER DEFAULT PRIVILEGES
FOR ROLE emitta_migrator
IN SCHEMA emitta
GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLES
TO emitta_app;

ALTER DEFAULT PRIVILEGES
FOR ROLE emitta_migrator
IN SCHEMA emitta
GRANT USAGE, SELECT
ON SEQUENCES
TO emitta_app;

ALTER DEFAULT PRIVILEGES
FOR ROLE emitta_migrator
IN SCHEMA emitta
GRANT SELECT
ON TABLES
TO emitta_readonly;


-- ============================================================
-- SEARCH PATH
-- ============================================================

ALTER ROLE emitta_migrator
IN DATABASE :"db_name"
SET search_path = emitta, public;

ALTER ROLE emitta_app
IN DATABASE :"db_name"
SET search_path = emitta, public;

ALTER ROLE emitta_readonly
IN DATABASE :"db_name"
SET search_path = emitta, public;


-- ============================================================
-- APPLICATION SAFETY
-- ============================================================

ALTER ROLE emitta_app
IN DATABASE :"db_name"
SET statement_timeout = '30s';

ALTER ROLE emitta_app
IN DATABASE :"db_name"
SET lock_timeout = '5s';

ALTER ROLE emitta_app
IN DATABASE :"db_name"
SET idle_in_transaction_session_timeout = '60s';

EOSQL

echo "=============================================="
echo "Emitta PostgreSQL initialization completed"
echo "=============================================="