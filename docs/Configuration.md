# Configuration

The system consists of two main components - CloudSigner Server and Certification Authority. As the Certification Authority, Keyfactor EJBCA is currently supported.

For time-defining values, the following syntax is supported: 1s 1h 1d 1y.


## CloudSigner Server

| Property                                                                | Default        | Note                                                                                                                                           |
|:------------------------------------------------------------------------|:---------------|:-----------------------------------------------------------------------------------------------------------------------------------------------|
| signer-cloud.server.document.waiting.timeout                            | `3600s`        | Maximal timeout threshold when is possible sign the document after upload.                                                                     |
| signer-cloud.server.document.waiting.retentionPeriod                    | _empty_        | Retention period for waiting documents. Empty value means no retention period is used, value `0` means documents will be deleted immediately.  |
| signer-cloud.server.document.rejected.retentionPeriod                   | _empty_        | Retention period for rejected documents. Empty value means no retention period is used, value `0` means documents will be deleted immediately. |
| signer-cloud.server.document.signed.retentionPeriod                     | _empty_        | Retention period for signed documents. Empty value means no retention period is used, value `0` means documents will be deleted immediately.   |
| signer-cloud.server.document.cleanup.cron                               | `2 1 0 * * *`  | Cron expression scheduling the job handling retention of the documents. Use `-` if you want to disable that.                                   |
| signer-cloud.server.signer.expiration.job.cron                          | `3 2 0 * * *`  | Cron expression scheduling the job handling retention of the signers. Use `-` if you want to disable that.                                     |
| signer-cloud.server.signer.expiration.job.limit                         | 1000           | Limit how many entries are processed in a single run.                                                                                          |
| signer-cloud.server.signer.expiration.callback-enabled                  | `true`         | Enable/disable the callback to the external system when the signer expires.                                                                    |
| signer-cloud.server.callback.dispatch-pending-callback-events.job.cron  | `0 1 * * * *`  | Cron expression scheduling the job handling pending callback events. Use `-` if you want to disable that.                                      |
| signer-cloud.server.callback.dispatch-pending-callback-events.job.limit | 100            | Limit how many entries are processed in a single run.                                                                                          |
| signer-cloud.server.callback.cleanup-callback-events.job.cron           | `0 11 * * * *` | Cron expression scheduling the job handling expired callback events. Use `-` if you want to disable that.                                      |
| signer-cloud.server.callback.rerun-stale-callback-events.job.cron       | `0 23 * * * *` | Cron expression scheduling the job rerun staled callback events. Use `-` if you want to disable that.                                          |
| signer-cloud.server.callback.client-cache.refresh-after-write           | `5m`           | Duration after which the cached REST client should be refreshed (if `callback.timestamp_last_updated` has changed).                            |
| signer-cloud.server.callback.http-proxy-enabled                         | `false`        | Enable/disable HTTP proxy for callback requests.                                                                                               |
| signer-cloud.server.callback.http-proxy-host                            | `127.0.0.1`    | HTTP proxy host.                                                                                                                               |
| signer-cloud.server.callback.http-proxy-port                            | `8080`         | HTTP proxy port.                                                                                                                               |
| signer-cloud.server.callback.http-proxy-username                        | _empty_        | HTTP proxy username.                                                                                                                           |
| signer-cloud.server.callback.http-proxy-password                        | _empty_        | HTTP proxy password.                                                                                                                           |
| signer-cloud.server.callback.http-connection-timeout                    | `5s`           | Timeout for establishing HTTP connection.                                                                                                      |
| signer-cloud.server.callback.http-response-timeout                      | `60s`          | Timeout for receiving HTTP response.                                                                                                           |
| signer-cloud.server.callback.http-max-idle-time                         | `200s`         | Maximum time HTTP connection can remain idle.                                                                                                  |
| signer-cloud.server.security.auth.type                                  | `OAUTH2`       | Authentication type.                                                                                                                           |
| spring.security.oauth2.resource-server.jwt.issuer-uri                   | _empty_        | URL of the authorization server.                                                                                                               |
| spring.security.oauth2.resource-server.jwt.audiences                    | _empty_        | A comma-separated list of allowed `aud` JWT claim values to be validated.                                                                      |


## Certification Authority

TODO