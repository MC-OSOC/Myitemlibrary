"""Fails unless CoDatabaseTest ran on SQLite and on the MySQL-compatible server (none skipped).
Usage: python3 check-db-tests.py <surefire-report.xml> <server-name>"""
import sys
import xml.etree.ElementTree as ET

report, server = sys.argv[1], sys.argv[2]
cases = ET.parse(report).getroot().findall("testcase")
for c in cases:
    if c.find("skipped") is not None:
        state = "SKIPPED"
    elif c.find("failure") is not None or c.find("error") is not None:
        state = "FAILED"
    else:
        state = "passed"
    print(f"{state:8} {c.get('name')}")
ran = [c.get("name") for c in cases if c.find("skipped") is None]
# Parameterized tests run once per backend: [1] = SQLite, [2] = the MySQL-compatible server,
# [3] = the same server without SSL (plugin default: allow-public-key-retrieval).
need = ["(String)[1]", "(String)[2]", "(String)[3]", "concurrentMigrationOfSharedMysqlDatabase",
        "firstLoginWithoutSslUsesPublicKeyRetrieval"]
missing = [n for n in need if not any(n in x for x in ran)]
if missing or len(ran) != len(cases):
    sys.exit(f"database tests did not run on SQLite and {server} (missing {missing}, {len(cases) - len(ran)} skipped)")
print(f"{len(ran)} database tests ran on SQLite and {server}")
