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

import com.wultra.core.rest.client.base.DefaultRestClient;
import com.wultra.core.rest.client.base.RestClient;
import com.wultra.core.rest.client.base.RestClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration creating {@link CallbackRestClient}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Slf4j
@Configuration
class CallbackRestClientConfiguration {

    @Bean
    public CallbackRestClient expiredCallbackClient(final CallbackConfigurationProperties callbackConfiguration) throws RestClientException {
        return createCallbackRestClient(CallbackType.EXPIRED, callbackConfiguration);
    }

    @Bean
    public CallbackRestClient renewedCallbackClient(final CallbackConfigurationProperties callbackConfiguration) throws RestClientException {
        return createCallbackRestClient(CallbackType.RENEWED, callbackConfiguration);
    }

    private CallbackRestClient createCallbackRestClient(final CallbackType callbackType, final CallbackConfigurationProperties configuration) throws RestClientException {
        logger.info("Initiating RestClient for callbackType={}", callbackType);
        return CallbackRestClient.builder()
                .restClient(initializeRestClient(callbackType, configuration))
                .timestampCreated(LocalDateTime.now())
                .failureCount(new AtomicInteger(0))
                .timestampLastFailure(new AtomicReference<>(LocalDateTime.MIN))
                .callbackType(callbackType)
                .build();
    }

    private static RestClient initializeRestClient(final CallbackType callbackType, final CallbackConfigurationProperties configuration) throws RestClientException {
        final CallbackConfigurationProperties.CallbackConfiguration callbackConfiguration = configuration.callbackConfigurationFor(callbackType);

        final DefaultRestClient.Builder builder = DefaultRestClient.builder()
                .baseUrl(callbackConfiguration.url())
                .connectionTimeout(configuration.getHttpConnectionTimeout())
                .responseTimeout(configuration.getHttpResponseTimeout())
                .maxIdleTime(configuration.getHttpMaxIdleTime());
        if (Boolean.TRUE.equals(configuration.getHttpProxyEnabled())) {
            builder.proxy()
                    .host(configuration.getHttpProxyHost())
                    .port(configuration.getHttpProxyPort())
                    .username(configuration.getHttpProxyUsername())
                    .password(configuration.getHttpProxyPassword());
        }

        final CallbackAuthentication authentication = callbackConfiguration.authentication();
        configureAuthentication(authentication, builder);

        return builder.build();
    }

    private static void configureAuthentication(final CallbackAuthentication authentication, final DefaultRestClient.Builder builder) {
        configureCertificateAuth(authentication, builder);
        configureBasicAuth(authentication, builder);
        configureOAuth2(authentication, builder);
    }

    private static void configureOAuth2(final CallbackAuthentication authentication, final DefaultRestClient.Builder builder) {
        if (authentication == null) {
            return;
        }

        final CallbackAuthentication.OAuth2 oAuth2Config = authentication.getOAuth2();
        if (oAuth2Config != null && oAuth2Config.isEnabled()) {
            builder.filter(configureOAuth2ExchangeFilter(oAuth2Config));
        }
    }

    private static void configureBasicAuth(final CallbackAuthentication authentication, final DefaultRestClient.Builder builder) {
        if (authentication == null) {
            return;
        }

        final CallbackAuthentication.HttpBasic httpBasicAuth = authentication.getHttpBasic();
        if (httpBasicAuth != null && httpBasicAuth.isEnabled()) {
            builder.httpBasicAuth()
                    .username(httpBasicAuth.getUsername())
                    .password(httpBasicAuth.getPassword());
        }
    }

    private static void configureCertificateAuth(final CallbackAuthentication authentication, final DefaultRestClient.Builder builder) {
        if (authentication == null) {
            return;
        }

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
    }

    private static ServerOAuth2AuthorizedClientExchangeFilterFunction configureOAuth2ExchangeFilter(final CallbackAuthentication.OAuth2 config) {
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
}
