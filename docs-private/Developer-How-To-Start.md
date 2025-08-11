# Developer - How to Start Guide


## Signer Cloud Server


### Standalone Run

- Use IntelliJ Idea run configuration at `../.run/SignerCloudServerApplication.run.xml`
- Open [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) and you should get `{"status":"UP"}`


### Database

Database changes are driven by Liquibase.

This is an example how to invoke Liquibase.
Important and fixed parameter is `changelog-file`.
Others (like URL, username, password) depend on your environment.

To list all undeployed changesets run this `status` command. 

```shell
liquibase --changelog-file=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --url=jdbc:postgresql://localhost:5432/signer-cloud-server --username=signer-cloud-server status
```

To apply the changesets run this `update` command.

```shell
liquibase --changelog-file=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --url=jdbc:postgresql://localhost:5432/signer-cloud-server --username=signer-cloud-server update
```

To generate SQL script run this command.


#### PostgreSQL

```shell
liquibase --changeLogFile=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --output-file=./docs/sql/postgresql/generated-postgresql-script.sql updateSQL --url=offline:postgresql
```


## EJBCA


### Enroll Certificate

```shell
curl -X 'POST' \
        'https://smoke-ejbca-dev.wultra.app/ejbca/ejbca-rest-api/v1/certificate/pkcs10enroll' \
        --cert client.crt \
        --key client.key \
        --json '{
        "end_entity_profile_name": "ExampleEEP",
        "certificate_authority_name": "ExampleCA",
        "certificate_request": "MIICh...V8shQ== OR -----BEGIN CERTIFICATE REQUEST-----\nMIICh...V8shQ==\n-----END CERTIFICATE REQUEST-----",
        "certificate_profile_name": "ENDUSER"
        }'
```
