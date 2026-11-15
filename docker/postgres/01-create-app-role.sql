-- Runs once, on first start of an empty volume. Creates the least-privilege
-- runtime role; table grants are applied by Flyway migration V4.
-- Local development credentials only.
CREATE ROLE taskmanager_app LOGIN PASSWORD 'taskmanager_app';
GRANT CONNECT ON DATABASE taskmanager TO taskmanager_app;
