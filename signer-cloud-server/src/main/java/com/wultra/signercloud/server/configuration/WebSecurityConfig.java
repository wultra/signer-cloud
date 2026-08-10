/*
 *
 *  * Signer Cloud
 *  * Copyright (C) 2025 Wultra s.r.o.
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as published
 *  * by the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU Affero General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Affero General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.wultra.signercloud.server.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Web security configuration.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class WebSecurityConfig {

    @Value("${signer-cloud.server.security.auth.type}")
    private AuthType authType;

    /**
     * Configure the security filter chain.
     *
     * @param http HTTP configuration.
     * @return Security filter chain.
     * @throws Exception In case a configuration error occurs.
     */
    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        if (authType == AuthType.BASIC_HTTP) {
            logger.info("Initializing HTTP basic authentication.");
            http.httpBasic(withDefaults());
        } else if (authType == AuthType.OAUTH2) {
            logger.info("Initializing OAuth 2.0 authentication.");
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        }

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-resources/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/signature/**",
                                "/",
                                "/resources/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                ).build();
    }

    private enum AuthType {
        BASIC_HTTP,
        OAUTH2
    }
}
