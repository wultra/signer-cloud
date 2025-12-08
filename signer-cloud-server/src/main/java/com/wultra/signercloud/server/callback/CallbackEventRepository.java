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

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for  {@link CallbackEvent}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
interface CallbackEventRepository extends CrudRepository<CallbackEvent, Long>  {

    /**
     * Finds a list of callback events with the status 'PENDING' where the next call timestamp
     * is earlier than the provided timestamp. Results are ordered by the next call timestamp
     * in descending order and limited to the provided number of rows.
     *
     * @param timestamp the timestamp used to filter callback events with a next call time prior to this value
     * @param limit the maximum number of callback events to return
     * @return a list of pending callback events that match the specified criteria
     * @implSpec {@code FETCH FIRST} is supported by {@code ANSI SQL:2008}.
     */
    @Query("""
            SELECT * FROM "sc_callback_event" c
            WHERE c."status" = 'PENDING'
            AND c."timestamp_next_call" < :timestamp
            ORDER BY c."timestamp_next_call"
            FETCH FIRST :limit ROWS ONLY
            """)
    List<CallbackEvent> findPending(LocalDateTime timestamp, int limit);

    @Modifying
    @Query("""
            DELETE FROM "sc_callback_event" c
            WHERE c."status" = 'COMPLETED'
            AND c."timestamp_delete_after" < :timestamp
            """)
    int deleteCompletedAfterRetentionPeriod(LocalDateTime timestamp);

    @Modifying
    @Query("""
            UPDATE sc_callback_event c
            SET "status" = 'PENDING',
                "timestamp_next_call" = c."timestamp_last_call",
                "timestamp_rerun_after" = null
            WHERE c."status" = 'PROCESSING'
            AND c."timestamp_rerun_after" < :timestamp
            """)
    int updateStaleEventsToPendingState(LocalDateTime timestamp);

    @Modifying
    @Query("""
            UPDATE sc_callback_event c
            SET "status" = 'PENDING',
                "timestamp_next_call" = c."timestamp_last_call",
                "timestamp_rerun_after" = null
            WHERE c."id" = :id
            """)
    void updateEventToPendingState(Long id);
}
