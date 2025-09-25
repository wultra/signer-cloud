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

import com.wultra.signercloud.server.document.*;
import com.wultra.signercloud.server.signer.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Handler for validation exception producing HTTP 400 response.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ControllerAdvice
@Slf4j
public class RestExceptionHandler {
    private static final String ERROR_STATUS = "ERROR";

    /**
     * Handler for {@link MethodArgumentNotValidException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleValidationException(final MethodArgumentNotValidException ex) {
        final var message = "Validation failed: " + ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst()
                .orElse("Unknown validation error");

        final var responseBody = new ErrorResponse(
                ERROR_STATUS,
                new ErrorDetails(ErrorCode.ERROR_GENERIC, message)
        );

        return ResponseEntity.badRequest().body(responseBody);
    }

    /**
     * Handler for {@link DocumentContentNotFoundException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentContentNotFoundException(final DocumentContentNotFoundException ex) {
        return produceBadRequest(ErrorCode.ERROR_RESOURCE_NOT_FOUND, ex.getMessage());
    }

    /**
     * Handler for {@link DocumentNotFoundException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentNotFoundException(final DocumentNotFoundException ex) {
        return produceBadRequest(ErrorCode.ERROR_RESOURCE_NOT_FOUND, ex.getMessage());
    }

    /**
     * Handler for {@link DocumentStateException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentStateException(final DocumentStateException ex) {
        return produceBadRequest(ErrorCode.DOCUMENT_STATE_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link DocumentStatusTransitionException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentStatusTransitionException(final DocumentStatusTransitionException ex) {
        return produceBadRequest(ErrorCode.DOCUMENT_STATUS_TRANSITION_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link DocumentUploadException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentUploadException(final DocumentUploadException ex) {
        return produceBadRequest(ErrorCode.DOCUMENT_UPLOAD_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link DocumentSigningException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentSigningException(final DocumentSigningException ex) {
        return produceBadRequest(ErrorCode.DOCUMENT_SIGNING_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link CertificateProcessingException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleCertificateProcessingException(final CertificateProcessingException ex) {
        return produceBadRequest(ErrorCode.CERTIFICATE_PROCESSING_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link CsrProcessingException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleCsrProcessingException(final CsrProcessingException ex) {
        return produceBadRequest(ErrorCode.CSR_PROCESSING_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link EjbcaException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleEjbcaException(final EjbcaException ex) {
        return produceBadRequest(ErrorCode.EJBCA_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link SignatureVerificationException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleSignatureVerificationException(final SignatureVerificationException ex) {
        return produceBadRequest(ErrorCode.SIGNATURE_VERIFICATION_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link SignerNotFoundException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleSignerNotFoundException(final SignerNotFoundException ex) {
        return produceBadRequest(ErrorCode.ERROR_RESOURCE_NOT_FOUND, ex.getMessage());
    }

    /**
     * Handler for {@link SignerStateException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleSignerStateException(final SignerStateException ex) {
        return produceBadRequest(ErrorCode.SIGNER_STATE_ERROR, ex.getMessage());
    }

    /**
     * Handler for {@link SignerStatusTransitionException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleSignerStatusTransitionException(final SignerStatusTransitionException ex) {
        return produceBadRequest(ErrorCode.SIGNER_STATUS_TRANSITION_ERROR, ex.getMessage());
    }

    /**
     * Handler for generic {@link RuntimeException} producing {@link HttpStatus#BAD_REQUEST} response.
     * This is a fallback handler for all unhandled runtime exceptions.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleRuntimeException(final RuntimeException ex) {
        logger.error("Unexpected runtime exception occurred", ex);
        return produceBadRequest(ErrorCode.ERROR_GENERIC, ex.getMessage());
    }

    private static ResponseEntity<ErrorResponse> produceBadRequest(final ErrorCode errorCode, final String message) {
        final var responseBody = new ErrorResponse(
                ERROR_STATUS,
                new ErrorDetails(errorCode, message)
        );

        return ResponseEntity.badRequest().body(responseBody);
    }
}
