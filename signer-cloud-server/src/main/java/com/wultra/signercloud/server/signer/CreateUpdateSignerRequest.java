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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * REST body for creating a new {@link Signer}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
record CreateUpdateSignerRequest(
        @Schema(
                description = "Unique identifier of the signer in the external system.",
                example = "756419e1-1d85-4172-815d-d8653ecd3a89"
        )
        @NotBlank String signerId,

        @Schema(
                description = "Identifier of the user owning the signer.",
                example = "demo-user"
        )
        @NotBlank String userId,

        @Schema(
                description = "PEM encoded PKCS10 CSR, one line, line endings '\\n'",
                example = "-----BEGIN CERTIFICATE REQUEST-----\nMIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT4i0arrfMJ+3mkipWWQRY33l1uoLWUttTzTEselqaNxk+GNLnQy9GW7KBaB9RZ4LhreWEJMDfjO1prlCFFxxgmoAAwCgYIKoZIzj0EAwIDSAAwRQIhAOTV4jyWM0hIg3iRT8Xh//JGmEjFgN+wVJiYRI2Zl5nzAiAeoKKXtYzzU5VxqrqkbylVSPdSzgsetPvt/arRNQhNfw==\n-----END CERTIFICATE REQUEST-----\n"
        )
        @NotBlank String csr
) {
}