-- The board now reads one page per status column, ordered by the chosen sort.
-- Composite indexes let PostgreSQL walk each column in order and stop after
-- one page instead of sorting every task in that status.

-- Default sort: newest first (id breaks ties between identical timestamps).
CREATE INDEX ix_tasks_status_created ON tasks (status, created_at DESC, id DESC);

-- "Due date" sort: earliest first, tasks without a due date last.
CREATE INDEX ix_tasks_status_due ON tasks (status, due_date ASC NULLS LAST, id DESC);

-- Superseded: every query that used it is served by the leading column above.
DROP INDEX ix_tasks_status;

-- Free-text search (ILIKE '%term%') still scans the filtered rows. At larger
-- scale, add a pg_trgm GIN index on title/description.
