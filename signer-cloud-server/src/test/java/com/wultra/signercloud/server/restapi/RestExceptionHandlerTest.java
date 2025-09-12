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
    void testHandleSignerNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Signer not found: dummyExternalSignerId";

        // When
        final var response = restExceptionHandler.handleSignerNotFoundException(new SignerNotFoundException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleDocumentUploadExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Unsupported content callbackType: image/jpeg";

        // When
        final var response = restExceptionHandler.handleDocumentUploadException(new DocumentUploadException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.ERROR_GENERIC);
    }

    @Test
    void testHandleDocumentNotFoundExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Document not found for document ID: 123";

        // When
        final var response = restExceptionHandler.handleDocumentNotFoundException(new DocumentNotFoundException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.ERROR_RESOURCE_NOT_FOUND);
    }

    @Test
    void testHandleSignDocumentExceptionWhenExceptionIsHandledThenCorrectResponseIsReturned() {
        // Given
        final var message = "Document signing timeout exceeded";

        // When
        final var response = restExceptionHandler.handleSignDocumentException(new SignDocumentException(message));

        // Then
        assertErrorResponse(response, message, ErrorCode.ERROR_GENERIC);
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
