#!/usr/bin/env bash
set -euo pipefail
base="${1:-http://localhost:8080}"
command -v jq >/dev/null || { echo 'Install jq to run the HTTP demo.' >&2; exit 1; }
token=''
body=''
call() {
  local method="$1" path="$2" expected="$3" data="${4:-}" response code
  local args=(-sS --max-time 15 -X "$method" -H 'Content-Type: application/json')
  if [[ -n "$token" ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$data" ]]; then args+=(--data "$data"); fi
  response=$(curl "${args[@]}" -w $'\n%{http_code}' "$base$path")
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [[ "$code" == "$expected" ]] || { echo "$method $path expected $expected, got $code" >&2; exit 1; }
  printf '%s %s %s\n' "$code" "$method" "$path"
}
suffix="$(date +%s)-$RANDOM"
credentials="{\"email\":\"demo-$suffix@example.com\",\"password\":\"DemoPassword42!\"}"
call GET /actuator/health/readiness 200
call POST /api/v1/auth/register 201 "$credentials"
call POST /api/v1/auth/token 200 "$credentials"
token=$(jq -r '.accessToken' <<< "$body")
owner_token="$token"
call POST /api/v1/projects 201 '{"name":"Ship a reliable Java API"}'
project=$(jq -r '.id' <<< "$body")
call POST "/api/v1/projects/$project/tasks" 201 '{"title":"Review the architecture"}'
task=$(jq -r '.id' <<< "$body")
path="/api/v1/projects/$project/tasks/$task"
outsider="{\"email\":\"outsider-$suffix@example.com\",\"password\":\"DemoPassword42!\"}"
call POST /api/v1/auth/register 201 "$outsider"
call POST /api/v1/auth/token 200 "$outsider"
token=$(jq -r '.accessToken' <<< "$body")
call GET "$path" 404
token="$owner_token"
call PATCH "$path" 422 '{"status":"DONE","expectedVersion":0}'
call PATCH "$path" 200 '{"status":"IN_PROGRESS","expectedVersion":0}'
call PATCH "$path" 409 '{"title":"Stale edit","expectedVersion":0}'
call PATCH "$path" 200 '{"status":"DONE","expectedVersion":1}'
call GET "$path/events" 200
jq -e '.items | map(.toStatus) == ["TODO","IN_PROGRESS","DONE"]' <<< "$body" >/dev/null
call DELETE "/api/v1/projects/$project" 204
echo 'Workflow passed: authentication, ownership, transitions, conflicts, audit, and project cleanup.'
