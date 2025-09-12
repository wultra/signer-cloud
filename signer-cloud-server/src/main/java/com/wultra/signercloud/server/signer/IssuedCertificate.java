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

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Data access object for {@code sc_issued_certificate} table.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Getter
@Builder(toBuilder = true)
@Table("sc_issued_certificate")
public class IssuedCertificate {

    @Id
    @Sequence("sc_issued_certificate_seq")
    private long id;

    /**
     * {@link Signer#id}
     */
    private long signerId;

    private Instant timestampCreated;

    private String serialNumber;

    private String issuerDn;

    private Instant timestampCertificateExpiration;

}
