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

    Optional<Signer> findByExternalSignerId(String externalSignerId);

    /**
     * Find signers for expiration.
     *
     * @param limit Limit of signers to return.
     * @return List of signers.
     * @apiNote Internal API, use {@link #markAsExpired(int)} instead.
     * @implSpec {@code FETCH FIRST} is supported by {@code ANSI SQL:2008}.
     */
    @Query("""
        SELECT * FROM sc_signer WHERE status = 'ACTIVE' AND timestamp_certificate_expiration < NOW()
                ORDER BY timestamp_certificate_expiration
                FETCH FIRST :limit ROWS ONLY
        """)
    List<Signer> findForExpiration(int limit);

    /**
     * Find signers for renewal.
     *
     * @param expirationThreshold Limit signers to this expiration threshold.
     * @param limit Limit of signers to return.
     * @return List of signers.
     * @implSpec {@code FETCH FIRST} is supported by {@code ANSI SQL:2008}.
     */
    @Query("""
        SELECT * FROM sc_signer WHERE status = 'ACTIVE' AND timestamp_certificate_expiration BETWEEN NOW() AND :expirationThreshold
                ORDER BY timestamp_certificate_expiration
                FETCH FIRST :limit ROWS ONLY
        """)
    List<Signer> findForRenewal(Instant expirationThreshold, int limit);

    /**
     * Marks signers as expired.
     * <p>
     * The signers are marked as expired if they are active and their certificate expiration date is before the current time.
     *
     * @param ids Signer IDs to mark as expired.
     * @apiNote Internal API, use {@link #markAsExpired(int)} instead.
     */
    @Modifying
    @Query("UPDATE sc_signer SET timestamp_last_updated = NOW(), status = 'EXPIRED' WHERE id IN (:ids)")
    void markAsExpired(List<Long> ids);

    /**
     * Marks signers as expired.
     * <p>
     * The signers are marked as expired if they are active and their certificate expiration date is before the current time.
     *
     * @param limit Limit of signers to mark as expired in a single query.
     * @return List of signers marked as expired.
     * @implSpec Unfortunately, usage of Common Table Expressions is limited to PostgreSQL only.
     */
    default List<Signer> markAsExpired(int limit) {
        final List<Signer> signers = findForExpiration(limit);
        final List<Long> ids = signers.stream()
                .map(Signer::getId)
                .toList();
        markAsExpired(ids);
        return signers;
    }

    Optional<Signer> findById(AggregateReference<Signer, Long> reference);
}
