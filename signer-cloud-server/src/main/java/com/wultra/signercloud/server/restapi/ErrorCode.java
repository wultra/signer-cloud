/*
 * Signer Cloud
 * Copyright (C) 2025 Wultra s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wultra.signercloud.server.restapi;

/**
 * Error codes for {@link ErrorResponse} that extend HTTP status codes, representing more detailed
 * subcategories or specific reasons for the error.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public enum ErrorCode {

    /**
     * Unauthorized request.
     */
    ERROR_UNAUTHORIZED,

    /**
     * REST API endpoint called with invalid body or parameters.
     */
    REQUEST_VALIDATION_ERROR,

    /**
     * Resource is not found.
     */
    ERROR_RESOURCE_NOT_FOUND,

    /**
     * Issue with processing a certificate.
     */
    CERTIFICATE_PROCESSING_ERROR,

    /**
     * Signature verification via PowerAuth server failed.
     */
    SIGNATURE_VERIFICATION_ERROR,

    /**
     * Invalid status change of the {@link com.wultra.signercloud.server.signer.Signer}.
     */
    SIGNER_STATUS_TRANSITION_ERROR,

    /**
     * Error when processing the CSR (Certificate Signing Request).
     */
    CSR_PROCESSING_ERROR,

    /**
     * Error returned from EJBCA.
     */
    EJBCA_ERROR,

    /**
     * Error when document could not be uploaded.
     */
    DOCUMENT_UPLOAD_ERROR,

    /**
     * Invalid status change of the {@link com.wultra.signercloud.server.document.Document}.
     */
    DOCUMENT_STATUS_TRANSITION_ERROR,

    /**
     * Document is in invalid state for the requested operation.
     */
    DOCUMENT_STATE_ERROR,

    /**
     * Error when signing the {@link com.wultra.signercloud.server.document.Document}. Either signature is invalid or error when assembling the signed document.
     */
    DOCUMENT_SIGNING_ERROR,

    /**
     * {@link com.wultra.signercloud.server.signer.Signer} is in invalid state for the requested operation.
     */
    SIGNER_STATE_ERROR,

    /**
     * Any other error not covered by a specific error code.
     */
    ERROR_GENERIC,
}
