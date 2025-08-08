package com.wultra.signercloud.server.status;

/**
 * Enum representing the status of a document in the signing process.
 *
 * <ul>
 *   <li>WAITING - Document is uploaded and is waiting for signature</li>
 *   <li>REJECTED - Document was rejected by signer</li>
 *   <li>SIGNED - Document is signed</li>
 * </ul>
 */
public enum DocumentStatus {
    WAITING,
    REJECTED,
    SIGNED
}
