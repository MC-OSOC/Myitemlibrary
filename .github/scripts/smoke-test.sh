#!/usr/bin/env bash
# Starts a real server with the plugin, checks it enables, and exercises the REST API and the GUI.
#
# Usage: smoke-test.sh <server-jar> <plugin-jar>
# Environment (all optional):
#   OLD_PLUGIN_JAR  released jar to start with first (migration test: add items, stop, swap in the new jar)
#   DB_MODE         sqlite (default) or mysql (expects MySQL on 127.0.0.1:3306, db/user "mil", password "milpass")
#   MC_VERSION      Minecraft version, used for the bot
#   BOT_DIR         directory with claim-bot.js and an installed mineflayer; the GUI claim test runs only if
#                   mineflayer supports MC_VERSION, otherwise it prints SKIPPED
set -euo pipefail

SERVER_JAR=$(realpath "$1")
PLUGIN_JAR=$(realpath "$2")
REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DB_MODE=${DB_MODE:-sqlite}
MC_VERSION=${MC_VERSION:-unknown}
OLD_PLUGIN_JAR=${OLD_PLUGIN_JAR:+$(realpath "$OLD_PLUGIN_JAR")}
BOT_DIR=${BOT_DIR:+$(realpath "$BOT_DIR")}
WORK=$(mktemp -d)
API="http://127.0.0.1:1558"
FAILS=0
SUMMARY="$WORK/summary.txt"
SERVER_PID=

pass() { echo "PASS $*"; echo "PASS $*" >> "$SUMMARY"; }
fail() { echo "FAIL $*"; echo "FAIL $*" >> "$SUMMARY"; FAILS=$((FAILS + 1)); }
skip() { echo "SKIPPED: $*"; echo "SKIPPED: $*" >> "$SUMMARY"; }

cd "$WORK"
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-protection=0\nspawn-monsters=false\n' > server.properties
mkdir -p plugins/MyItemLibrary
# Default config with the API enabled and an EMPTY key: the plugin must generate one.
# The rate limit is raised because this script sends far more than 100 requests per minute.
sed -e 's/c-api-enable: false/c-api-enable: true/' -e 's/max-requests-per-minute: 100/max-requests-per-minute: 100000/' \
  "$REPO_ROOT/src/main/resources/config.yml" > plugins/MyItemLibrary/config.yml
if [ "$DB_MODE" = "mysql" ]; then
  sed -i -e 's/^c-database-mode: .*/c-database-mode: MySQL/' \
         -e 's/  host: "localhost"/  host: "127.0.0.1"/' \
         -e 's/  username: ""/  username: "mil"/' \
         -e 's/  password: ""/  password: "milpass"/' \
         -e 's/  database: ""/  database: "mil"/' \
         -e 's/  use-ssl: false/  use-ssl: true/' plugins/MyItemLibrary/config.yml
fi
POOL_NAME=$([ "$DB_MODE" = "mysql" ] && echo "MyItemLibrary-MySQL" || echo "MyItemLibrary-SQLite")

start_server() { # <log-file>
  local log=$1
  rm -f console && mkfifo console
  java -Xmx1500M -jar "$SERVER_JAR" --nogui < console > "$log" 2>&1 &
  SERVER_PID=$!
  exec 3> console
  if ! timeout 900 bash -c 'until grep -q "Done (" '"$log"'; do sleep 2; kill -0 '"$SERVER_PID"' 2>/dev/null || exit 1; done'; then
    echo "Server did not start"; tail -100 "$log"; exit 1
  fi
}

stop_server() {
  [ -n "$SERVER_PID" ] || return 0
  echo stop >&3 2>/dev/null || true
  timeout 120 tail --pid="$SERVER_PID" -f /dev/null 2>/dev/null || kill -9 "$SERVER_PID" 2>/dev/null || true
  exec 3>&- || true
  SERVER_PID=
}

cleanup() {
  stop_server
  for log in server-old.log server.log; do
    [ -f "$log" ] || continue
    echo "----- $log (MyItemLibrary / Hikari / SLF4J / errors) -----"
    grep -E "MyItemLibrary|cakedek|Hikari|SLF4J|ERROR|Exception" "$log" || true
  done
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ] && [ -f "$SUMMARY" ]; then
    { echo "### ${PLATFORM:-server} $MC_VERSION ($DB_MODE)"; echo '```'; cat "$SUMMARY"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
  fi
}
trap cleanup EXIT

# Prints the HTTP status, or curl-exit-N if the connection failed (e.g. server closed it early).
# Response body goes to $WORK/body, response headers to $WORK/headers.
code() { local c; c=$(curl -s -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' "$@") || c="curl-exit-$?"; echo "$c"; }
expect() { local want=$1 desc=$2; shift 2; local got; got=$(code "$@"); [ "$got" = "$want" ] && pass "$desc ($got)" || { fail "$desc: expected $want got $got: $(head -c 200 "$WORK/body")"; }; }
json() { python3 -c "import json,sys; d=json.load(open('$WORK/body')); print($1)"; }
replay_header() { grep -qi '^X-Idempotent-Replay: *true' "$WORK/headers"; }
read_key() { grep -E '^\s*c-api-key:' plugins/MyItemLibrary/config.yml | sed -E "s/^[^:]*:[[:space:]]*['\"]?([^'\"]*)['\"]?[[:space:]]*$/\1/"; }
item() { # <name> <player> <command> [extra json fields]
  echo "{\"item_name\":\"$1\",\"item_display\":\"DIAMOND_SWORD\",\"description\":\"smoke test\",\"player\":\"$2\",\"command\":\"$3\",\"used\":1${4:+,$4}}"
}
offline_uuid() { python3 -c "import hashlib,uuid,sys; print(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+sys.argv[1]).encode()).digest(), version=3))" "$1"; }

BOT=BotTester
BOT_UUID=$(offline_uuid "$BOT")

# ---------------------------------------------------------------- phase 1: released jar (migration source)
if [ -n "${OLD_PLUGIN_JAR:-}" ]; then
  cp "$OLD_PLUGIN_JAR" plugins/
  start_server server-old.log
  KEY=$(read_key)
  H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")
  expect 200 "[old jar] add item for Steve"     "${H[@]}" -d "$(item '&aMigrated Sword' Steve 'say migrated <player>')" "$API/add-item"
  expect 200 "[old jar] add item for $BOT"      "${H[@]}" -d "$(item 'Migrated Gift' "$BOT" 'say migrated-<player>')" "$API/add-item"
  stop_server
  rm -f plugins/"$(basename "$OLD_PLUGIN_JAR")"
  MIGRATED=2
else
  MIGRATED=0
fi

# ---------------------------------------------------------------- phase 2: this build
cp "$PLUGIN_JAR" plugins/
start_server server.log

grep -q "\[MyItemLibrary\] Running on" server.log && pass "plugin enabled: $(grep -o 'Running on .*' server.log | head -1)" || fail "plugin did not log 'Running on'"
grep -q "API server started" server.log && pass "API server started" || fail "API server not started"
if grep -E "Error occurred while enabling MyItemLibrary|Could not load 'plugins/my-item-library" server.log; then fail "plugin failed to load/enable"; fi
if grep -B2 -A10 -E "Exception" server.log | grep -q "cakedek"; then fail "exception from plugin code in log"; else pass "no exceptions from plugin code"; fi
# Hikari logs through slf4j: seeing its log line proves the server's slf4j is used (none is bundled).
grep -q "$POOL_NAME - Start completed" server.log && pass "HikariCP pool started and logs via the server's slf4j" || fail "no '$POOL_NAME - Start completed' in log"
if grep -qE "SLF4J\(W\)|SLF4J: (No SLF4J providers|Failed to load class)" server.log; then fail "slf4j warning in log: $(grep -m1 SLF4J server.log)"; else pass "no slf4j warnings"; fi
if [ "$MIGRATED" -gt 0 ]; then
  grep -q "Database migration: added column co_list_item.player_uuid" server.log && pass "migration added player_uuid" || fail "no migration log line for player_uuid"
  grep -q "Database migration: added column co_list_item.order_id" server.log && pass "migration added order_id" || fail "no migration log line for order_id"
fi

KEY=$(read_key)
[ "${#KEY}" -ge 16 ] && pass "API key available (${#KEY} chars)" || fail "API key not generated: '$KEY'"
H=(-H "X-API-Key: $KEY" -H "Content-Type: application/json")

# --- auth and basic endpoints
expect 401 "no API key rejected"           "$API/items"
expect 401 "empty API key rejected"        -H "X-API-Key;" "$API/items"
expect 401 "wrong API key rejected"        -H "X-API-Key: wrong-key-1234567890" "$API/items"
expect 401 "/claims without API key"       "$API/claims"

if [ "$MIGRATED" -gt 0 ]; then
  expect 200 "items from 1.3 database still listed" "${H[@]}" "$API/items"
  [ "$(json 'len(d)')" = "$MIGRATED" ] && pass "all $MIGRATED migrated items present" || fail "expected $MIGRATED migrated items: $(head -c 300 "$WORK/body")"
  [ "$(json 'all(i.get("playerUuid") is None for i in d)')" = "True" ] && pass "migrated items are unbound (no UUID yet)" || fail "migrated items unexpectedly bound"
  expect 200 "migrated item listable by player" "${H[@]}" "$API/items/Steve"
  grep -q "Migrated Sword" "$WORK/body" && pass "migrated item content intact" || fail "migrated item content missing"
fi

expect 200 "add item (no order_id)"        "${H[@]}" -d "$(item '&aTest Sword' Steve 'say <player> got a sword')" "$API/add-item"
replay_header && fail "header X-Idempotent-Replay on a normal add" || pass "no replay header on a normal add"
expect 200 "get items of player"           "${H[@]}" "$API/items/Steve"
grep -q "Test Sword" "$WORK/body" && pass "added item returned" || fail "added item missing from response"
expect 200 "player lookup is case-insensitive" "${H[@]}" "$API/items/steve"
expect 400 "invalid player name rejected"  "${H[@]}" -d "$(item x '@a' 'say x')" "$API/add-item"
expect 400 "missing field rejected"        "${H[@]}" -d '{"item_name":"x"}' "$API/add-item"
expect 400 "invalid JSON rejected"         "${H[@]}" -d '{not json' "$API/add-item"
expect 405 "wrong method rejected"         "${H[@]}" "$API/add-item"

# --- order_id idempotency
expect 200 "add item with order_id"        "${H[@]}" -d "$(item 'Order Item' Steve 'say order' '"order_id":"shop-1001"')" "$API/add-item"
replay_header && fail "first use of an order_id marked as replay" || pass "first use of order_id is not a replay"
expect 200 "same order_id again"           "${H[@]}" -d "$(item 'Order Item' Steve 'say order' '"order_id":"shop-1001"')" "$API/add-item"
replay_header && pass "replay has X-Idempotent-Replay: true" || fail "replay header missing: $(cat "$WORK/headers")"
grep -q "already processed" "$WORK/body" && pass "replay message: $(cat "$WORK/body")" || fail "replay message unclear: $(cat "$WORK/body")"
expect 200 "list Steve's items"            "${H[@]}" "$API/items/Steve"
[ "$(json 'sum(1 for i in d if i.get("orderId")=="shop-1001")')" = "1" ] && pass "replayed order added nothing (1 item with order_id)" || fail "order shop-1001 item count wrong: $(head -c 300 "$WORK/body")"
expect 400 "invalid order_id (space, !) rejected" "${H[@]}" -d "$(item x Steve 'say x' '"order_id":"bad id!"')" "$API/add-item"
expect 400 "order_id longer than 64 rejected"     "${H[@]}" -d "$(item x Steve 'say x' "\"order_id\":\"$(printf 'a%.0s' {1..65})\"")" "$API/add-item"
expect 400 "non-string order_id rejected"         "${H[@]}" -d "$(item x Steve 'say x' '"order_id":123')" "$API/add-item"
expect 400 "invalid order_id on /add-item-all"    "${H[@]}" -d "$(item x Steve 'say x' '"order_id":"a/b"')" "$API/add-item-all"
expect 400 "invalid order_id on /add-item-online" "${H[@]}" -d "$(item x Steve 'say x' '"order_id":""')" "$API/add-item-online"
expect 200 "64-char order_id with . _ : - accepted" "${H[@]}" -d "$(item 'Long Order' Steve 'say x' "\"order_id\":\"Ab.9_:-$(printf 'x%.0s' {1..57})\"")" "$API/add-item"

# Concurrent duplicates: only one request may add items (the primary key decides, not a prior SELECT).
for i in $(seq 1 10); do
  curl -s -o /dev/null -D "$WORK/race-$i" "${H[@]}" -d "$(item 'Race Item' Steve 'say race' '"order_id":"race-1"')" "$API/add-item" &
done
wait
REPLAYS=$(grep -li '^X-Idempotent-Replay: *true' "$WORK"/race-* | wc -l)
OKS=$(grep -l '^HTTP/1.1 200' "$WORK"/race-* | wc -l)
[ "$OKS" = "10" ] && [ "$REPLAYS" = "9" ] && pass "10 concurrent same-order requests: 10x 200, 9 replays" || fail "concurrent order: $OKS x 200, $REPLAYS replays"
expect 200 "list after concurrent requests" "${H[@]}" "$API/items/Steve"
[ "$(json 'sum(1 for i in d if i.get("orderId")=="race-1")')" = "1" ] && pass "concurrent duplicates added exactly one item" || fail "race-1 item count: $(json 'sum(1 for i in d if i.get("orderId")=="race-1")')"

# --- player_uuid
ALEX_UUID=0f0e0d0c-0b0a-4908-8706-050403020100
expect 200 "add item with player_uuid (upper-case)" "${H[@]}" -d "$(item 'Uuid Item' Alex 'say uuid' "\"player_uuid\":\"${ALEX_UUID^^}\"")" "$API/add-item"
expect 200 "items by UUID"                 "${H[@]}" "$API/items/$ALEX_UUID"
[ "$(json 'd[0]["playerUuid"]')" = "$ALEX_UUID" ] && pass "player_uuid stored lower-case" || fail "playerUuid: $(head -c 300 "$WORK/body")"
expect 400 "invalid player_uuid rejected"  "${H[@]}" -d "$(item x Alex 'say x' '"player_uuid":"not-a-uuid"')" "$API/add-item"
expect 400 "invalid name or UUID in /items/{player}" "${H[@]}" "$API/items/not-a-uuid-or-name!"

# --- add-item-online / add-item-all with order_id
expect 200 "add-item-online with order_id" "${H[@]}" -d "$(item 'Online Gift' x 'say online' '"order_id":"online-1"')" "$API/add-item-online"
grep -q "0 online players" "$WORK/body" && pass "nobody online: $(cat "$WORK/body")" || fail "add-item-online: $(cat "$WORK/body")"
expect 200 "add-item-online replay"        "${H[@]}" -d "$(item 'Online Gift' x 'say online' '"order_id":"online-1"')" "$API/add-item-online"
replay_header && pass "add-item-online replay header" || fail "add-item-online replay header missing"
# Owners so far: Steve (unbound), Alex (bound) and, after a migration, the bot (unbound).
ALL_EXPECTED=$([ "$MIGRATED" -gt 0 ] && echo 3 || echo 2)
expect 200 "add-item-all with order_id"    "${H[@]}" -d "$(item 'All Gift' x 'say all-<player>' '"order_id":"all-1"')" "$API/add-item-all"
grep -q "for $ALL_EXPECTED players" "$WORK/body" && pass "add-item-all reached $ALL_EXPECTED owners" || fail "add-item-all: $(cat "$WORK/body")"
expect 200 "add-item-all replay"           "${H[@]}" -d "$(item 'All Gift' x 'say all-<player>' '"order_id":"all-1"')" "$API/add-item-all"
replay_header && pass "add-item-all replay header" || fail "add-item-all replay header missing"
expect 200 "items by UUID after add-item-all" "${H[@]}" "$API/items/$ALEX_UUID"
[ "$(json 'sorted(i["itemName"] for i in d if i["playerUuid"]=="'$ALEX_UUID'")')" = "['All Gift', 'Uuid Item']" ] && pass "add-item-all carried Alex's UUID" || fail "Alex items: $(head -c 300 "$WORK/body")"

# --- /claims validation
expect 200 "/claims empty"                 "${H[@]}" "$API/claims"
[ "$(cat "$WORK/body")" = "[]" ] && pass "/claims returns []" || fail "/claims not empty: $(head -c 200 "$WORK/body")"
expect 200 "/claims with all filters"      "${H[@]}" "$API/claims?player=Steve&uuid=$ALEX_UUID&order_id=x.y&since=0&limit=1000"
expect 400 "/claims limit=0"               "${H[@]}" "$API/claims?limit=0"
expect 400 "/claims limit=1001"            "${H[@]}" "$API/claims?limit=1001"
expect 400 "/claims limit=abc"             "${H[@]}" "$API/claims?limit=abc"
expect 400 "/claims since=-1"              "${H[@]}" "$API/claims?since=-1"
expect 400 "/claims since empty"           "${H[@]}" "$API/claims?since="
expect 400 "/claims bad uuid"              "${H[@]}" "$API/claims?uuid=123"
expect 400 "/claims bad player"            "${H[@]}" "$API/claims?player=%40a"
expect 400 "/claims bad order_id"          "${H[@]}" "$API/claims?order_id=a%20b"
expect 400 "/claims unknown parameter"     "${H[@]}" "$API/claims?foo=1"
expect 400 "/claims repeated parameter"    "${H[@]}" "$API/claims?limit=1&limit=2"
expect 400 "/claims malformed encoding"    "${H[@]}" "$API/claims?player=%zz"
expect 405 "/claims POST"                  "${H[@]}" -X POST "$API/claims"
expect 404 "/claims/extra path"            "${H[@]}" "$API/claims/1"

# --- size limits
head -c 2000000 /dev/zero | tr '\0' 'a' > "$WORK/big"
expect 200 "count items before oversized requests" "${H[@]}" "$API/items"
BEFORE=$(json 'len(d)')
expect 413 "oversized body rejected"       "${H[@]}" --data-binary @"$WORK/big" "$API/add-item"
# For chunked uploads the server may close the connection before the client finished sending,
# so a connection reset also counts as "rejected".
got=$(code "${H[@]}" -H "Transfer-Encoding: chunked" --data-binary @"$WORK/big" "$API/add-item")
case "$got" in 413|curl-exit-52|curl-exit-55|curl-exit-56) pass "oversized chunked body rejected ($got)";; *) fail "oversized chunked body: got $got";; esac
expect 200 "list items after oversized requests" "${H[@]}" "$API/items"
[ "$(json 'len(d)')" = "$BEFORE" ] && pass "oversized requests added nothing" || fail "expected $BEFORE items, found $(json 'len(d)')"

# --- get / delete by id
expect 200 "add throwaway item"            "${H[@]}" -d "$(item 'Delete Me' Nobody 'say x')" "$API/add-item"
expect 200 "find throwaway item"           "${H[@]}" "$API/items/Nobody"
DEL_ID=$(json 'd[0]["id"]')
expect 200 "get item by id"                "${H[@]}" "$API/item/$DEL_ID"
expect 200 "delete item"                   "${H[@]}" -X DELETE "$API/item/$DEL_ID"
expect 404 "deleted item gone"             "${H[@]}" "$API/item/$DEL_ID"

# ---------------------------------------------------------------- GUI claim with a bot player
BOT_SUPPORTED=no
if [ -n "${BOT_DIR:-}" ] && (cd "$BOT_DIR" && node -e "process.exit(require('mineflayer').testedVersions.includes(process.argv[1]) ? 0 : 1)" "$MC_VERSION"); then
  BOT_SUPPORTED=yes
fi
if [ "$BOT_SUPPORTED" = "yes" ]; then
  # Before the bot joins: one item by name only (bound on join) and one already bound to its UUID under an
  # old name (rename case: the join must update the name). Plus the migrated item and the add-item-all gift.
  expect 200 "bot item by name with order_id"  "${H[@]}" -d "$(item 'Bot Reward' "$BOT" 'say claimed-<player>' '"order_id":"bot-order-1"')" "$API/add-item"
  expect 200 "bot item by UUID under old name" "${H[@]}" -d "$(item 'Renamed Reward' OldBotName 'say renamed-<player>' "\"order_id\":\"bot-order-2\",\"player_uuid\":\"$BOT_UUID\"")" "$API/add-item"
  BOT_ITEMS=2; [ "$MIGRATED" -gt 0 ] && BOT_ITEMS=4   # + migrated item + add-item-all gift
  BOUND_BY_NAME=$((BOT_ITEMS - 1))

  if (cd "$BOT_DIR" && timeout 240 node claim-bot.js 127.0.0.1 25565 "$BOT" "$MC_VERSION") > "$WORK/bot.log" 2>&1; then
    pass "bot joined and finished"
  else
    fail "bot failed: $(tail -5 "$WORK/bot.log")"
  fi
  cat "$WORK/bot.log"
  RESULT=$(grep -o 'RESULT {.*}' "$WORK/bot.log" | sed 's/^RESULT //' || true)
  r() { python3 -c "import json,sys; print(json.loads(sys.argv[1])['$1'])" "${RESULT:-null}" 2>/dev/null || echo "?"; }
  [ "$(r initial)" = "$BOT_ITEMS" ] && pass "bot saw its $BOT_ITEMS items in /my-library (bound by UUID + renamed)" || fail "bot saw $(r initial) items, expected $BOT_ITEMS"
  [ "$(r claimed)" = "$BOT_ITEMS" ] && pass "bot claimed $BOT_ITEMS items by clicking" || fail "bot claimed $(r claimed), expected $BOT_ITEMS"
  [ "$(r final)" = "0" ] && pass "re-opened library is empty: nothing can be claimed twice" || fail "library still shows $(r final) items after claiming"
  sleep 2

  grep -q "Bound $BOUND_BY_NAME item(s) to $BOT ($BOT_UUID)" server.log && pass "join bound $BOUND_BY_NAME name-only items to $BOT_UUID" || fail "no 'Bound $BOUND_BY_NAME item(s) to $BOT' in log"
  for cmd in claimed renamed $([ "$MIGRATED" -gt 0 ] && echo "migrated all"); do
    n=$(grep -c "\[Server\] $cmd-$BOT\$" server.log || true)
    [ "$n" = "1" ] && pass "reward command '$cmd-<player>' ran exactly once" || fail "reward command '$cmd-$BOT' ran $n times"
  done

  expect 200 "/claims?order_id=bot-order-1"  "${H[@]}" "$API/claims?order_id=bot-order-1"
  [ "$(json 'len(d)')" = "1" ] && pass "exactly one claim for bot-order-1" || fail "claims for bot-order-1: $(head -c 300 "$WORK/body")"
  [ "$(json 'd[0]["player_uuid"]+" "+d[0]["player_name"]+" "+d[0]["command"]+" "+d[0]["order_id"]')" = "$BOT_UUID $BOT say claimed-$BOT bot-order-1" ] \
    && pass "claim log row has UUID, name, replaced command and order_id" || fail "claim row: $(head -c 300 "$WORK/body")"
  expect 200 "/claims?uuid=<bot uuid>"       "${H[@]}" "$API/claims?uuid=$BOT_UUID"
  [ "$(json 'len(d)')" = "$BOT_ITEMS" ] && pass "$BOT_ITEMS claims for the bot UUID" || fail "claims by uuid: $(json 'len(d)')"
  [ "$(json 'all(d[i]["claimed_at"] >= d[i+1]["claimed_at"] for i in range(len(d)-1))')" = "True" ] && pass "/claims newest first" || fail "/claims not sorted"
  expect 200 "/claims?player=bottester&limit=1" "${H[@]}" "$API/claims?player=bottester&limit=1"
  [ "$(json 'len(d)')" = "1" ] && pass "player filter is case-insensitive and limit works" || fail "player/limit filter: $(json 'len(d)')"
  expect 200 "/claims?since=<future>"        "${H[@]}" "$API/claims?since=$(( $(date +%s) * 1000 + 3600000 ))"
  [ "$(json 'len(d)')" = "0" ] && pass "since filter" || fail "since filter returned $(json 'len(d)')"
  expect 200 "bot items after claiming"      "${H[@]}" "$API/items/$BOT_UUID"
  [ "$(json 'len(d)')" = "$BOT_ITEMS" ] && [ "$(json 'all(i["playerUuid"]=="'$BOT_UUID'" and i["player"]=="'$BOT'" and not i["enable"] for i in d)')" = "True" ] \
    && pass "all bot items bound to its UUID, renamed to $BOT and claimed" || fail "bot items: $(head -c 400 "$WORK/body")"
  expect 404 "old name has no items any more (renamed)" "${H[@]}" "$API/items/OldBotName"
else
  skip "bot not supported on $MC_VERSION (GUI claim test not run)"
fi

# ---------------------------------------------------------------- reload and shutdown
echo "my-library-reload" >&3
sleep 5
expect 200 "API works after /my-library-reload" "${H[@]}" "$API/items"
[ "$(grep -c "$POOL_NAME - Start completed" server.log)" -ge 2 ] && pass "reload opened a new pool" || fail "reload did not open a new pool"
expect 200 "/claims works after reload"    "${H[@]}" "$API/claims"

stop_server
grep -q "$POOL_NAME - Shutdown completed" server.log && pass "pool closed cleanly on shutdown" || fail "no '$POOL_NAME - Shutdown completed' in log"
if grep -B2 -A10 -E "Exception" server.log | grep -q "cakedek"; then fail "exception from plugin code in log"; else pass "no exceptions from plugin code (whole run)"; fi

echo
if [ "$FAILS" -eq 0 ]; then echo "SMOKE TEST PASSED"; else echo "SMOKE TEST FAILED ($FAILS)"; exit 1; fi
