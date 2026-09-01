alter table synthetic_fixture_generation_run
    drop constraint ck_fixture_generation_profile;

alter table synthetic_fixture_generation_run
    add constraint ck_fixture_generation_profile
        check(profile in ('SMOKE','DEMO','PUBLIC','LOAD','DEV'));

comment on table synthetic_fixture_generation_run is
    'SMOKE·DEMO·PUBLIC·LOAD·DEV 결정론적 합성 데이터 생성 실행과 검증 건수';
