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
package com.wultra.signercloud.server.signer;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * Exception thrown by Certificate Authority.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Getter
public class CertificateAuthorityException extends RuntimeException {

    private final HttpStatusCode httpStatus;

    public CertificateAuthorityException(final String message, final Throwable cause, final HttpStatusCode httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
