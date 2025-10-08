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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RestExceptionHandler}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class RestExceptionHandlerTest {

    private static final String ERROR_STATUS = "ERROR";
    private static final String ERROR_MESSAGE = "Validation Failed";
    private static final String DUMMY_OBJECT_NAME = "dummyObject";
    private static final String DUMMY_FIELD_NAME = "dummyField";

    private static final String EXPECTED_SPECIFIC_MESSAGE = "Validation failed: " + DUMMY_FIELD_NAME + " " + ERROR_MESSAGE;
    private static final String EXPECTED_GENERIC_MESSAGE = "Validation failed: Unknown validation error";

    private BindingResult bindingResult;

    @Mock
    private MethodArgumentNotValidException exception;

    @InjectMocks
    private RestExceptionHandler restExceptionHandler;

    @BeforeEach
    void setUp() {
        bindingResult = new BeanPropertyBindingResult(new Object(), DUMMY_OBJECT_NAME);
    }

    @Test
    void testHandleValidationExceptionWhenInvalidFieldIsSpecifiedThenSpecificMessageIsReturned() {
        // Given
        bindingResult.addError(new FieldError(DUMMY_OBJECT_NAME, DUMMY_FIELD_NAME, ERROR_MESSAGE));

        when(exception.getBindingResult()).thenReturn(bindingResult);

        // When
        final var response = restExceptionHandler.handleValidationException(exception);

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, EXPECTED_SPECIFIC_MESSAGE, ErrorCode.REQUEST_VALIDATION_ERROR);
    }

    @Test
    void testHandleValidationExceptionWhenInvalidFieldIsNotSpecifiedThenGenericMessageIsReturned() {
        // Given
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // When
        final var response = restExceptionHandler.handleValidationException(exception);

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, EXPECTED_GENERIC_MESSAGE, ErrorCode.REQUEST_VALIDATION_ERROR);
    }

    @Test
    void testHandleDocumentContentNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        // -

        // When
        final var response = restExceptionHandler.handleDocumentContentNotFoundException(DocumentContentNotFoundException.forId("123"));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, "Content for document ID 123 not found", ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleDocumentNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        // -

        // When
        final var response = restExceptionHandler.handleDocumentNotFoundException(DocumentNotFoundException.forId("123"));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, "Document with ID 123 not found", ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleDocumentStateExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Document is in invalid state for this operation.";

        // When
        final var response = restExceptionHandler.handleDocumentStateException(new DocumentStateException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.ILLEGAL_OPERATION_ERROR);
    }

    @Test
    void testHandleDocumentStatusTransitionExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Invalid document status transition.";

        // When
        final var response = restExceptionHandler.handleDocumentStatusTransitionException(new DocumentStatusTransitionException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.ILLEGAL_OPERATION_ERROR);
    }

    @Test
    void testHandleDocumentUploadExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Unsupported content type: image/jpeg";

        // When
        final var response = restExceptionHandler.handleDocumentUploadException(new DocumentUploadException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.DOCUMENT_UPLOAD_ERROR);
    }

    @Test
    void testHandleDocumentSigningExceptionWhenSigningExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Error when assembling signed document content";

        // When
        final var response = restExceptionHandler.handleDocumentSigningException(new DocumentSigningException(message, new RuntimeException()));

        // Then
        assertErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, response, message, ErrorCode.DOCUMENT_SIGNING_ERROR);
    }

    @Test
    void testHandleDocumentInvalidSignatureExceptionWhenSigningExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Invalid document signature";

        // When
        final var response = restExceptionHandler.handleDocumentInvalidSignatureException(new DocumentInvalidSignatureException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.DOCUMENT_INVALID_SIGNATURE_ERROR);
    }

    @Test
    void testHandleCertificateProcessingExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Certificate processing error";

        // When
        final var response = restExceptionHandler.handleCertificateProcessingException(new CertificateProcessingException(message, new RuntimeException()));

        // Then
        assertErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, response, message, ErrorCode.CERTIFICATE_PROCESSING_ERROR);
    }

    @Test
    void testHandleCsrProcessingExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "CSR processing error";

        // When
        final var response = restExceptionHandler.handleCsrProcessingException(new CsrProcessingException(message, new RuntimeException()));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.CSR_INVALID_SIGNATURE_ERROR);
    }

    @Test
    void testHandleCertificateAuthorityExceptionWhenExceptionWith4xxHttpStatusIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Ejbca error";

        // When
        final var response = restExceptionHandler.handleCertificateAuthorityException(
                new CertificateAuthorityException(message, new RuntimeException(), HttpStatus.NOT_FOUND)
        );

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.CERTIFICATE_AUTHORITY_ERROR);
    }

    @Test
    void testHandleCertificateAuthorityExceptionWhenExceptionWith5xxHttpStatusIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Ejbca error";

        // When
        final var response = restExceptionHandler.handleCertificateAuthorityException(
                new CertificateAuthorityException(message, new RuntimeException(), HttpStatus.INTERNAL_SERVER_ERROR)
        );

        // Then
        assertErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, response, message, ErrorCode.CERTIFICATE_AUTHORITY_ERROR);
    }

    @Test
    void testHandleCsrInvalidSignatureExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Invalid CSR signature";

        // When
        final var response = restExceptionHandler.handleSignatureVerificationException(new CsrInvalidSignatureException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.CSR_INVALID_SIGNATURE_ERROR);
    }

    @Test
    void testHandleCsrVerificationExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Signature verification failed";

        // When
        final var response = restExceptionHandler.handleCsrVerificationException(new CsrVerificationException(message, new RuntimeException()));

        // Then
        assertErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, response, message, ErrorCode.CSR_SIGNATURE_VERIFICATION_ERROR);
    }

    @Test
    void testHandleSignerNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        // -

        // When
        final var response = restExceptionHandler.handleSignerNotFoundException(SignerNotFoundException.forId("abc"));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, "Signer with ID abc not found", ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleSignerStateExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Signer is in invalid state for this operation.";

        // When
        final var response = restExceptionHandler.handleSignerStateException(new SignerStateException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.ILLEGAL_OPERATION_ERROR);
    }

    @Test
    void testHandleSignerStatusTransitionExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Invalid signer status transition.";

        // When
        final var response = restExceptionHandler.handleSignerStatusTransitionException(new SignerStatusTransitionException(message));

        // Then
        assertErrorResponse(HttpStatus.BAD_REQUEST, response, message, ErrorCode.ILLEGAL_OPERATION_ERROR);
    }

    @Test
    void testHandleTimestampAuthorityExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // given
        final var message = "TSA URL not set in configuration";

        // when
        final var response = restExceptionHandler.handleTimestampAuthorityException(new TimestampAuthorityException(message));

        // then
        assertErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, response, message, ErrorCode.TIMESTAMP_AUTHORITY_ERROR);
    }

    @Test
    void testHandleRuntimeExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Generic runtime exception";

        // When
        final var response = restExceptionHandler.handleRuntimeException(new RuntimeException(message));

        // Then
        assertErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, response, message, ErrorCode.ERROR_GENERIC);
    }

    private void assertErrorResponse(
            final HttpStatus httpStatus,
            final ResponseEntity<ErrorResponse> responseEntity,
            final String expectedMessage,
            final ErrorCode expectedCode
    ) {
        assertEquals(httpStatus, responseEntity.getStatusCode());

        final var body = responseEntity.getBody();
        assertEquals(ERROR_STATUS, body.status());

        final var responseObject = body.responseObject();
        assertEquals(expectedCode, responseObject.code());
        assertEquals(expectedMessage, responseObject.message());
    }
}
