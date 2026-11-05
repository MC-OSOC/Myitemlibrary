#!/usr/bin/env bash
# Regression test for "Public Key Retrieval is not allowed" (MySQL 8 + caching_sha2_password + use-ssl: false).
#
# The error only appears on the FIRST full authentication of a user, when MySQL has no cached password hash
# for it (fresh server, after a restart or FLUSH PRIVILEGES). So this script restarts the MySQL container
# first and never logs in as the plugin's user before the server does.
#
# Usage: mysql-pubkey-regression.sh <server-jar> <plugin-jar> <expect: fail|ok> [label]
#   fail: the plugin must NOT connect and the log must show the error (reproduces the bug, e.g. with 1.4)
#   ok:   the plugin must connect and the API must work (the fix)
# Environment: MYSQL_CONTAINER (default db), a MySQL 8.4 server on 127.0.0.1:3306, db/user mil, password milpass,
#              root password root.
set -euo pipefail

SERVER_JAR=$(realpath "$1")
PLUGIN_JAR=$(realpath "$2")
EXPECT=$3
LABEL=${4:-$(basename "$PLUGIN_JAR")}
REPO_ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DB=${MYSQL_CONTAINER:-db}
WORK=$(mktemp -d)
API="http://127.0.0.1:1558"
ERROR_TEXT="Public Key Retrieval is not allowed"

echo "=== $LABEL with MySQL use-ssl: false, expecting: $EXPECT ==="

# Fresh authentication state: restart MySQL (clears the caching_sha2_password cache).
docker restart "$DB" > /dev/null
for i in $(seq 1 60); do
  # Ping as root over the local socket: this must not log in as 'mil' (that would cache its hash).
  docker exec "$DB" mysqladmin ping -uroot -proot --silent > /dev/null 2>&1 && break
  sleep 2
done
echo "MySQL restarted: $(docker exec "$DB" mysql -uroot -proot -N -e 'SELECT VERSION()' 2>/dev/null)"

cd "$WORK"
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nwhite-list=false\n' > server.properties
mkdir -p plugins/MyItemLibrary
cp "$PLUGIN_JAR" plugins/
sed -e 's/c-api-enable: false/c-api-enable: true/' \
    -e 's/^c-database-mode: .*/c-database-mode: MySQL/' \
    -e 's/  host: "localhost"/  host: "127.0.0.1"/' \
    -e 's/  username: ""/  username: "mil"/' \
    -e 's/  password: ""/  password: "milpass"/' \
    -e 's/  database: ""/  database: "mil"/' \
    "$REPO_ROOT/src/main/resources/config.yml" > plugins/MyItemLibrary/config.yml
grep -q '^  use-ssl: false' plugins/MyItemLibrary/config.yml || { echo "config does not have use-ssl: false"; exit 1; }

mkfifo console
java -Xmx1500M -jar "$SERVER_JAR" --nogui < console > server.log 2>&1 &
PID=$!
exec 3> console
stop() { echo stop >&3 2>/dev/null || true; timeout 120 tail --pid="$PID" -f /dev/null 2>/dev/null || kill -9 "$PID" 2>/dev/null || true; }
trap stop EXIT
timeout 600 bash -c 'until grep -q "Done (" server.log; do sleep 2; kill -0 '"$PID"' 2>/dev/null || exit 1; done' \
  || { echo "server did not start"; tail -50 server.log; exit 1; }
sleep 2

echo "--- MyItemLibrary log lines ---"
grep -E "MyItemLibrary|Public Key|Hikari" server.log || true
echo "-------------------------------"

if [ "$EXPECT" = "fail" ]; then
  if grep -q "$ERROR_TEXT" server.log && grep -q "Failed to setup database" server.log; then
    echo "REPRODUCED: $LABEL cannot connect: $(grep -m1 -o "$ERROR_TEXT.*" server.log)"
    exit 0
  fi
  echo "NOT REPRODUCED: expected '$ERROR_TEXT' with $LABEL"; exit 1
fi

FAILS=0
if grep -q "$ERROR_TEXT" server.log; then echo "FAIL: '$ERROR_TEXT' in log"; FAILS=$((FAILS + 1)); fi
grep -q "MyItemLibrary-MySQL - Start completed" server.log && echo "PASS: connected (MyItemLibrary-MySQL - Start completed)" \
  || { echo "FAIL: pool did not start"; FAILS=$((FAILS + 1)); }
KEY=$(grep -E '^\s*c-api-key:' plugins/MyItemLibrary/config.yml | sed -E "s/^[^:]*:[[:space:]]*['\"]?([^'\"]*)['\"]?[[:space:]]*$/\1/")
CODE=$(curl -s -o body -w '%{http_code}' -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"item_name":"x","item_display":"STONE","description":"d","player":"Steve","command":"say x","used":1}' "$API/add-item" || true)
[ "$CODE" = "200" ] && echo "PASS: /add-item works on MySQL without SSL" || { echo "FAIL: /add-item returned $CODE: $(cat body 2>/dev/null)"; FAILS=$((FAILS + 1)); }
[ "$FAILS" -eq 0 ] && echo "FIXED: $LABEL connects to MySQL 8 with use-ssl: false" || exit 1
