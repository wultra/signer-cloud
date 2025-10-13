# Configuration Properties

You can set the following configurations to the PowerAuth Cloud components:

| Environment Variable                                             | Default Value                                             | Description            |
|------------------------------------------------------------------|-----------------------------------------------------------|------------------------|
| SIGNER_CLOUD_DATASOURCE_URL                                      | `jdbc:postgresql://host.docker.internal:5432/signercloud` |                        |
| SIGNER_CLOUD_DATASOURCE_USERNAME                                 | `$USERNAME$`                                              |                        |
| SIGNER_CLOUD_DATASOURCE_PASSWORD                                 | `$PASSWORD$`                                              |                        |
| SIGNER_CLOUD_POWERAUTH_URL                                       | `http://localhost:8080/powerauth-java-server/rest`        | PowerAuth REST API URL |
| SIGNER_CLOUD_POWERAUTH_CLIENT_TOKEN                              |                                                           |                        |
| SIGNER_CLOUD_POWERAUTH_CLIENT_SECRET                             |                                                           |                        |
| SIGNER_CLOUD_EJBCA_URL                                           |                                                           | EJBCA REST API URL     |
| SIGNER_CLOUD_EJBCA_KEYSTORE_PASSWORD                             |                                                           |                        |
| SIGNER_CLOUD_EJBCA_KEY_ALIAS                                     |                                                           |                        |
| SIGNER_CLOUD_EJBCA_KEY_PASSWORD                                  |                                                           |                        |
| SIGNER_CLOUD_EJBCA_KEYSTORE_BASE64                               |                                                           |                        |
| SIGNER_CLOUD_EJBCA_CERTIFICATE_PROFILE_NAME                      | `UserCertificateProfile`                                  |                        |
| SIGNER_CLOUD_EJBCA_END_ENTITY_PROFILE_NAME                       | `UserEndEntityProfile`                                    |                        |
| SIGNER_CLOUD_EJBCA_CERTIFICATE_AUTHORITY_NAME                    | `IssuingCA`                                               |                        |
| SIGNER_CLOUD_PADES_TSA_URL                                       |                                                           |                        | 
| SIGNER_CLOUD_PADES_SIGNATURE_LEVEL                               | `PADES_B_B`                                               |                        |
| SIGNER_CLOUD_PADES_HASH_ALGORITHM                                | `SHA256`                                                  |                        |
| SIGNER_CLOUD_PADES_SIGNATURE_ALGORITHM                           | `ECDSA_SHA256`                                            |                        |
| SIGNER_CLOUD_OAUTH2_ISSUER_URI                                   |                                                           |                        |
| SIGNER_CLOUD_OAUTH2_AUDIENCES                                    |                                                           |                        |
| SIGNER_CLOUD_DOCUMENT_CLEANUP_CRON                               | `2 1 0 * * *`                                             |                        |
| SIGNER_CLOUD_DOCUMENT_WAITING_RETENTION_PERIOD                   |                                                           |                        |
| SIGNER_CLOUD_DOCUMENT_WAITING_TIMEOUT                            | `3600s`                                                   |                        |
| SIGNER_CLOUD_DOCUMENT_REJECTED_RETENTION_PERIOD                  |                                                           |                        |
| SIGNER_CLOUD_DOCUMENT_SIGNED_RETENTION_PERIOD                    |                                                           |                        |
| SIGNER_CLOUD_SIGNER_EXPIRATION_JOB_CRON                          | `3 2 0 * * *`                                             |                        |
| SIGNER_CLOUD_SIGNER_EXPIRATION_JOB_LIMIT                         | `1000`                                                    |                        |
| SIGNER_CLOUD_SIGNER_RENEWAL_JOB_CRON                             | `0 */15 * * * *`                                          |                        |
| SIGNER_CLOUD_SIGNER_RENEWAL_JOB_LIMIT                            | `25`                                                      |                        |
| SIGNER_CLOUD_SIGNER_RENEWAL_THRESHOLD                            | `14d`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_EXPIRED_URL                                |                                                           |                        |
| SIGNER_CLOUD_CALLBACK_EXPIRED_ENABLED                            | `false`                                                   |                        |
| SIGNER_CLOUD_CALLBACK_EXPIRED_MAX_ATTEMPTS                       | `1`                                                       |                        |
| SIGNER_CLOUD_CALLBACK_EXPIRED_RETENTION_PERIOD                   | `30d`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_RENEWED_URL                                |                                                           |                        |
| SIGNER_CLOUD_CALLBACK_RENEWED_ENABLED                            | `false`                                                   |                        |
| SIGNER_CLOUD_CALLBACK_RENEWED_MAX_ATTEMPTS                       | `1`                                                       |                        |
| SIGNER_CLOUD_CALLBACK_RENEWED_RETENTION_PERIOD                   | `30d`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_DISPATCH_PENDING_CALLBACK_EVENTS_JOB_CRON  | `0 */1 * * * *`                                           |                        |
| SIGNER_CLOUD_CALLBACK_DISPATCH_PENDING_CALLBACK_EVENTS_JOB_LIMIT | `100`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_CLEANUP_CALLBACK_EVENTS_JOB_CRON           | `0 */5 * * * *`                                           |                        |
| SIGNER_CLOUD_CALLBACK_RERUN_STALE_CALLBACK_EVENTS_JOB_CRON       | `0 */5 * * * *`                                           |                        |
| SIGNER_CLOUD_CALLBACK_FORCE_RERUN_PERIOD                         |                                                           |                        |
| SIGNER_CLOUD_CALLBACK_FAILURE_THRESHOLD                          | `200`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_FAILURE_RESET_TIMEOUT                      | `60s`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_PROXY_ENABLED                         | `false`                                                   |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_PROXY_HOST                            | `127.0.0.1`                                               |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_PROXY_PORT                            | `8080`                                                    |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_PROXY_USERNAME                        |                                                           |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_PROXY_PASSWORD                        |                                                           |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_CONNECTION_TIMEOUT                    | `5s`                                                      |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_RESPONSE_TIMEOUT                      | `60s`                                                     |                        |
| SIGNER_CLOUD_CALLBACK_HTTP_MAX_IDLE_TIME                         | `200s`                                                    |                        |
| SIGNER_CLOUD_CALLBACK_THREAD_POOL_CORE_SIZE                      | `1`                                                       |                        |
| SIGNER_CLOUD_CALLBACK_THREAD_POOL_MAX_SIZE                       | `2`                                                       |                        |
| SIGNER_CLOUD_CALLBACK_THREAD_POOL_QUEUE_CAPACITY                 | `1000`                                                    |                        |

Please note that under normal circumstanced you are supposed to define only:

```
SIGNER_CLOUD_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/signercloud
SIGNER_CLOUD_DATASOURCE_USERNAME=$USERNAME$
SIGNER_CLOUD_DATASOURCE_PASSWORD=$PASSWORD$
SIGNER_CLOUD_POWERAUTH_URL
SIGNER_CLOUD_EJBCA_URL
SIGNER_CLOUD_EJBCA_KEYSTORE_BASE64
SIGNER_CLOUD_EJBCA_KEYSTORE_PASSWORD
SIGNER_CLOUD_EJBCA_KEY_ALIAS
SIGNER_CLOUD_EJBCA_KEY_PASSWORD
SIGNER_CLOUD_OAUTH2_AUDIENCES
SIGNER_CLOUD_OAUTH2_ISSUER_URI
```

Consult support if you have some specific request.