alter table follow_up_task
    add column result_note varchar(500);

alter table follow_up_task
    add column completed_at timestamptz;

create index if not exists idx_follow_up_task_completed
    on follow_up_task (demo_session_id, demo_run_id, completed_at, follow_up_id);
