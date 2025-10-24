# Configuration

The system consists of two main components - Signer Cloud Server and Certification Authority. As the Certification Authority, Keyfactor EJBCA is currently supported.

For time-defining values, the following syntax is supported: 1s 1h 1d 1y.


## Signer Cloud Server

| Property                                                                | Default            | Note                                                                                                                                                                                                                            |
|:------------------------------------------------------------------------|:-------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| signer-cloud.server.document.waiting.timeout                            | `3600s`            | Maximal timeout threshold when is possible sign the document after upload.                                                                                                                                                      |
| signer-cloud.server.document.waiting.retention-period                   | _empty_            | Retention period for waiting documents. Empty value means no retention period is used, value `0` means documents will be deleted immediately.                                                                                   |
| signer-cloud.server.document.rejected.retention-period                  | _empty_            | Retention period for rejected documents. Empty value means no retention period is used, value `0` means documents will be deleted immediately.                                                                                  |
| signer-cloud.server.document.signed.retention-period                    | _empty_            | Retention period for signed documents. Empty value means no retention period is used, value `0` means documents will be deleted immediately.                                                                                    |
| signer-cloud.server.document.cleanup.cron                               | `2 1 0 * * *`      | Cron expression scheduling the job handling retention of the documents. Use `-` if you want to disable that.                                                                                                                    |
| signer-cloud.server.pades.signature-algorithm                           | `ECDSA_SHA256`     | The algorithm used to compute document digest and its signing. Any value of [SignatureAlgorithm](https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/apidocs/eu/europa/esig/dss/enumerations/SignatureAlgorithm.html). | 
| signer-cloud.server.pades.signature-level                               | `PADES_B_B`        | Default signature level used for the document.                                                                                                                                                                                  |
| signer-cloud.server.pades.tsa-url                                       | _empty_            | URL of an endpoint providing a timestamp according to **RFC 3161**. It must be set if the `PADES_B_T` signature level is requested when signing the document.                                                                   |
| signer-cloud.server.signer.expiration.job.cron                          | `3 2 0 * * *`      | Cron expression scheduling the job handling retention of the signers. Use `-` if you want to disable that.                                                                                                                      |
| signer-cloud.server.signer.expiration.job.limit                         | 1000               | Limit how many entries are processed in a single run.                                                                                                                                                                           |
| signer-cloud.server.signer.renewal.job.cron                             | `0 */15 * * * *`   | Cron expression scheduling the job handling renewal of the signers. Use `-` if you want to disable that.                                                                                                                        |
| signer-cloud.server.signer.renewal.job.limit                            | 25                 | Limit how many entries are processed in a single run.                                                                                                                                                                           |
| signer-cloud.server.signer.renewal.threshold                            | `14d`              | Threshold when the signer is considered about to renew before expiration.                                                                                                                                                       |
| signer-cloud.server.callback.dispatch-pending-callback-events.job.cron  | `0 */1 * * * *`    | Cron expression scheduling the job handling pending callback events. Use `-` if you want to disable that.                                                                                                                       |
| signer-cloud.server.callback.dispatch-pending-callback-events.job.limit | 100                | Limit how many entries are processed in a single run.                                                                                                                                                                           |
| signer-cloud.server.callback.cleanup-callback-events.job.cron           | `0 */5 * * * *`    | Cron expression scheduling the job handling expired callback events. Use `-` if you want to disable that.                                                                                                                       |
| signer-cloud.server.callback.rerun-stale-callback-events.job.cron       | `0 */5 * * * *`    | Cron expression scheduling the job rerun staled callback events. Use `-` if you want to disable that.                                                                                                                           |
| signer-cloud.server.callback.expired.url                                | _empty_            | Callback URL.                                                                                                                                                                                                                   |
| signer-cloud.server.callback.expired.enabled                            | `false`            | Enable/disable the callback to the external system when the signer expires.                                                                                                                                                     |
| signer-cloud.server.callback.expired.max-attempts                       | 1                  | Maximum number of callback attempts.                                                                                                                                                                                            |
| signer-cloud.server.callback.expired.retention-period                   | `30d`              | Retention period of the callback event.                                                                                                                                                                                         |
| signer-cloud.server.callback.expired.initial-backoff                    | `2s`               | Initial backoff between callback attempts.                                                                                                                                                                                      |
| signer-cloud.server.callback.renewed.url                                | _empty_            | Callback URL.                                                                                                                                                                                                                   |
| signer-cloud.server.callback.renewed.enabled                            | `false`            | Enable/disable the callback to the external system when the signer renews.                                                                                                                                                      |
| signer-cloud.server.callback.renewed.max-attempts                       | 1                  | Maximum number of callback attempts.                                                                                                                                                                                            |
| signer-cloud.server.callback.renewed.retention-period                   | `30d`              | Retention period of the callback event.                                                                                                                                                                                         |
| signer-cloud.server.callback.renewed.initial-backoff                    | `2s`               | Initial backoff between callback attempts.                                                                                                                                                                                      |
| signer-cloud.server.callback.max-backoff                                | `32s`              | Maximum possible backoff period between successive attempts.                                                                                                                                                                    |
| signer-cloud.server.callback.backoff-multiplier                         | 1.5                | Multiplier used to calculate the backoff period.                                                                                                                                                                                |
| signer-cloud.server.callback.force-rerun-period                         | _empty_            | Period after which a Callback Event is considered stale and should be dispatched again. The default value is computed as a function of configured HTTP timeouts.                                                                |
| signer-cloud.server.callback.failure-threshold                          | 200                | Number of allowed Callback Events failures in a row. When the threshold is reached, no other events with the same Callback configuration will be posted.`-1` means that the threshold is disabled.                              |
| signer-cloud.server.callback.failure-reset-timeout                      | 60s                | Period after which a Callback Event will be dispatched even though the failure threshold is reached.                                                                                                                            |
| signer-cloud.server.callback.http-proxy-enabled                         | `false`            | Enable/disable HTTP proxy for callback requests.                                                                                                                                                                                |
| signer-cloud.server.callback.http-proxy-host                            | `127.0.0.1`        | HTTP proxy host.                                                                                                                                                                                                                |
| signer-cloud.server.callback.http-proxy-port                            | `8080`             | HTTP proxy port.                                                                                                                                                                                                                |
| signer-cloud.server.callback.http-proxy-username                        | _empty_            | HTTP proxy username.                                                                                                                                                                                                            |
| signer-cloud.server.callback.http-proxy-password                        | _empty_            | HTTP proxy password.                                                                                                                                                                                                            |
| signer-cloud.server.callback.http-connection-timeout                    | `5s`               | Timeout for establishing HTTP connection.                                                                                                                                                                                       |
| signer-cloud.server.callback.http-response-timeout                      | `60s`              | Timeout for receiving HTTP response.                                                                                                                                                                                            |
| signer-cloud.server.callback.http-max-idle-time                         | `200s`             | Maximum time HTTP connection can remain idle.                                                                                                                                                                                   |
| signer-cloud.server.callback.thread-pool-core-size                      | 1                  | Number of core threads in the thread pool.                                                                                                                                                                                      |
| signer-cloud.server.callback.thread-pool-max-size                       | 2                  | Maximum number of threads in the thread pool.                                                                                                                                                                                   |
| signer-cloud.server.callback.thread-pool-queue-capacity                 | 1000               | Queue capacity of the thread pool.                                                                                                                                                                                              |
| signer-cloud.server.security.auth.type                                  | `OAUTH2`           | Authentication type.                                                                                                                                                                                                            |
| spring.security.oauth2.resource-server.jwt.issuer-uri                   | _empty_            | URL of the authorization server.                                                                                                                                                                                                |
| spring.security.oauth2.resource-server.jwt.audiences                    | _empty_            | A comma-separated list of allowed `aud` JWT claim values to be validated.                                                                                                                                                       |


## Certification Authority

### Create Management CA

EJBCA requires a Management CA, an internal root certification authority, to issue an mTLS user certificate for the super admin account (or other administrative accounts). This certificate is used to access the Admin GUI and REST API.

Since our deployment uses an EJBCA installation without a ManagementCA, one must be created manually via the wizard in the admin interface right after installation.

**Required steps**

1. [Create Crypto Tokens](#create-crypto-tokens) (eg `ManagementCACryptoToken`)
2. Open [Installation Wizard](https://localhost:8443/ejbca/adminweb/initpki.xhtml)
3. Select "Create a New CA" and click "Next"
4. Select "Use Existing Crypto Token" and select Crypto Token created in the first step
5. Use Following settings and click "Next":
``` bash
Signing Algorithm=SHA256withRSA
defaultKey=encryptKey
certSignKey=signKey
keyEncryptKey=- Default key
testKey=- Default key
CA Name=ManagementCA
Subject DN=CN=ManagementCA,O=Your Company Name,C=CZ
Validity (days)=10y
```
6. Create Super Administrator account with default settings, fill your custom Password and click "Next"
7. Review settings at Summary page and click "Install"
8. Click "Enroll" to create SuperAdmin end entity and download user certificate (SuperAdmin.p12 file)
<!-- begin box warning -->
If you click on “Download CA Certificate“ link first, page will disappear and SuperAdmin account will not be created. If it happens, you can create SuperAdmin account manually under “RA Web > Enroll > Make New Request”
<!-- end -->
9. Click to "Download CA Certificate".
<!-- begin box warning -->
If you don’t download CA cert now, you can do it later under CA Functions > CA Structure & CRLs
<!-- end -->

#### Enable access to the Admin GUI

**Required steps**

1. Add ManagementCA as trusted in your OS
2. Install SuperAdmin certificate, created in the previous step, into your OS

#### Enable access to the REST API

**Authentication**

The EJBCA Community Edition only supports authentication via an administrator user certificate. You can use the SuperAdmin account, which is used to access the GUI, or create a new user with the same roles. If you have a certificate keystore, you need to  [Configure the Docker Image](./Installation.md) properties.

More info about [Authentication](https://docs.keyfactor.com/ejbca/9.1.1/ejbca-rest-interface#id-(9.1.1)EJBCARESTInterface-Authenticationauthentication).

**Access to the specific endpoints**

The REST interface collects multiple API endpoints. Some are enabled by default; others are disabled. Check the settings under "System Configuration > Protocol Configuration," especially "REST Certificate Management," which must be "enabled."

More info about [Security for REST endpoint](https://docs.keyfactor.com/ejbca/9.1.1/ejbca-rest-interface#id-(9.1.1)EJBCARESTInterface-SecurityforRESTendpoint).

### Create PKI Hierarchy

Before creating a PKI hierarchy, we recommend reading [Tutorial - Create a PKI Hierarchy in EJBCA](https://docs.keyfactor.com/ejbca/9.1.1/tutorial-create-a-pki-hierarchy-in-ejbca).

#### Create Certificate Profiles

Read more about [Certificate Profiles Overview](https://docs.keyfactor.com/ejbca/9.1.1/certificate-profiles-overview).

1. Clone ROOTCA profile with custom name (eg. "RootCACertificateProfile")
2. Clone SUBCA profile with custom name (eg. "IssuingCACertificateProfile")
3. You can leave the settings as they are for now (the profile allows you to override them later)

#### Create Crypto Tokens

Read more about [Crypto Tokens Overview](https://docs.keyfactor.com/ejbca/9.1.1/crypto-tokens-overview).

1. Create two independent Crypto Tokens (eg. "RootCACryptoToken" and "IssuingCACryptoToken")
2. CA Functions > Crypto Tokens > Create new...
3. Fill Name, select Type to "SOFT", check "Auto-activation" and fill your custom authentication code (preferably generated and stored in a secure password manager)
4. Generate two key-pairs signKey and encryptKey, both with `ECDSA P-384 / secp384r1` key algorithm (use `RSA 4096` for ManagementCA).

#### Create CAs

Read more about [Certificate Authority Overview](https://docs.keyfactor.com/ejbca/9.1.1/certificate-authority-overview).

1. Create two independent CAs (e.g. "RootCA" and "IssuingCA")
2. Select Crypto Token from previous step and Signing Algorithm `SHA384withECDSA`  (use `SHA256withRSA` for ManagementCA).
3. Select keys from previous step
4. Include Subject DN (eg. "CN = RootCA, O = Your Company Name, C = CZ")