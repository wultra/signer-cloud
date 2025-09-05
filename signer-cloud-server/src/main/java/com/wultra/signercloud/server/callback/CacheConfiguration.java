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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache configuration.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Configuration
@Slf4j
class CacheConfiguration {

    /**
     * Configuration of the cache for RestClient used for posting callbacks.
     * {@link Callback#getId()} is used as a cache key.
     *
     * @return Cache for CachedRestClient.
     */
    @Bean
    public LoadingCache<Long, CachedRestClient> callbackUrlRestClientCache(
            @Value("${signer-cloud.server.callback.client-cache.refresh-after-write:5m}") final Duration refreshAfterWrite,
            final CallbackRestClientCacheLoader cacheLoader) {

        logger.info("Initializing Callback REST Client cache with refreshAfterWrite={}", refreshAfterWrite);
        return Caffeine.newBuilder()
                .refreshAfterWrite(refreshAfterWrite)
                .build(cacheLoader);
    }
}
