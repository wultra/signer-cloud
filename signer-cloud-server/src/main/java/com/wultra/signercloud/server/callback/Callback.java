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

package com.wultra.signercloud.server.callback;

import com.wultra.signercloud.server.encryption.EncryptionMode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Callback event data access object.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Table("sc_callback")
@Getter
@Builder(toBuilder = true)
@ToString
public class Callback {

    @Id
    @Sequence("sc_callback_seq")
    private long id;

    private CallbackType callbackType;

    private String callbackUrl;

    /**
     * Callback request authentication. May be encrypted, configured by {@link #encryptionMode}.
     */
    @Builder.Default
    private String authentication = "{}";

    /**
     * Encryption mode of {@link #authentication}.
     */
    private EncryptionMode encryptionMode;

    /**
     * Maximum number of attempts to send the callback.
     */
    private Integer maxAttempts;

    /**
     * Initial backoff before the next sending attempt.
     */
    private Duration initialBackoff;

    /**
     * Duration for which the callback event is stored.
     */
    private Duration retentionPeriod;

    private LocalDateTime timestampCreated;

    private LocalDateTime timestampLastUpdated;
}
