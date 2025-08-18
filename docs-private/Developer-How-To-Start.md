# Developer - How to Start Guide


## Signer Cloud Server


### Standalone Run

- Use IntelliJ Idea run configuration at `../.run/SignerCloudServerApplication.run.xml`
- Open [http://localhost:8090/actuator/health](http://localhost:8090/actuator/health) and you should get `{"status":"UP"}`

<!-- begin box info -->
The application runs on port `8090` by default to avoid conflicts with other apps in the infrastructure (for example, PowerAuth).
<!-- end -->


### Swagger

[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)


### Database

We support:
- PostgreSQL
- H2 for test purposes

For local development you can use docker image
```shell
docker run --name postgres-liquibase -e POSTGRES_USER=signer_cloud_server -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=signer_cloud_server -p 5432:5432 -d postgres:16
```

Don't forget to run the Liquibase script (see the section below); it is not applied automatically.


#### Liquibase

Database changes are driven by Liquibase.

This is an example how to invoke Liquibase.
Important and fixed parameter is `changelog-file`.
Others (like URL, username, password) depend on your environment.

To list all undeployed changesets run this `status` command. 

```shell
liquibase --changelog-file=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --url=jdbc:postgresql://localhost:5432/signer_cloud_server --username=signer_cloud_server status
```

To apply the changesets run this `update` command.

```shell
liquibase --changelog-file=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --url=jdbc:postgresql://localhost:5432/signer_cloud_server --username=signer_cloud_server update
```

To generate SQL script run this command.


#### PostgreSQL

```shell
liquibase --changeLogFile=./docs/db/changelog/changesets/signer-cloud-server/db.changelog-module.xml --output-file=./docs/sql/postgresql/generated-postgresql-script.sql updateSQL --url=offline:postgresql
```


## PowerAuth

The PowerAuth instance checks whether the signer is active (can be used for certificate creation).
For local setup see the [PowerAuth documentation](https://github.com/wultra/powerauth-server).


## EJBCA

The EJBCA instance is used for certificate management — creating new certificates and keeping existing ones linked to a specific `user` and `externalSignerId`.

There is a docker image available ([here](https://hub.docker.com/r/keyfactor/ejbca-ce)), however for local development we use an instance created in our cloud infrastructure.
It is available at `https://smoke-ejbca-dev.wultra.app/ejbca/`.


### Enroll Certificate

The EJBCA provides a REST API. For creating a new certificate call:

```shell
curl --location 'https://smoke-ejbca-dev.wultra.app/ejbca/ejbca-rest-api/v1/certificate/pkcs10enroll' \
     --cert client.crt \
     --key client.key \
     --json '{
        "end_entity_profile_name": "UserEndEntityProfile",
        "certificate_authority_name": "IssuingCA",
        "certificate_request": "MIICfDCCAWQCAQAwNzE...",
        "certificate_profile_name": "UserCertificateProfile",
        "accountBindingId": "userId",
        "username": "externalSignerId"
     }'
```

The instance uses mTLS authentication, so you need to provide a client certificate and key. See config properties with prefix `signer-cloud.server.ejbca.rest-client-configuration`
for keystore configuration.

<!-- begin box warning -->
Remove `-----BEGIN CERTIFICATE REQUEST-----` and `-----END CERTIFICATE REQUEST-----` from the `certificate_request` value.
If included, it will return `{"error_code":400,"error_message":"Invalid certificate request"}`.
<!-- end -->