-- Defense in depth: the database enforces the same invariants as the domain
-- model, so data stays valid even if written by another client, a script or
-- a future service that bypasses the application.

-- Deleting a member must never leave a dangling assignment.
ALTER TABLE tasks DROP CONSTRAINT fk_tasks_assignee;
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_assignee
        FOREIGN KEY (assignee_id) REFERENCES members (id) ON DELETE SET NULL;

ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_title_not_blank CHECK (btrim(title) <> ''),
    ADD CONSTRAINT ck_tasks_version_non_negative CHECK (version >= 0),
    ADD CONSTRAINT ck_tasks_updated_after_created CHECK (updated_at >= created_at),
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now();

-- Emails are stored normalized (lower-case), which makes the existing unique
-- constraint effectively case-insensitive.
ALTER TABLE members
    ADD CONSTRAINT ck_members_name_not_blank CHECK (btrim(name) <> ''),
    ADD CONSTRAINT ck_members_email_lowercase CHECK (email = lower(email)),
    ADD CONSTRAINT ck_members_email_format CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+$');

COMMENT ON TABLE members IS 'Team members that tasks can be assigned to.';
COMMENT ON COLUMN members.email IS 'Unique, stored lower-case.';

COMMENT ON TABLE tasks IS 'Units of work tracked on the board.';
COMMENT ON COLUMN tasks.status IS 'Workflow state: TODO, IN_PROGRESS or DONE.';
COMMENT ON COLUMN tasks.priority IS 'LOW, MEDIUM or HIGH.';
COMMENT ON COLUMN tasks.due_date IS 'Optional calendar date (no time zone). Overdue = past due and not DONE.';
COMMENT ON COLUMN tasks.assignee_id IS 'Assigned member; set to NULL when the member is deleted.';
COMMENT ON COLUMN tasks.version IS 'Optimistic-locking counter, incremented on every update.';
