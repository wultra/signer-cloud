# Integration

## Certificate Enrollment

![Certificate Enrollment Sequence](./img/Certificate_Enrollment_Sequence.png)

Steps

1. Generate CSR on mobile device.
2. Sign CSR using PowerAuth SDK on activated mobile device.
3. Send result to the Orchestrator Service (bank’s service managing business logic).
4. Pass signed CSR from Orchestrator Service to the CloudSigner via REST API. After the signature verification, CSR is processed, certificate is generated and result is immediately returned.

## Document Signing

![Document Signing Sequence](./img/Document_Signing_Sequence.png)

Steps

1. Present document to the user (it has to be loaded from banks storage) a let him select which document should be signed. Pass the document (or document ID) from Mobile App to the Orchestrator Service
2. Pass the document from the Orchestrator Service to the CloudSigner using REST API method Upload Document. CloudSigner will store file and returns its hash in the response.
3. Sign document hash using PowerAuth SDK on activated mobile device.
4. Send result to the Orchestrator Service.
5. Pass signed hash from Orchestrator Service to the CloudSigner via REST API. After the signature verification, document is completed and result is immediately returned.

## Application States

Chapter descibes application states.

### Signer

States of the entity (user/device) that can sign documents. Some states can be directly controlled via API.

| State   | Description                                                                                                                                            |
|:--------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|
| ACTIVE  | Signer can sign documents. Certificate renewal is active. State can be changed to BLOCKED.                                                             |
| BLOCKED | Signer cannot sign documents, certificate renewal is suspended but certificate stays active until its expiration. State can be changed back to ACTIVE. |
| REMOVED | Signer cannot sign documents, certificate renewal is suspended but certificate stays active until its expiration.                                      |
| REVOKED | Signer cannot sign documents, certificate renewal is suspended and certificate is immediately revoked.                                                 |

### Document

States used during document lifecycle.

| State    | Description                                                                  |
|:---------|:-----------------------------------------------------------------------------|
| WAITING  | Document is uploaded and is waiting for signature. Has configurable timeout. |
| REJECTED | Document was rejected by signer. Has configurable retention period.          |
| SIGNED   | Document is signed. Has configurable retention period.                       |