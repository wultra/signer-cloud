# Migration from 0.9.2 to 0.10.0

This guide contains instructions for migration from Signer Cloud Server version `0.9.2` to version `0.10.0`.


## REST API Changes

In order to have consistent fields naming `signerId` field was renamed to `externalSignerId`. Following endpoints were affected:
- Response body in `GET /signers/{externalSignerId}`
- Request body in `POST /signers`
- Parameter in `POST /documents`