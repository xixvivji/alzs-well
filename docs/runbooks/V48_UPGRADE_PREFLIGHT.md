# V48 이상 업그레이드 전 직원 접근권 정리

V40~V47에서는 서로 다른 업무 목적의 scope를 하나의 grant에 넣을 수 있었습니다. V48은 목적·scope 불변조건을 fail-closed로 적용하므로 이러한 기존 행이 있으면 migration이 `23514`로 중단됩니다.

## 절차

1. 반드시 운영 DB 백업과 복구 확인을 먼저 수행합니다.
2. V48 적용 전에 migrator의 읽기 전용 세션에서 다음 검사를 실행합니다.

   ```bash
   psql "$MIGRATOR_DATABASE_URL" --single-transaction --file backend/scripts/v48-upgrade-preflight.sql
   ```

3. 결과가 없으면 V48 이상으로 업그레이드할 수 있습니다.
4. 결과가 있으면 각 grant를 현재 직원 접근권 철회 API로 철회합니다. 자동으로 목적을 추정하거나 scope를 삭제하지 않습니다.
5. V49 배포 후 필요한 목적별 grant를 각각 새 멱등키로 재발급합니다.
6. 검사를 다시 실행해 결과가 없는 것을 확인한 후 Flyway를 실행합니다.

감사이력을 보존하기 위해 `staff_access_grant`를 직접 삭제하거나 기존 grant의 목적·scope를 직접 수정하면 안 됩니다. 긴급 DB 조치가 필요한 경우에도 변경승인 번호와 대상 grant 목록을 별도 운영 감사기록에 남깁니다.
