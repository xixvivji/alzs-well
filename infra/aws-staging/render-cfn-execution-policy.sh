#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! $1 =~ ^[0-9]{12}$ ]]; then
  echo "사용법: $0 <12자리 AWS 계정 ID>" >&2
  exit 64
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
account_id="$1"

jq --arg account_id "$account_id" '
  walk(
    if type == "string" then
      gsub("__AWS_ACCOUNT_ID__"; $account_id)
    else
      .
    end
  )
  | if ([.. | strings | select(contains("__AWS_ACCOUNT_ID__"))] | length) == 0 then
      .
    else
      error("AWS 계정 ID 자리표시자가 남아 있습니다.")
    end
' "$script_dir/cfn-execution-policy.json"
