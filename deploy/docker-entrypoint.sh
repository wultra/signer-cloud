#!/usr/bin/env bash
set -euo pipefail

liquibase --headless=true --log-level=INFO --changeLogFile="$LB_HOME/db/changelog/db.changelog-master.xml" \
  --username="${DB_USERNAME:-}" \
  --password="${DB_PASSWORD:-}" \
  --url="${DB_URL}" \
  update

java "${JAVA_OPTS:-}" -jar signer-cloud-server.war
