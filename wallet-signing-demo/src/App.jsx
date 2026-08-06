import { useEffect, useState } from 'react'
import QRCode from 'qrcode'
import {
  completeSignatureRequest,
  createSignatureRequest,
  DEMO_PID,
  prepareSignedPdfDownload,
} from './api/scaApi.js'

const INITIAL_FLOW = {
  phase: 'idle',
  signatureRequest: null,
  qrCodeUrl: '',
  download: null,
  error: '',
}

const wait = (milliseconds) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

function App() {
  const [file, setFile] = useState(null)
  const [flow, setFlow] = useState(INITIAL_FLOW)

  const isBusy = !['idle', 'complete', 'error'].includes(flow.phase)

  useEffect(() => {
    return () => {
      if (flow.download?.url) {
        URL.revokeObjectURL(flow.download.url)
      }
    }
  }, [flow.download?.url])

  async function handleSubmit(event) {
    event.preventDefault()

    if (!file) {
      setFlow({ ...INITIAL_FLOW, phase: 'error', error: 'Select a PDF first.' })
      return
    }

    try {
      if (flow.download?.url) {
        URL.revokeObjectURL(flow.download.url)
      }

      setFlow({ ...INITIAL_FLOW, phase: 'uploading' })

      // The PDF and demo PID data are uploaded immediately to the SCA.
      // The SCA calls the mock QTSP, which starts the Verifier transaction.
      const signatureRequest = await createSignatureRequest(file)
      const qrCodeUrl = await QRCode.toDataURL(signatureRequest.deepLink, {
        width: 320,
        margin: 2,
        errorCorrectionLevel: 'M',
      })

      setFlow({
        ...INITIAL_FLOW,
        phase: 'waiting-for-wallet',
        signatureRequest,
        qrCodeUrl,
      })

      // Verifier callback/status is stubbed for now.
      await wait(3000)

      setFlow((current) => ({
        ...current,
        phase: 'finalizing',
      }))

      // The SCA must check the QTSP/Verifier authorization status before
      // calling POST /qtsp/signatures/signHash.
      await completeSignatureRequest(signatureRequest)

      const download = await prepareSignedPdfDownload(
        signatureRequest.signatureRequestId,
        file.name,
      )

      setFlow((current) => ({
        ...current,
        phase: 'complete',
        download,
      }))
    } catch (error) {
      console.error(error)
      setFlow((current) => ({
        ...current,
        phase: 'error',
        error: error instanceof Error ? error.message : 'The demo failed.',
      }))
    }
  }

  function handleFileChange(event) {
    if (flow.download?.url) {
      URL.revokeObjectURL(flow.download.url)
    }

    setFile(event.target.files?.[0] ?? null)
    setFlow(INITIAL_FLOW)
  }

  return (
    <main>
      <h1>Sign PDF</h1>

      <p>
        Certificate user: {DEMO_PID.given_name} {DEMO_PID.family_name} ({DEMO_PID.personal_administrative_number})
      </p>

      <form onSubmit={handleSubmit}>
        <input
          type="file"
          accept="application/pdf,.pdf"
          onChange={handleFileChange}
          disabled={isBusy}
        />
        <button type="submit" disabled={isBusy || !file}>
          Start
        </button>
      </form>

      {file && <p>Selected file: {file.name}</p>}
      {flow.phase === 'uploading' && <p>Uploading PDF and creating certificate…</p>}
      {flow.error && <p className="error">Error: {flow.error}</p>}

      {flow.signatureRequest && (
        <section>
          <h2>Confirm with wallet</h2>
          <img src={flow.qrCodeUrl} alt="Wallet QR code" />
          <p>
            Deep link:{' '}
            <a href={flow.signatureRequest.deepLink}>
              {flow.signatureRequest.deepLink}
            </a>
          </p>
        </section>
      )}

      {flow.phase === 'waiting-for-wallet' && (
        <p>Waiting three seconds for the wallet verification stub…</p>
      )}

      {flow.phase === 'finalizing' && <p>Signing the document…</p>}

      {flow.download && (
        <section>
          <h2>Success</h2>
          <a href={flow.download.url} download={flow.download.fileName}>
            Download {flow.download.fileName}
          </a>
        </section>
      )}
    </main>
  )
}

export default App
