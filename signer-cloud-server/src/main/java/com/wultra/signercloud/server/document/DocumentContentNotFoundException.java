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
 * Exception thrown when a {@link DocumentContent} is not found in the system.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public class DocumentContentNotFoundException extends RuntimeException {
    private DocumentContentNotFoundException(final String message) {
        super(message);
    }

    /**
     * Creates exception for document content.
     *
     * @param documentUuid Document UUID
     * @return Exception instance
     */
    public static DocumentContentNotFoundException forId(final String documentUuid) {
        final var message = "Content for document ID %s not found".formatted(documentUuid);
        return new DocumentContentNotFoundException(message);
    }
}
