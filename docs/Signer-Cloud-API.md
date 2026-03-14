# Signer Cloud API
<!-- template api -->

Signer Cloud Server provides a RESTful API that controls specific parts of the signing process. 

<!-- begin remove -->
- `POST` [/signers](#create-new-signer) - Create New Signer
- `PUT` [/signers/{externalSignerId}](#change-signer-status) - Change Signer Status
- `GET` [/signers/{externalSignerId}](#signer-details) - Signer Details
- `POST` [/documents](#upload-document) - Upload Document
- `PUT` [/documents/{documentId}](#reject-document) - Reject Document
- `DELETE` [/documents/{documentId}](#delete-document) - Delete Document
- `POST` [/documents/{documentId}/signature](#sign-document) - Sign Document
- `GET` [/documents/{documentId}/file](#download-document) - Download Document
<!-- end -->

## Error Handling

Signer Cloud Server uses the following format for error response body, accompanied by an appropriate HTTP status code. Besides the HTTP error codes that the application server may return regardless of the server application (such as 404 when the resource is not found or 503 when the server is down), the following error codes may be returned:

| Error Code                        | HTTP Code | Description                                                                                                                                                             |
|:----------------------------------|:----------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ERROR_UNAUTHORIZED                | 401       | Unauthorized request                                                                                                                                                    |
| REQUEST_VALIDATION_ERROR          | 400       | REST API endpoint called with invalid body or parameters                                                                                                                |
| ERROR_RESOURCE_NOT_FOUND          | 400       | Resource is not found                                                                                                                                                   |
| CERTIFICATE_PROCESSING_ERROR      | 500       | Issue with processing the certificate                                                                                                                                   |
| CSR_INVALID_SIGNATURE_ERROR       | 400       | Error when the signature of the CSR (Certificate Signing Request) is invalid                                                                                            |
| CSR_SIGNATURE_VERIFICATION_ERROR  | 503       | Error when the signature of the CSR (Certificate Signing Request) could not be verified. Can indicate a problem with the PowerAuth server                               |
| CERTIFICATE_AUTHORITY_ERROR       | 400,503   | Error returned from Certificate Authority. 4xx errors are mapped to 400, and 5xx errors are mapped to 503. Can indicate a problem with the Certificate Authority server |
| DOCUMENT_UPLOAD_ERROR             | 400       | Error when `Document` content could not be uploaded                                                                                                                     |
| DOCUMENT_INVALID_SIGNATURE_ERROR  | 400       | Error when the signature of `Document` is invalid                                                                                                                       |
| DOCUMENT_SIGNING_ERROR            | 500       | Error when the content of a signed `Document` could not be assembled                                                                                                    |
| ILLEGAL_OPERATION_ERROR           | 400       | The state of the resource does not allow the requested operation                                                                                                        |
| DOCUMENT_VISUAL_SIGNATURE_ERROR   | 400       | Visual signature is invalid for `Document`                                                                                                                              |
| ERROR_GENERIC                     | 400       | Any other error not covered by a specific error code                                                                                                                    |


All error responses that are produced by the Signer Cloud Server have the following body:

```json
{
  "status": "ERROR",
  "responseObject": {
    "code": "ERROR_GENERIC",
    "message": "An error message"
  }
}
```

##  API Endpoints

<!-- begin api POST /signers -->
###  Create New Signer

Create a new signer and enroll for a new certificate using CSR. The system will track certificate expiration and start the auto-renewal job.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>POST</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/signers</code></td>
    </tr>
</table>
<!-- end -->

#### Request

```json
{
  "externalSignerId": "456def",
  "userId" : "123abc",
  "csr": "-----BEGIN CERTIFICATE REQUEST-----\ncontent\nwith\ncorrect\nline\nendings\n-----END CERTIFICATE REQUEST-----",
}
```

##### Request Params

| Attribute          | Type     | Description                                          |
|:-------------------|:---------|:-----------------------------------------------------|
| `externalSignerId` | `String` | Activation ID (Registration ID) from PowerAuth.      |
| `userId`           | `String` | Custom User ID mostly for tracking purposes.         |
| `csr`              | `String` | PEM encoded PKCS10 CSR, one line, line endings `\n`. |

#### Response 200

```
200 OK
```

<!-- end -->

<!-- begin api PUT /signers/{externalSignerId} -->
###  Change Signer Status

Change the status of an existing signer (e.g., activate, deactivate, suspend) identified by <code>externalSignerId</code>.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>PUT</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/signers/{externalSignerId}</code></td>
    </tr>
</table>
<!-- end -->

#### Request

```json
{
  "signerStatus": "REVOKED",
  "revocationReason": "UNSPECIFIED"
}
```

##### Path Params

| Param              | Type     | Description                                     |
|:-------------------|:---------|:------------------------------------------------|
| `externalSignerId` | `String` | Activation ID (Registration ID) from PowerAuth. | 

##### Request Params

| Attribute          | Type     | Description                                                                                                                                                                                                                                                                                                                                                                                                           |
|:-------------------|:---------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `signerStatus`     | `String` | Select new signer status. ENUM: `ACTIVE`, `BLOCKED`, `REMOVED`, `REVOKED`, `EXPIRED`                                                                                                                                                                                                                                                                                                                                  |
| `revocationReason` | `String` | Optional parameter, used only if `signerStatus` is set to `REVOKED`. It specifies the reason for revocation, which is passed to EJBCA. If not provided, the default value `UNSPECIFIED` is used. ENUM: `NOT_REVOKED`, `UNSPECIFIED`, `KEY_COMPROMISE`, `CA_COMPROMISE`, `AFFILIATION_CHANGED`, `SUPERSEDED`, `CESSATION_OF_OPERATION`, `CERTIFICATE_HOLD`, `REMOVE_FROM_CRL`, `PRIVILEGES_WITHDRAWN`, `AA_COMPROMISE` |

#### Response 200

```
200 OK
```

<!-- end -->

<!-- begin api GET /signers/{externalSignerId} -->
###  Signer Details

Get signer state.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>GET</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/signers/{externalSignerId}</code></td>
    </tr>
</table>
<!-- end -->

#### Request

##### Path Params

| Param              | Type     | Description                                     |
|:-------------------|:---------|:------------------------------------------------|
| `externalSignerId` | `String` | Activation ID (Registration ID) from PowerAuth. |

#### Response 200

```json
{
  "externalSignerId": "123abc",
  "userId": "123abc",
  "signerStatus": "ACTIVE"
}
```

##### Response Params

| Attribute          | Type     | Description                                                               |
|:-------------------|:---------|:--------------------------------------------------------------------------|
| `externalSignerId` | `String` | Activation ID (Registration ID) from PowerAuth.                           |
| `userId`           | `String` | Custom User ID mostly for tracking purposes.                              |
| `signerStatus`     | `String` | Signer status. ENUM: `ACTIVE`, `BLOCKED`, `REMOVED`, `REVOKED`, `EXPIRED` |
<!-- end -->

<!-- begin api POST /documents -->
###  Upload Document

Upload document as one file using multipart/form-data. The maximum file size depends on the server configuration.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>POST</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/documents</code></td>
    </tr>
</table>
<!-- end -->

#### Request

Upload via Multipart Requests File Upload.

```
POST /documents/upload
Host: example.com
Content-Length: {size}
Content-Type: multipart/form-data; boundary=abcde12345

--abcde12345
Content-Disposition: form-data; name="externalSignerId"
Content-Type: text/plain

{externalSignerId}
--abcde12345
Content-Disposition: form-data; name="externalId"
Content-Type: text/plain

{externalId}
--abcde12345
Content-Disposition: form-data; name="name"
Content-Type: text/plain

{name}
--abcde12345
Content-Disposition: form-data; name="file"; filename="{fileName}"
Content-Type: application/pdf

{fileContent}
--abcde12345--

Content-Type: application/json
Content-Disposition: form-data; name="visualSignature"; filename="{visualSignatureFileName}"

{visualSignature}
--abcde12345--
```

##### Request Params

| Attribute          | Type     | Description                                                                                                                                                                                                    |
|:-------------------|:---------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `size`             | `Number` | Document size in bytes.                                                                                                                                                                                        |
| `externalSignerId` | `String` | Activation ID (Registration ID) from PowerAuth.                                                                                                                                                                |
| `externalId`       | `String` | Custom unique ID identifying document in client’s systems.                                                                                                                                                     |
| `name`             | `String` | Document name.                                                                                                                                                                                                 |
| `fileName`         | `String` | File name (including suffix), e.g., “attachment.pdf”.                                                                                                                                                           |
| `fileContent`      | `String` | File content (binary data).                                                                                                                                                                                    |
| `visualSignature`  | `String` | Optional parameter for visual signature definition as JSON. See [PAdES Visible Signature](https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html#PAdESVisibleSignatureAnnex). |


#### Response 200

```json
{
  "documentId": "123abc",
  "externalSignerId": "Signer-123abc",
  "externalId": "DocumentID-123abc",
  "name": "Document name",
  "fileName": "real_document_name.pdf",
  "size": 343425734,
  "hash": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
}
```


##### Response Params

| Attribute          | Type     | Description                                                                                                    |
|:-------------------|:---------|:---------------------------------------------------------------------------------------------------------------|
| `documentId`       | `String` | Document ID.                                                                                                   |
| `externalSignerId` | `String` | Activation ID (Registration ID) from PowerAuth.                                                                |
| `externalId`       | `String` | Custom unique ID identifying document in client’s systems.                                                     |
| `name`             | `String` | Document name.                                                                                                 |
| `fileName`         | `String` | File name (including suffix), e.g. “attachment.pdf”.                                                           |
| `size`             | `Number` | Document size in bytes.                                                                                        |
| `hash`             | `String` | SHA-256 summary of uploaded document. Hash has to be signed by the user and used in the Sign Document request. |

Maximum file size limitations depend on server configuration (web/apps server, database, network), with a max size of around 50MB.

Document mime-type validation is performed.

<!-- begin box warning -->
Because the `hash` of the document (including signature metadata) is calculated at this step, the document cannot be updated later.
For example, this affects the signature timestamp in the signed document, since the time of upload is used rather than the time when the document is actually signed (assembled).
<!-- end -->

<!-- end -->

<!-- begin api PUT /documents/{documentId} -->
###  Reject Document

Reject the document and terminate the signing process.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>PUT</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/documents/{documentId}</code></td>
    </tr>
</table>
<!-- end -->

#### Request

```json
{
  "status": "REJECTED"
}
```

##### Path Params

| Param        | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |

##### Request Params

| Attribute    | Type     | Description               |
|:-------------|:---------|:--------------------------|
| `status`     | `String` | Set status to `REJECTED`. |

#### Response 200

```json
{
  "documentId": "123abc",
  "name": "Document name",
  "fileName": "real_document_name.pdf",
  "size": 343425734,
  "hash": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
}
```

##### Response Params

| Attribute          | Type     | Description                                                                                            |
|:-------------------|:---------|:-------------------------------------------------------------------------------------------------------|
| `documentId`       | `String` | Document ID.                                                                                           |
| `name`             | `String` | Document name.                                                                                         |
| `fileName`         | `String` | File name (including suffix), e.g. “attachment.pdf”.                                                   |
| `size`             | `Number` | Document size in bytes.                                                                                |
| `hash`             | `String` | SHA-256 summary of uploaded document. Hash has to be signed by user and used in Sign Document request. |

<!-- end -->

<!-- begin api DELETE /documents/{documentId} -->
###  Delete Document

Delete the document, no matter if it’s only an uploaded or signed document.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>DELETE</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/documents/{documentId}</code></td>
    </tr>
</table>
<!-- end -->

#### Request

##### Path Params

| Param        | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |

#### Response 200

```
204 No Content
```
<!-- end -->

<!-- begin api POST /documents/{documentId}/signature -->
###  Sign Document

Complete the signature with the approved document hash.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>POST</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/documents/{documentId}/signature</code></td>
    </tr>
</table>
<!-- end -->

#### Request

```json
{
  "signature": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
}
```

##### Path Params

| Param        | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |

##### Request Params

| Attribute    | Type     | Description                                                                   |
|:-------------|:---------|:------------------------------------------------------------------------------|
| `signature`  | `String` | Hash taken from Document Upload and signed with private key on mobile device. |

#### Response 200

```json
{
  "documentId": "123abc",
  "uri": "https://HOSTNAME/documents/{documentId}"
}
```

##### Response Params

| Attribute          | Type     | Description                          |
|:-------------------|:---------|:-------------------------------------|
| `documentId`       | `String` | Document ID.                         |
| `uri`              | `String` | URL address of the created document. |

<!-- end -->

<!-- begin api GET /documents/{documentId}/file -->
###  Download Document

Download document.

We support the optional `Range` request header to download content partially, and `Accept-Ranges: bytes` response header to track the download progress. In this case, the `Content-Range` header will be returned in the response (otherwise, it will be omitted).

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>GET</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/documents/{documentId}/file</code></td>
    </tr>
</table>
<!-- end -->

#### Request

- Headers:
    - `Range: bytes=0-999`

##### Request Headers

| Header       | Description                                                |
|:-------------|:-----------------------------------------------------------|
| `Range`      | Optional byte range.                                       |

##### Path Params

| Param        | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |


#### Response 200

```
Accept-Ranges: bytes
Content-Length: 1000
Content-Range: bytes 0-999/98024
Content-Type: application/pdf

{fileContent}
```

##### Response Headers

| Header              | Description                                                              |
|:--------------------|:-------------------------------------------------------------------------|
| `Accept-Range`      | The supported range unit, only value `bytes` is currently supported.     |
| `Content-Length`    | Size of the body in an HTTP response, specified in bytes.                |
| `Content-Range`     | Specific range of payload returned in thh format `<unit> <range>/<size>` |
| `Content-Type`      | Type of the file, only `application/pdf` is currently supported.         |

##### Response Params

| Param         | Type      | Description                                                    |
|:--------------|:----------|:---------------------------------------------------------------|
| `fileContent` | `bytes[]` | The binary payload of the uploaded file, represented as bytes. |

<!-- end -->