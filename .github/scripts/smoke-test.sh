#!/usr/bin/env bash
# Starts a real server with the plugin, checks it enables, and exercises the REST API.
# Usage: smoke-test.sh <server-jar> <plugin-jar>
set -euo pipefail

SERVER_JAR=$(realpath "$1")
PLUGIN_JAR=$(realpath "$2")
REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
WORK=$(mktemp -d)
API="http://127.0.0.1:1558"
FAILS=0

pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; FAILS=$((FAILS + 1)); }

cd "$WORK"
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-protection=0\n' > server.properties
mkdir -p plugins/MyItemLibrary
cp "$PLUGIN_JAR" plugins/
# Default config with the API enabled and an EMPTY key: the plugin must generate one.
sed 's/c-api-enable: false/c-api-enable: true/' "$REPO_ROOT/src/main/resources/config.yml" > plugins/MyItemLibrary/config.yml

mkfifo console
java -Xmx1500M -jar "$SERVER_JAR" --nogui < console > server.log 2>&1 &
SERVER_PID=$!
exec 3> console

cleanup() {
  echo stop >&3 2>/dev/null || true
  timeout 60 tail --pid="$SERVER_PID" -f /dev/null 2>/dev/null || kill -9 "$SERVER_PID" 2>/dev/null || true
  echo "----- server log (MyItemLibrary / errors) -----"
  grep -E "MyItemLibrary|cakedek|ERROR|Exception" server.log || true
}
trap cleanup EXIT

if ! timeout 900 bash -c 'until grep -q "Done (" server.log; do sleep 2; kill -0 '"$SERVER_PID"' 2>/dev/null || exit 1; done'; then
  echo "Server did not start"; tail -100 server.log; exit 1
fi

grep -q "\[MyItemLibrary\] Running on" server.log && pass "plugin enabled: $(grep -o 'Running on .*' server.log | head -1)" || fail "plugin did not log 'Running on'"
grep -q "API server started" server.log && pass "API server started" || fail "API server not started"
if grep -E "Error occurred while enabling MyItemLibrary|Could not load 'plugins/my-item-library" server.log; then fail "plugin failed to load/enable"; fi
if grep -B2 -A10 -E "Exception" server.log | grep -q "cakedek"; then fail "exception from plugin code in log"; else pass "no exceptions from plugin code"; fi

KEY=$(grep -E '^\s*c-api-key:' plugins/MyItemLibrary/config.yml | sed -E "s/^[^:]*:[[:space:]]*['\"]?([^'\"]*)['\"]?[[:space:]]*$/\1/")
[ "${#KEY}" -ge 16 ] && pass "API key auto-generated (${#KEY} chars)" || fail "API key not generated: '$KEY'"

# Prints the HTTP status, or curl-exit-N if the connection failed (e.g. server closed it early).
code() { local c; c=$(curl -s -o /tmp/body -w '%{http_code}' "$@") || c="curl-exit-$?"; echo "$c"; }
expect() { local want=$1 desc=$2; shift 2; local got; got=$(code "$@"); [ "$got" = "$want" ] && pass "$desc ($got)" || { fail "$desc: expected $want got $got: $(head -c 200 /tmp/body)"; }; }

ITEM='{"item_name":"&aTest Sword","item_display":"DIAMOND_SWORD","description":"smoke test","player":"Steve","command":"say <player> got a sword","used":1}'

expect 401 "no API key rejected"           "$API/items"
expect 401 "empty API key rejected"        -H "X-API-Key;" "$API/items"
expect 401 "wrong API key rejected"        -H "X-API-Key: wrong-key-1234567890" "$API/items"
expect 200 "add item"                      -H "X-API-Key: $KEY" -H "Content-Type: application/json" -d "$ITEM" "$API/add-item"
expect 200 "get items of player"           -H "X-API-Key: $KEY" "$API/items/Steve"
grep -q "Test Sword" /tmp/body && pass "added item returned" || fail "added item missing from response"
expect 200 "get all items"                 -H "X-API-Key: $KEY" "$API/items"
expect 200 "get item by id"                -H "X-API-Key: $KEY" "$API/item/1"
expect 400 "invalid player name rejected"  -H "X-API-Key: $KEY" -d "${ITEM/Steve/@a}" "$API/add-item"
expect 400 "missing field rejected"        -H "X-API-Key: $KEY" -d '{"item_name":"x"}' "$API/add-item"
expect 400 "invalid JSON rejected"         -H "X-API-Key: $KEY" -d '{not json' "$API/add-item"
expect 405 "wrong method rejected"         -H "X-API-Key: $KEY" "$API/add-item"
head -c 2000000 /dev/zero | tr '\0' 'a' > /tmp/big
expect 413 "oversized body rejected"       -H "X-API-Key: $KEY" --data-binary @/tmp/big "$API/add-item"
# For chunked uploads the server may close the connection before the client finished sending,
# so a connection reset also counts as "rejected".
got=$(code -H "X-API-Key: $KEY" -H "Transfer-Encoding: chunked" --data-binary @/tmp/big "$API/add-item")
case "$got" in 413|curl-exit-52|curl-exit-55|curl-exit-56) pass "oversized chunked body rejected ($got)";; *) fail "oversized chunked body: got $got";; esac
expect 200 "list items after oversized requests" -H "X-API-Key: $KEY" "$API/items"
COUNT=$(python3 -c "import json; print(len(json.load(open('/tmp/body'))))")
[ "$COUNT" = "1" ] && pass "oversized requests added nothing" || fail "expected 1 item, found $COUNT"
expect 200 "delete item"                   -H "X-API-Key: $KEY" -X DELETE "$API/item/1"
expect 404 "deleted item gone"             -H "X-API-Key: $KEY" "$API/item/1"

# Reload command from console must keep the plugin working.
echo "my-library-reload" >&3
sleep 5
expect 200 "API works after /my-library-reload" -H "X-API-Key: $KEY" "$API/items"

echo
if [ "$FAILS" -eq 0 ]; then echo "SMOKE TEST PASSED"; else echo "SMOKE TEST FAILED ($FAILS)"; exit 1; fi
