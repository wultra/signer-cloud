# Signer Cloud API
<!-- template api -->

Signer Cloud Server provides a RESTful API that allows to control specific parts of the signing process. 

<!-- begin remove -->
- `POST` [/signers](#create-new-signer) - Create New Signer
- `PUT` [/signers/{signerId}](#change-signer-status) - Change Signer Status
- `GET` [/signers/{signerId}](#signer-details) - Signer Details
- `POST` [/documents](#upload-document) - Upload Document
- `PUT` [/documents/{documentId}](#reject-document) - Reject Document
- `DELETE` [/documents/{documentId}](#delete-document) - Delete Document
- `POST` [/documents/{documentId}/signature](#sign-document) - Sign Document
- `GET` [/documents/{documentId}/file](#download-document) - Download Document
<!-- end -->

## Error Handling

Signer Cloud Server uses following format for error response body, accompanied by an appropriate HTTP status code. Besides the HTTP error codes that application server may return regardless of server application (such as 404 when resource is not found or 503 when server is down), following ERROR codes may be returned:

| Error Code         | HTTP Code | Description                                                |
|:-------------------|:----------|:-----------------------------------------------------------|
| ERROR_GENERIC      | 400       | Issue with a request format or issue of the business logic |
| ERROR_UNAUTHORIZED | 401       | Unauthorized request                                       |

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

Create new signer and enroll for new certificate using CSR. System will track certificate expiration and starts auto-renewal job.

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
  "signerId": "456def",
  "userId" : "123abc",
  "csr": "-----BEGIN CERTIFICATE REQUEST-----\ncontent\nwith\ncorrect\nline\nendings\n-----END CERTIFICATE REQUEST-----",
}
```

##### Request Params

| Attribute                | Type     | Description                                                                                                                            |
|:-------------------------|:---------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `signerId`               | `String` | Activation ID (Registration ID) from PowerAuth.                                                                                        |
| `userId`                 | `String` | Custom User ID mostly for tracking purposes.                                                                                           |
| `csr`                    | `String` | PEM encoded PKCS10 CSR, one line, line endings `\n`.                                                                                   |

#### Response 200

```json
{
  "result": "String",
  "resultReason": null
}
```

##### Response  Params

| Attribute      | Type     | Description                                                                                                                                       |
|:---------------|:---------|:--------------------------------------------------------------------------------------------------------------------------------------------------|
| `result`       | `String` | The processing outcome `OK`, `FAIL`.                                                                                                              |
| `resultReason` | `String` | The reason is used when result is `FAIL` to disclose the reason of failed process.                                                                  |
<!-- end -->

<!-- begin api PUT /signers/{signerId} -->
###  Change Signer Status

Change the status of an existing signer (e.g., activate, deactivate, suspend) identified by <code>signerId</code>.

<!-- begin remove -->
<table>
    <tr>
        <td>Method</td>
        <td><code>PUT</code></td>
    </tr>
    <tr>
        <td>Resource URI</td>
        <td><code>/signers/{signerId}</code></td>
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

##### Request Params

| Attribute          | Type     | Description                                                                                                                                                                                                                                                                                                                                                                                                           |
|:-------------------|:---------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `signerId`         | `String` | Activation ID (Registration ID) from PowerAuth.                                                                                                                                                                                                                                                                                                                                                                       |
| `signerStatus`     | `String` | Select new signer status. ENUM: `ACTIVE`, `BLOCKED`, `REMOVED`, `REVOKED`, `EXPIRED`                                                                                                                                                                                                                                                                                                                                  |
| `revocationReason` | `String` | Optional parameter, used only if `signerStatus` is set to `REVOKED`. It specifies the reason for revocation, which is passed to EJBCA. If not provided, the default value `UNSPECIFIED` is used. ENUM: `NOT_REVOKED`, `UNSPECIFIED`, `KEY_COMPROMISE`, `CA_COMPROMISE`, `AFFILIATION_CHANGED`, `SUPERSEDED`, `CESSATION_OF_OPERATION`, `CERTIFICATE_HOLD`, `REMOVE_FROM_CRL`, `PRIVILEGES_WITHDRAWN`, `AA_COMPROMISE` |

#### Response 200

```json
{
  "result": "String",
  "resultReason": null
}
```

##### Response  Params

| Attribute      | Type     | Description                                                                                                                                      |
|:---------------|:---------|:-------------------------------------------------------------------------------------------------------------------------------------------------|
| `result`       | `String` | The processing outcome `OK`, `FAIL`.                                                                                                                 |
| `resultReason` | `String` | The reason is used when result is `FAIL` to disclose the reason of failed process.                                                                 |
<!-- end -->

<!-- begin api GET /signers/{signerId} -->
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
        <td><code>/signers/{signerId}</code></td>
    </tr>
</table>
<!-- end -->

#### Request

```json
 
```

##### Request Params

| Attribute                | Type     | Description                                                                                                                            |
|:-------------------------|:---------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `signerId`               | `String` | Activation ID (Registration ID) from PowerAuth.                                                                                        |

#### Response 200

```json
{
  "signerId": "123abc",
  "userId": "123abc",
  "signerStatus": "ACTIVE"
}
```

##### Response  Params

| Attribute      | Type     | Description                                                               |
|:---------------|:---------|:--------------------------------------------------------------------------|
| `signerId`     | `String` | Activation ID (Registration ID) from PowerAuth.                           |
| `userId`       | `String` | Custom User ID mostly for tracking purposes.                              |
| `signerStatus` | `String` | Signer status. ENUM: `ACTIVE`, `BLOCKED`, `REMOVED`, `REVOKED`, `EXPIRED` |
<!-- end -->

<!-- begin api POST /documents -->
###  Upload Document

Upload document as one file using multipart/form-data. Maximum file size depends on server configuration.

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
Content-Disposition: form-data; name="signerId"
Content-Type: text/plain

{signerId}
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
```

##### Request Params

| Attribute     | Type      | Description                                                |
|:--------------|:----------|:-----------------------------------------------------------|
| `size`        | `Number`  | Document size in bytes.                                    |
| `signerId`    | `String`  | Activation ID (Registration ID) from PowerAuth.            |
| `externalId`  | `String`  | Custom unique ID identifying document in client’s systems. |
| `name`        | `String`  | Document name.                                             |
| `fileName`    | `String`  | File name (including suffix), e.g. “attachment.pdf”.       |
| `fileContent` | `String`  | File content (binary data).                                |


#### Response 200

```json
{
  "documentId": "String",
  "signerId": "String",
  "externalId": "String",
  "name": "String",
  "fileName": "String",
  "size": Number,
  "hash": "String",
}
```

##### Response  Params

| Attribute | Type     | Description                                                                                            |
|:----------|:---------|:-------------------------------------------------------------------------------------------------------|
| `hash`    | `String` | SHA-256 summary of uploaded document. Hash has to be signed by user and used in Sign Document request. |

Maximum file size limitations depends on server configuration (web/apps server, database, network) with max size around 50MB.

Document mime-type validation is performed.
<!-- end -->

<!-- begin api PUT /documents/{documentId} -->
###  Reject Document

Reject document and terminate signing process.

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

##### Request Params

| Attribute    | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |
| `status`     | `String` | Set status to `REJECTED`.                                    |

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
<!-- end -->

<!-- begin api DELETE /documents/{documentId} -->
###  Delete Document

Delete document, no matter if it’s only uploaded or signed document.

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

```json
 
```

##### Request Params

| Attribute    | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |

#### Response 200

```
204 No Content
```
<!-- end -->

<!-- begin api POST /documents/{documentId}/signature -->
###  Sign Document

Complete the signature with approved document hash.

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

##### Request Params

| Attribute    | Type     | Description                                                                   |
|:-------------|:---------|:------------------------------------------------------------------------------|
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems.                    |
| `signature`  | `String` | Hash taken from Document Upload and signed with private key on mobile device. |

#### Response 200

```json
{
  "documentId": "123abc",
  "uri": "https://HOSTNAME/documents/{documentId}"
}
```
<!-- end -->

<!-- begin api GET /documents/{documentId}/file -->
###  Download Document

Download document.

As you can see from the Accept-Ranges: bytes response header, we support optional Range request header to download content partially. In this case `Content-Range` header will be returned in the response (otherwise it will be omitted).

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

```
Range: bytes=0-999
```

##### Request Params

| Attribute    | Type     | Description                                                |
|:-------------|:---------|:-----------------------------------------------------------|
| `Range`      | `String` | Optional byte range.                                       |
| `documentId` | `String` | Custom Unique ID identifying document in client’s systems. |

#### Response 200

```
Accept-Ranges: bytes
Content-Length: 1000
Content-Range: bytes 0-999/98024
Content-Type: application/pdf

{fileContent}
```
<!-- end -->
