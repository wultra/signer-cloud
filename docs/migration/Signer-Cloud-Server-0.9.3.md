# Migration from 0.9.2 to 0.9.3

This guide contains instructions for migration from Signer Cloud Server version `0.9.2` to version `0.9.3`.


## REST API Changes

In order to have consistent fields naming `signerId` field was renamed to `externalSignerId`. Following endpoints were affected:
- Response body in `POST /signers/{externalSignerId}`
- Request body in `POST /signers`
- Parameter in `POST /documents`