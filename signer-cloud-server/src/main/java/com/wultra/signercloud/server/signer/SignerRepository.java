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

import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing a {@link Signer}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public interface SignerRepository extends CrudRepository<Signer, Long> {

    Optional<Signer> findByExternalSignerId(final String externalSignerId);

    /**
     * Find signers for expiration.
     *
     * @param limit Limit of signers to return.
     * @param now Current time.
     * @return List of signers.
     * @implSpec {@code FETCH FIRST} is supported by {@code ANSI SQL:2008}.
     */
    @Query("""
        SELECT * FROM "sc_signer" WHERE "status" = 'ACTIVE' AND "timestamp_certificate_expiration" < :now
                ORDER BY "timestamp_certificate_expiration"
                FETCH FIRST :limit ROWS ONLY
        """)
    List<Signer> findForExpiration(final int limit, final Instant now);

    /**
     * Find signers for renewal.
     *
     * @param expirationThreshold Limit signers to this expiration threshold.
     * @param limit Limit of signers to return.
     * @param now Current time.
     * @return List of signers.
     * @implSpec {@code FETCH FIRST} is supported by {@code ANSI SQL:2008}.
     */
    @Query("""
        SELECT * FROM "sc_signer" WHERE "status" = 'ACTIVE' AND "timestamp_certificate_expiration" BETWEEN :now AND :expirationThreshold
                ORDER BY "timestamp_certificate_expiration"
                FETCH FIRST :limit ROWS ONLY
        """)
    List<Signer> findForRenewal(final Instant expirationThreshold, final int limit, final Instant now);

    /**
     * Marks signers as expired.
     * <p>
     * The signers are marked as expired if they are active and their certificate expiration date is before the current time.
     *
     * @param ids Signer IDs to mark as expired.
     * @param now Current time.
     */
    @Modifying
    @Query("""
        UPDATE "sc_signer" SET "timestamp_last_updated" = :now, "status" = 'EXPIRED' WHERE "id" IN (:ids)
        """)
    void markAsExpired(final List<Long> ids, final Instant now);

    /**
     * Find signer by aggregate reference.
     *
     * @param reference Aggregate reference.
     * @return Optional signer.
     */
    Optional<Signer> findById(final AggregateReference<Signer, Long> reference);
}
