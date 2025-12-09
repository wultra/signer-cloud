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

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for accessing a {@link IssuedCertificateMetadata}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
interface IssuedCertificateMetadataRepository extends CrudRepository<IssuedCertificateMetadata, Long> {

    /**
     * Finds all active issued certificates for the signer.
     *
     * @param signerId id of Signer to be revoked
     * @param now Current time
     * @return list of certificates metadata
     */
    @Query("""
        SELECT * FROM "sc_issued_certificate_metadata"
        WHERE "signer_id" = :signerId AND "timestamp_certificate_expiration" > :now AND "status" != 'REVOKED'
        """)
    List<IssuedCertificateMetadata> findForRevocation(final long signerId, final Instant now);
}
