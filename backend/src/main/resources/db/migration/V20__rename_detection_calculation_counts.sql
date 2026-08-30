alter table baseline_calculation_job
    rename column baselines_written to baselines_evaluated;

alter table baseline_calculation_job
    rename column signals_written to signals_evaluated;

comment on column baseline_calculation_job.baselines_evaluated is '현재 계산 작업에서 검증한 기준선 snapshot 수';
comment on column baseline_calculation_job.signals_evaluated is '현재 계산 작업에서 검증한 변화신호 snapshot 수';
