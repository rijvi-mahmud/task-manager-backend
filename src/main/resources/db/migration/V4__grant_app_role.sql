-- Least privilege: the application connects as a role with data access only
-- (no DDL). Migrations run as the schema owner. If the role does not exist
-- (e.g. a throwaway test database), this migration is a no-op.
DO $$
DECLARE
    app_role   text := '${approle}';
    app_schema text := '${flyway:defaultSchema}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', app_schema, app_role);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO %I', app_schema, app_role);
        EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I', app_schema, app_role);
        -- Tables created by later migrations get the same grants automatically.
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', app_schema, app_role);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT USAGE, SELECT ON SEQUENCES TO %I', app_schema, app_role);
        -- The app must not rewrite migration history.
        EXECUTE format('REVOKE INSERT, UPDATE, DELETE ON %I.flyway_schema_history FROM %I', app_schema, app_role);
    END IF;
END
$$;
