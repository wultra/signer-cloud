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
package com.wultra.signercloud.server.document;

/**
 * Exception thrown when document signing fails.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public class DocumentSigningException extends RuntimeException {

    public DocumentSigningException(final String message) {
        super(message);
    }

    public DocumentSigningException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
