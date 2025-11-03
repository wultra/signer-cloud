# Database Setup

Choose the manual depending on used database:

- [Database Setup for PostgreSQL](./Database-Setup-PostgreSQL.md)

## Connection Pooling

All our applications use HikariCP (the default for Spring Boot). As a result, we manage connections efficiently. By default, we create 10 connections to the underlying PostgreSQL database.

In case you change the configuration of the image to a lower number of idle connections than the maximum number of connections, some of the connections may be released. If the application becomes inactive and connections are idle, HikariCP will keep these connections open for approximately 10 minutes (600 000 milliseconds, with maximum variation of 30s - HikariCP checks for idle state every 30s).


## Database Changes

The docker image automatically applies the database changes using Liquibase, executing this command at the container startup:

```shell
liquibase --headless=true --log-level=INFO --changeLogFile="$LB_HOME/db/changelog/db.changelog-master.xml" \
  --username="${SIGNER_CLOUD_DATASOURCE_USERNAME:-}" \
  --password="${SIGNER_CLOUD_DATASOURCE_PASSWORD:-}" \
  --url="${SIGNER_CLOUD_DATASOURCE_URL}" \
  update
```


### Generate SQL Script

It is preferred to manage the database changes with Liquibase, but if you need to generate SQL scripts for your database manually, you may execute the following command:


#### PostgreSQL

```shell
liquibase --changeLogFile=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --output-file=./docs/sql/postgresql/generated-postgresql-script.sql updateSQL --url=offline:postgresql
```


#### Oracle

```shell
liquibase --changeLogFile=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --output-file=./docs/sql/oracle/generated-oracle-script.sql updateSQL --url=offline:oracle
```
