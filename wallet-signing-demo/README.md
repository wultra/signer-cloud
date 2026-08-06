# Wallet PDF Signing Demo — SCA starts QTSP and Verifier flow

This is a deliberately small React/Vite demo following the simplified flow:

1. The user selects a PDF and clicks **Start**.
2. React immediately calls `POST /signature/request` with the PDF and PID-style
   certificate data inside the multipart `metadata` part.
3. The SCA calls the mock QTSP to create a credential/certificate.
4. During credential creation, the mock QTSP starts the OID4VP transaction at
   the Verifier and returns the wallet deep link through the SCA.
5. React displays the deep link as a QR code and clickable link.
6. The wallet/Verifier result is stubbed by waiting three seconds.
7. React calls `GET /signature/callback` with a dummy code and the SCA state.
8. The SCA checks authorization server-to-server, asks the QTSP to sign the
   prepared PDF hash, and creates the signed PDF.
9. React downloads the PDF with Basic Auth and displays a clickable blob link.

## Run

Requirements:

- Node.js 20.19 or newer
- npm
- SCA at `http://localhost:8090/`

```bash
npm install
npm run dev
```

Open the URL printed by Vite, normally `http://localhost:5173`.

## Basic Auth

Every SCA request includes:

```http
Authorization: Basic dXNlcjpkdW1teXBhc3N3b3Jk
```

This represents `user:dummypassword`. The credentials are intentionally visible
in browser code for this local demo only.

## Multipart metadata

`POST /signature/request` sends a JSON multipart part named `metadata`:

```json
{
  "externalId": "doc-generated-uuid",
  "name": "input.pdf",
  "pid": {
    "given_name": "Erika",
    "family_name": "Mustermann",
    "birthdate": "1990-01-01",
    "personal_administrative_number": "DEMO-123456",
    "issuing_country": "CZ"
  }
}
```

The demo values are in `src/api/scaApi.js` as `DEMO_PID`.

The SCA request DTO must accept the nested `pid` object. The SCA should pass
these values to `POST /qtsp/credentials` and should not accept a browser-created
`credentialId`; the mock QTSP creates and returns that identifier.

## Expected SCA response

The frontend accepts any one of these deep-link properties:

- `authorizationRequestUrl`
- `authorizationUrl`
- `deepLink`

A typical response is:

```json
{
  "signatureRequestId": "sig-123",
  "status": "AWAITING_USER_AUTHORIZATION",
  "authorizationRequestUrl": "eudi-openid4vp://authorize?...",
  "state": "state-123"
}
```

## Demo endpoints

```text
POST /signature/request
GET  /signature/callback?code=...&state=...
GET  /signature/{signatureRequestId}/file
```

The SCA is expected to use these mock QTSP/Verifier calls internally:

```text
POST /qtsp/credentials
POST /qtsp/signatures/signHash
POST /verifier/presentations
GET  /verifier/presentations/{verifierSessionId}
```

The browser callback is only a trigger. Before signing, the SCA/QTSP must verify
that the associated Verifier transaction is in the `VERIFIED` state.
