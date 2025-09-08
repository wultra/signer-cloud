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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.wultra.core.rest.client.base.DefaultRestClient;
import com.wultra.core.rest.client.base.RestClient;
import com.wultra.core.rest.client.base.RestClientException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Specialization of {@link CacheLoader} for {@link CachedRestClient}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@AllArgsConstructor
@Slf4j
@Component
class CallbackRestClientCacheLoader implements CacheLoader<Long, CachedRestClient> {

    private final ObjectMapper objectMapper;

    private final CallbackConfigurationProperties callbackConfiguration;

    private final CallbackRepository callbackRepository;

    @Override
    public @Nullable CachedRestClient load(final Long callbackId) throws RestClientException {
        logger.debug("Loading RestClient for Callback; id={}", callbackId);

        final Optional<Callback> callback = callbackRepository.findById(callbackId);
        if (callback.isEmpty()) {
            logger.warn("Callback is not available; id={}", callbackId);
            return null;
        }

        return createNewCachedRestClient(callback.get());
    }

    @Override
    public @Nullable CachedRestClient reload(final Long callbackId, final CachedRestClient cachedRestClient) throws RestClientException {
        logger.debug("Checking cached RestClient for Callback; id={}", callbackId);

        final Optional<Callback> callback = callbackRepository.findById(callbackId);
        if (callback.isEmpty()) {
            logger.warn("Callback is not available anymore; id={}", callbackId);
            return null;
        }

        final LocalDateTime lastEntityUpdate = callback.get().getTimestampLastUpdated();
        if (lastEntityUpdate != null && lastEntityUpdate.isAfter(cachedRestClient.timestampCreated())) {
            return createNewCachedRestClient(callback.get());
        }

        logger.debug("Keeping the RestClient in cache for Callback; id={}", callbackId);
        return cachedRestClient;
    }

    private CachedRestClient createNewCachedRestClient(final Callback callback) throws RestClientException {
        return CachedRestClient.builder()
                .restClient(initializeRestClient(callback))
                .timestampCreated(LocalDateTime.now())
                .failureCount(0)
                .timestampLastFailure(LocalDateTime.MIN)
                .callback(callback)
                .build();
    }

    /**
     * Initialize a REST client instance and configure it based on client configuration.
     *
     * @param callback Callback entity.
     * @throws RestClientException In case the REST Client initialization fails.
     */
    private RestClient initializeRestClient(final Callback callback) throws RestClientException {
        logger.debug("Initiating a new RestClient for callback; id={}", callback.getId());
        final DefaultRestClient.Builder builder = DefaultRestClient.builder();
        builder.connectionTimeout(callbackConfiguration.getHttpConnectionTimeout());
        builder.responseTimeout(callbackConfiguration.getHttpResponseTimeout());
        builder.maxIdleTime(callbackConfiguration.getHttpMaxIdleTime());
        if (Boolean.TRUE.equals(callbackConfiguration.getHttpProxyEnabled())) {
            builder.proxy()
                    .host(callbackConfiguration.getHttpProxyHost())
                    .port(callbackConfiguration.getHttpProxyPort())
                    .username(callbackConfiguration.getHttpProxyUsername())
                    .password(callbackConfiguration.getHttpProxyPassword());
        }
        final CallbackAuthentication authentication = convert(callback.getAuthentication());
        final CallbackAuthentication.Certificate certificateAuth = authentication.getCertificate();
        if (certificateAuth != null && certificateAuth.isEnabled()) {
            final byte[] keyStoreBytes = StringUtils.hasText(certificateAuth.getKeyStoreContent())
                    ? Base64.getDecoder().decode(certificateAuth.getKeyStoreContent())
                    : null;
            final byte[] trustStoreBytes = StringUtils.hasText(certificateAuth.getTrustStoreContent())
                    ? Base64.getDecoder().decode(certificateAuth.getTrustStoreContent())
                    : null;
            final DefaultRestClient.CertificateAuthBuilder certificateAuthBuilder = builder.certificateAuth();
            if (certificateAuth.isUseCustomKeyStore()) {
                certificateAuthBuilder.enableCustomKeyStore()
                        .keyStoreLocation(certificateAuth.getKeyStoreLocation())
                        .keyStoreBytes(keyStoreBytes)
                        .keyStorePassword(certificateAuth.getKeyStorePassword())
                        .keyAlias(certificateAuth.getKeyAlias())
                        .keyPassword(certificateAuth.getKeyPassword());
            }
            if (certificateAuth.isUseCustomTrustStore()) {
                certificateAuthBuilder.enableCustomTruststore()
                        .trustStoreLocation(certificateAuth.getTrustStoreLocation())
                        .trustStoreBytes(trustStoreBytes)
                        .trustStorePassword(certificateAuth.getTrustStorePassword());
            }
        }
        final CallbackAuthentication.HttpBasic httpBasicAuth = authentication.getHttpBasic();
        if (httpBasicAuth != null && httpBasicAuth.isEnabled()) {
            builder.httpBasicAuth()
                    .username(httpBasicAuth.getUsername())
                    .password(httpBasicAuth.getPassword());
        }

        final CallbackAuthentication.OAuth2 oAuth2Config = authentication.getOAuth2();
        if (oAuth2Config != null && oAuth2Config.isEnabled()) {
            builder.filter(configureOAuth2ExchangeFilter(oAuth2Config, callback.getId()));
        }

        return builder.build();
    }

    private static ServerOAuth2AuthorizedClientExchangeFilterFunction configureOAuth2ExchangeFilter(final CallbackAuthentication.OAuth2 config, final Long callbackId) {
        logger.debug("Configuring OAuth2 for callback ID: {}", callbackId);
        final String registrationId = "callback OAuth2";
        final ClientRegistration clientRegistration = ClientRegistration.withRegistrationId(registrationId)
                .tokenUri(config.getTokenUri())
                .clientId(config.getClientId())
                .clientSecret(config.getClientSecret())
                .scope(config.getScope())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        final ReactiveClientRegistrationRepository clientRegistrations = new InMemoryReactiveClientRegistrationRepository(clientRegistration);
        final ReactiveOAuth2AuthorizedClientService clientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);

        final AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrations, clientService);
        final ServerOAuth2AuthorizedClientExchangeFilterFunction oAuth2ExchangeFilterFunction = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oAuth2ExchangeFilterFunction.setDefaultClientRegistrationId(registrationId);
        return oAuth2ExchangeFilterFunction;
    }

    // TODO (racansky, 2025-09-05) implement decryption
    private CallbackAuthentication convert(String authentication) {
        if (authentication == null) {
            return new CallbackAuthentication();
        }
        try {
            return objectMapper.readValue(authentication, CallbackAuthentication.class);
        } catch (IOException e) {
            logger.error("Unable to parse JSON payload", e);
            return new CallbackAuthentication();
        }
    }
}
