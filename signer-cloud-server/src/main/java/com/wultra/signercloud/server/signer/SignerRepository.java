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
     * Find signer IDs for expiration.
     *
     * @param now Current time.
     * @return List of signer IDs.
     * @apiNote Internal API, use {@link #markAsExpired(Instant, int)} instead.
     * @implSpec {@code FETCH FIRST} is supported by {@code ANSI SQL:2008}.
     */
    @Query("""
        SELECT id FROM sc_signer WHERE status = 'ACTIVE' AND timestamp_certificate_expiration < :now
                ORDER BY timestamp_certificate_expiration
                FETCH FIRST :limit ROWS ONLY
        """)
    List<Long> findIdsForExpiration(Instant now, int limit);


    /**
     * Marks signers as expired.
     * <p>
     * The signers are marked as expired if they are active and their certificate expiration date is before the current time.
     *
     * @param ids Signer IDs to mark as expired.
     * @param now Current time.
     * @apiNote Internal API, use {@link #markAsExpired(Instant, int)} instead.
     */
    @Modifying
    @Query("UPDATE sc_signer SET timestamp_last_updated = :now, status = 'EXPIRED' WHERE id IN (:ids)")
    void markAsExpired(List<Long> ids, Instant now);

    /**
     * Marks signers as expired.
     * <p>
     * The signers are marked as expired if they are active and their certificate expiration date is before the current time.
     *
     * @param now Current time.
     * @param limit Limit of signers to mark as expired in a single query.
     * @return List of signer IDs marked as expired.
     * @implSpec Unfortunately, usage of Common Table Expressions is limited to PostgreSQL only.
     */
    default List<Long> markAsExpired(Instant now, int limit) {
        final List<Long> ids = findIdsForExpiration(now, limit);
        markAsExpired(ids, now);
        return ids;
    }
}
