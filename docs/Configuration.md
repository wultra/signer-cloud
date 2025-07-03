# Configuration

The system consists of two main components - CloudSigner Server and Certification Authority. As the Certification Authority, Keyfactor EJBCA is currently supported.

For time defining values, following syntax is supported: 1s 1h 1d 1y.

## CloudSigner Server

| Property                          | Default | Note                                                                                                                                                   |
|:----------------------------------|:--------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|
| document.waiting.timeout          | 3600    | Maximal timeout threshold between document upload and signing in seconds.                                                                              |
| document.rejected.retentionPeriod | _empty_ | Retention period for rejected documents in days. Empty value means no retention period is used, value `0` means documents will be deleted immediately. |
| document.signed.retentionPeriod   | _empty_ | Retention period for signed documents in days. Empty value means no retention period is used, value `0` means documents will be deleted immediately.   |


## Certification Authority

TODO