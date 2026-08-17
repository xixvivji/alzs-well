-- case_note는 일반적인 수정·삭제를 계속 거부한다.
-- 단, 익명 데모 세션 전체를 폐기하는 트랜잭션만 세션 범위의 cascade delete를 허용한다.
create or replace function reject_case_note_mutation()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'DELETE'
       and current_setting('app.demo_session_discard', true) = old.demo_session_id::text then
        return old;
    end if;
    raise exception 'case_note is append-only';
end;
$$;
