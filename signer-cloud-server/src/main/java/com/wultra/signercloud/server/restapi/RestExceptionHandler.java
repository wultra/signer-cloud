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

import com.wultra.signercloud.server.document.DocumentNotFoundException;
import com.wultra.signercloud.server.document.DocumentUploadException;
import com.wultra.signercloud.server.document.SignDocumentException;
import com.wultra.signercloud.server.signer.SignerNotFoundException;
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
     * Handler for {@link DocumentUploadException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDocumentUploadException(final DocumentUploadException ex) {
        return produceBadRequest(ErrorCode.ERROR_GENERIC, ex.getMessage());
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
     * Handler for {@link SignDocumentException} producing {@link HttpStatus#BAD_REQUEST} response.
     *
     * @param ex the exception
     * @return response as {@link ResponseEntity}
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleSignDocumentException(final SignDocumentException ex) {
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
