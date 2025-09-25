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
     * Issue with a request format or issue of the business logic.
     */
    ERROR_GENERIC,

    /**
     * Unauthorized request.
     */
    ERROR_UNAUTHORIZED,

    /**
     * Resource is not found.
     */
    ERROR_RESOURCE_NOT_FOUND,

    /**
     * Error during Signer certificate enrollment process.
     */
    CERTIFICATE_ENROLLMENT_ERROR,

    /**
     * Error during Signer certificate revocation process.
     */
    CERTIFICATE_REVOCATION_ERROR,

    /**
     * Error during Signer CSR verification process.
     */
    CSR_VERIFICATION_ERROR,

    /**
     * Invalid status change of the Signer.
     */
    SIGNER_STATUS_TRANSITION_ERROR
}
