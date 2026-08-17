update follow_up_task
   set completed_at = updated_at
 where status = 'COMPLETED'
   and completed_at is null;

update follow_up_task
   set completed_at = null
 where status <> 'COMPLETED'
   and completed_at is not null;

update follow_up_task
   set result_note = null
 where status = 'SCHEDULED'
   and result_note is not null;

update follow_up_task
   set result_note = '기존 후속조치 상태 이관'
 where status in ('COMPLETED', 'CANCELLED')
   and (result_note is null or btrim(result_note) = '');

alter table follow_up_task
    add constraint ck_follow_up_completion_fields
        check (
            (status = 'SCHEDULED' and completed_at is null and result_note is null)
            or (status = 'COMPLETED' and completed_at is not null and btrim(result_note) <> '')
            or (status = 'CANCELLED' and completed_at is null and btrim(result_note) <> '')
        );

create unique index uq_follow_up_task_one_scheduled_per_case
    on follow_up_task (demo_session_id, demo_run_id, case_id)
    where status = 'SCHEDULED';

comment on constraint ck_follow_up_completion_fields on follow_up_task
    is '완료·취소 상태와 서버 완료시각·결과기록의 일관성을 강제';
