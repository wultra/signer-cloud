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

import java.util.function.Supplier;

/**
 * Wrapper for the result of an operation from the service layer.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public sealed interface Try<T> permits Try.TryError, Try.TrySuccess{
    boolean isSuccess();
    RuntimeException getError();
    T getResponse();

    static <T> Try<T> success() {
        return new TrySuccess<>(null);
    }

    static <T> Try<T> success(final T result) {
        return new TrySuccess<>(result);
    }

    static <T> Try<T> error(final RuntimeException e) {
        return new TryError<>(e);
    }

    static <T> Try<T> execute(final Supplier<T> supplier) {
        try {
            final var result = supplier.get();
            return Try.success(result);
        } catch (final RuntimeException e) {
            return Try.error(e);
        }
    }

    static <T> Try<T> execute(final Runnable runnable) {
        try {
            runnable.run();
            return Try.success();
        } catch (final RuntimeException e) {
            return Try.error(e);
        }
    }

    /**
     * Represents a successful operation.
     */
    record TrySuccess<T>(T response) implements Try<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public RuntimeException getError() {
            throw new UnsupportedOperationException("No error in success");
        }

        @Override
        public T getResponse() {
            return response;
        }
    }

    /**
     * Represents a failed operation with an error.
     */
    record TryError<T>(RuntimeException exception) implements Try<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public RuntimeException getError() {
            return exception;
        }

        @Override
        public T getResponse() {
            throw new UnsupportedOperationException("No result in error");
        }
    }
}
