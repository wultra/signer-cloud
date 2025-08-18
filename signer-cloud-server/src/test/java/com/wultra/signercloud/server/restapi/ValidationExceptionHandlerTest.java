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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ValidationExceptionHandler}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class ValidationExceptionHandlerTest {

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
    private ValidationExceptionHandler validationExceptionHandler;

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
        final var response = validationExceptionHandler.handleValidationException(exception);

        // Then
        assertErrorResponse(response.getBody(), EXPECTED_SPECIFIC_MESSAGE);
    }

    @Test
    void testHandleValidationExceptionWhenInvalidFieldIsNotSpecifiedThenGenericMessageIsReturned() {
        // Given
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // When
        final var response = validationExceptionHandler.handleValidationException(exception);

        // Then
        assertErrorResponse(response.getBody(), EXPECTED_GENERIC_MESSAGE);
    }

    private void assertErrorResponse(ErrorResponse errorResponse, String expectedMessage) {
        assertEquals(ERROR_STATUS, errorResponse.status());

        final var responseObject = errorResponse.responseObject();
        assertEquals(ErrorCode.ERROR_GENERIC, responseObject.code());
        assertEquals(expectedMessage, responseObject.message());
    }
}
