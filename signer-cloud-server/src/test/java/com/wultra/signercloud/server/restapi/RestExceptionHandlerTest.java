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
        assertErrorResponse(response, EXPECTED_SPECIFIC_MESSAGE, ErrorCode.ERROR_GENERIC);
    }

    @Test
    void testHandleValidationExceptionWhenInvalidFieldIsNotSpecifiedThenGenericMessageIsReturned() {
        // Given
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // When
        final var response = restExceptionHandler.handleValidationException(exception);

        // Then
        assertErrorResponse(response, EXPECTED_GENERIC_MESSAGE, ErrorCode.ERROR_GENERIC);
    }

    @Test
    void testHandleDocumentContentNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        // -

        // When
        final var response = restExceptionHandler.handleDocumentContentNotFoundException(new DocumentContentNotFoundException("123"));

        // Then
        assertErrorResponse(response, "Content for document ID 123 not found", ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleDocumentNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        // -

        // When
        final var response = restExceptionHandler.handleDocumentNotFoundException(new DocumentNotFoundException("123"));

        // Then
        assertErrorResponse(response, "Document with ID 123 not found", ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleDocumentStateExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Document is in invalid state for this operation.";

        // When
        final var response = restExceptionHandler.handleDocumentStateException(new DocumentStateException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.DOCUMENT_STATE_ERROR);
    }

    @Test
    void testHandleDocumentStatusTransitionExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Invalid document status transition.";

        // When
        final var response = restExceptionHandler.handleDocumentStatusTransitionException(new DocumentStatusTransitionException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.DOCUMENT_STATUS_TRANSITION_ERROR);
    }

    @Test
    void testHandleDocumentUploadExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Unsupported content type: image/jpeg";

        // When
        final var response = restExceptionHandler.handleDocumentUploadException(new DocumentUploadException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.DOCUMENT_UPLOAD_ERROR);
    }

    @Test
    void testHandleDocumentSigningExceptionWhenSigningExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Document signing timeout exceeded";

        // When
        final var response = restExceptionHandler.handleDocumentSigningException(new DocumentSigningException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.DOCUMENT_SIGNING_ERROR);
    }

    @Test
    void testHandleCertificateProcessingExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Certificate processing error";

        // When
        final var response = restExceptionHandler.handleCertificateProcessingException(new CertificateProcessingException(message, new RuntimeException()));

        // Then
        assertErrorResponse(response, message, ErrorCode.CERTIFICATE_PROCESSING_ERROR);
    }

    @Test
    void testHandleCsrProcessingExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "CSR processing error";

        // When
        final var response = restExceptionHandler.handleCsrProcessingException(new CsrProcessingException(message, new RuntimeException()));

        // Then
        assertErrorResponse(response, message, ErrorCode.CSR_PROCESSING_ERROR);
    }

    @Test
    void testHandleEjbcaExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Ejbca error";

        // When
        final var response = restExceptionHandler.handleEjbcaException(new EjbcaException(message, new RuntimeException()));

        // Then
        assertErrorResponse(response, message, ErrorCode.EJBCA_ERROR);
    }

    @Test
    void testHandleSignatureVerificationExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Signature verification failed";

        // When
        final var response = restExceptionHandler.handleSignatureVerificationException(new SignatureVerificationException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.SIGNATURE_VERIFICATION_ERROR);
    }

    @Test
    void testHandleSignerNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        // -

        // When
        final var response = restExceptionHandler.handleSignerNotFoundException(new SignerNotFoundException("abc"));

        // Then
        assertErrorResponse(response, "Signer with ID abc not found", ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleSignerStateExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Signer is in invalid state for this operation.";

        // When
        final var response = restExceptionHandler.handleSignerStateException(new SignerStateException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.SIGNER_STATE_ERROR);
    }

    @Test
    void testHandleSignerStatusTransitionExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Invalid signer status transition.";

        // When
        final var response = restExceptionHandler.handleSignerStatusTransitionException(new SignerStatusTransitionException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.SIGNER_STATUS_TRANSITION_ERROR);
    }

    private void assertErrorResponse(
            final ResponseEntity<ErrorResponse> responseEntity,
            final String expectedMessage,
            final ErrorCode expectedCode
    ) {
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());

        final var body = responseEntity.getBody();
        assertEquals(ERROR_STATUS, body.status());

        final var responseObject = body.responseObject();
        assertEquals(expectedCode, responseObject.code());
        assertEquals(expectedMessage, responseObject.message());
    }
}
