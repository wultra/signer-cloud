#!/usr/bin/env bash
set -euo pipefail

if [ "${LQ_ENABLED}" = true ]; then
  liquibase --headless=true --log-level=INFO --changeLogFile="$LB_HOME/db/changelog/db.changelog-master.xml" \
    --username="${SIGNER_CLOUD_DATASOURCE_USERNAME:-}" \
    --password="${SIGNER_CLOUD_DATASOURCE_PASSWORD:-}" \
    --url="${SIGNER_CLOUD_DATASOURCE_URL}" \
    update
fi

exec java ${JAVA_OPTS:-} -cp "${APP_PATH}:${EXTLIB_PATH}/*" org.springframework.boot.loader.launch.WarLauncher
