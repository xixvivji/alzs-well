alter table baseline_calculation_job
    add column idempotency_key_hash varchar(80),
    add column request_hash varchar(80);

update baseline_calculation_job
   set idempotency_key_hash = 'sha256:legacy:' || calculation_id,
       request_hash = 'sha256:legacy:' || calculation_id
 where idempotency_key_hash is null or request_hash is null;

alter table baseline_calculation_job
    alter column idempotency_key_hash set not null,
    alter column request_hash set not null;

create unique index uq_baseline_calculation_idempotency
    on baseline_calculation_job (customer_id, idempotency_key_hash);

comment on column baseline_calculation_job.idempotency_key_hash is '원문을 저장하지 않는 계산 명령 멱등키 SHA-256';
comment on column baseline_calculation_job.request_hash is '정규화된 계산 명령의 SHA-256';
