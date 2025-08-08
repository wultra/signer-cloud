package com.wultra.signercloud.server.status;

/**
 * Enum representing the status of a signer.
 *
 * <ul>
 *   <li>ACTIVE - Signer can sign documents</li>
 *   <li>BLOCKED - Signer cannot sign documents. It can be moved back to ACTIVE</li>
 *   <li>REMOVED - Signer cannot sign documents, but certificate stays active until its expiration</li>
 *   <li>REVOKED - Signer cannot sign documents and certificate is immediately revoked</li>
 * </ul>
 */
public enum SignerStatus {
    ACTIVE,
    BLOCKED,
    REMOVED,
    REVOKED
}
