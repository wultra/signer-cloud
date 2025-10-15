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
 * Exception thrown when a {@link Document} is not found in the system.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public class DocumentNotFoundException extends RuntimeException {
    private DocumentNotFoundException(final String message) {
        super(message);
    }

    /**
     * Creates exception for document.
     *
     * @param documentUuid document UUID
     * @return exception instance
     */
    public static DocumentNotFoundException forId(final String documentUuid) {
        final var message = "Document with ID %s not found".formatted(documentUuid);
        return new DocumentNotFoundException(message);
    }
}
