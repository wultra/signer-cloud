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
 * Wrapper for the result of an operation from the service layer.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public interface Try {
    boolean isSuccess();
    Throwable getError();

    static Try success() {
        return new TrySuccess();
    }

    static Try error(Throwable t) {
        return new TryError(t);
    }

    /**
     * Represents a successful operation.
     */
    final class TrySuccess implements Try {
        private TrySuccess() {}

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Throwable getError() {
            throw new UnsupportedOperationException("No error in success");
        }
    }

    /**
     * Represents a failed operation with an error.
     */
    final class TryError implements Try {
        private final Throwable throwable;

        public TryError(Throwable t) {
            this.throwable = t;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Throwable getError() {
            return throwable;
        }
    }
}
