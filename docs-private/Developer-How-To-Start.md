# Developer - How to Start Guide

This guide explains how to start Signer Cloud for development.


## Signer Cloud Server

The Signer Cloud application is built using Maven and includes a single module `signer-cloud-server`.

Tools required for building and running the application:

| Tool   | Version  |
|--------|----------|
| Java   | > 17     |
| Maven  | > 3.6.3  |


### Standalone Run

- Use IntelliJ Idea run configuration at `../.run/SignerCloudServerApplication.run.xml`
- Open [http://localhost:8090/actuator/health](http://localhost:8090/actuator/health) and you should get `{"status":"UP"}`

<!-- begin box info -->
The application runs on port `8090` by default to avoid conflicts with other apps in the infrastructure (for example, PowerAuth).
<!-- end -->


### REST API


#### Swagger

[http://localhost:8090/swagger-ui.html](http://localhost:8090/swagger-ui.html)


#### Authentication

The Signer Cloud REST API is, by default, secured using Basic Authentication. The username is always `user`, and the password 
is a randomly generated UUID, which can be found in the logs. Here is an example of a log message:
`Using generated security password: 8c578094-8f0f-4caa-887d-76a0d6063eaf`


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

The PowerAuth instance checks the validity of signatures (for instance, CSR and signed document hashes).
For local setup see the [PowerAuth documentation](https://github.com/wultra/powerauth-server).


## EJBCA

The EJBCA instance is a Certificate Authority used for certificate management — creating new certificates and keeping existing ones linked to a specific `user` and `externalSignerId`.

There is a docker image available ([here](https://hub.docker.com/r/keyfactor/ejbca-ce)). Follow [Certification Authority](./../docs/Configuration.md#certification-authority) instructions to configure the instance.


### Enroll Certificate

The EJBCA provides a REST API. For creating a new certificate call:

```shell
curl --location "${SIGNER_CLOUD_EJBCA_URL}/v1/certificate/pkcs10enroll" \
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
For the `certificate_request` value, use either a single-line Base64 string or PEM format with
`-----BEGIN CERTIFICATE REQUEST-----` and `-----END CERTIFICATE REQUEST-----`, but without any whitespace and using `\n` for line breaks.

If the format is invalid, the response will be:
`{"error_code":400,"error_message":"Invalid certificate request"}`
<!-- end -->


### Revoke certificates

In EJBCA, all enrolled certificates are bound to a user and an externalSignerId. However, in the Community Edition, 
it is not possible to revoke all these certificates in a single REST API call (this functionality is available only in 
the Enterprise Edition).

All issued certificate metadata are stored in the Signer Cloud database table `sc_issued_certificate_metadata`. 
Each record is linked to a signer via the `signer_id` column.

To revoke all certificates issued to a specific `externalSignerId`, find all issued certificates in the mentioned table. 
Then, for each record, call this endpoint. The `CN%3DIssuingCA` value comes from the `issuer_dn` column, 
and `76D893C8C9BA218D3AEA5CC24411128D63D86F7C` comes from the `serial_number` column.

<!-- begin box warning -->
Since the `serial_number` is stored in decimal format in the database, but the EJBCA REST API expects a hexadecimal value, 
conversion is required.

The reason value is also required; in the example call, the value `UNSPECIFIED` is used.
<!-- end -->

```shell
curl -X PUT \
    --location "${SIGNER_CLOUD_EJBCA_URL}/v1/certificate/CN%3DIssuingCA/76D893C8C9BA218D3AEA5CC24411128D63D86F7C/revoke?reason=UNSPECIFIED" \
     --cert admin-cert.pem \
     --key admin-key.pem
```