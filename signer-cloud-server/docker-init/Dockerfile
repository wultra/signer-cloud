FROM liquibase:4.33.0-alpine

LABEL maintainer="Wultra <hello@wultra.com>"

COPY ./docs/db/changelog/changesets/signer-cloud-server /liquibase/changelog
COPY signer-cloud-server/docker-init/docker-entrypoint.sh /

USER liquibase
ENTRYPOINT ["/docker-entrypoint.sh"]

