const SCA_PROXY_PREFIX = '/sca'
const SCA_USERNAME = 'user'
const SCA_PASSWORD = 'dummypassword'
const SCA_AUTHORIZATION = `Basic ${window.btoa(
  `${SCA_USERNAME}:${SCA_PASSWORD}`,
)}`

// Demo customer data known by the banking application. The SCA forwards these
// PID-style values to the mock QTSP when creating the certificate.
export const DEMO_PID = {
  given_name: 'Erika',
  family_name: 'Mustermann',
  birthdate: '1990-01-01',
  personal_administrative_number: 'DEMO-123456',
  issuing_country: 'CZ',
}

async function scaFetch(path, options = {}) {
  const headers = new Headers(options.headers)

  // Local demo only. Browser users can see these credentials in DevTools.
  headers.set('Authorization', SCA_AUTHORIZATION)

  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }

  return fetch(`${SCA_PROXY_PREFIX}${path}`, {
    ...options,
    headers,
  })
}

async function readJson(response, operation) {
  if (!response.ok) {
    const details = await response.text().catch(() => '')
    throw new Error(
      `${operation} failed with HTTP ${response.status}${
        details ? `: ${details.slice(0, 300)}` : ''
      }`,
    )
  }

  return response.json()
}

/**
 * Uploads the PDF immediately. The SCA should:
 * 1. store the PDF,
 * 2. call POST /qtsp/credentials with metadata.pid,
 * 3. receive a certificate and wallet authorization deep link,
 * 4. prepare the PDF hash,
 * 5. return the deep link in this response.
 */
export async function createSignatureRequest(pdfFile) {
  const metadata = {
    externalId: `doc-${crypto.randomUUID()}`,
    name: pdfFile.name,
    pid: DEMO_PID,
    qtspSessionId: "session-123",
    credentialId: "cred-123"
  }

  const formData = new FormData()
  formData.append(
    'metadata',
    new Blob([JSON.stringify(metadata)], { type: 'application/json' }),
  )
  formData.append('file', pdfFile, pdfFile.name)

  const response = await scaFetch('/signature/request', {
    method: 'POST',
    body: formData,
  })

  const result = await readJson(response, 'Creating the signature request')
  const deepLink =
    result.authorizationRequestUrl ??
    result.authorizationUrl ??
    result.deepLink

  if (!result.signatureRequestId) {
    throw new Error('The SCA response is missing signatureRequestId.')
  }

  if (!deepLink) {
    throw new Error(
      'The SCA response is missing authorizationRequestUrl, authorizationUrl, or deepLink.',
    )
  }

  if (!result.state) {
    throw new Error('The SCA response is missing state.')
  }

  return {
    ...result,
    deepLink,
    metadata,
  }
}

/**
 * Temporary replacement for the real wallet/Verifier completion signal.
 * The SCA callback must still verify the authorization status server-to-server
 * before asking the mock QTSP to sign the document hash.
 */
export async function completeSignatureRequest(signatureRequest) {
  const query = new URLSearchParams({
    code: `demo-${crypto.randomUUID()}`,
    state: signatureRequest.state,
  })

  const response = await scaFetch(
    `/signature/callback?${query.toString()}`,
    { method: 'GET' },
  )

  return readJson(response, 'Completing the signature request')
}

/**
 * A normal download link cannot add the Basic Auth header. Fetch the PDF first
 * and expose a local blob URL as the clickable link.
 */
export async function prepareSignedPdfDownload(
  signatureRequestId,
  originalFileName,
) {
  const response = await scaFetch(
    `/signature/${encodeURIComponent(signatureRequestId)}/file`,
    {
      method: 'GET',
      headers: {
        Accept: 'application/pdf',
      },
    },
  )

  if (!response.ok) {
    const details = await response.text().catch(() => '')
    throw new Error(
      `Downloading the signed PDF failed with HTTP ${response.status}${
        details ? `: ${details.slice(0, 300)}` : ''
      }`,
    )
  }

  const pdfBlob = await response.blob()

  return {
    url: URL.createObjectURL(pdfBlob),
    fileName: `signed-${originalFileName}`,
  }
}
