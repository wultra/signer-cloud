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

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Class for storing callback authentication credentials.
 * <p>
 * It is a model for a JSON string.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Getter
@Setter
class CallbackAuthentication {

    /**
     * Certificate authentication credentials object.
     */
    private Certificate certificate;

    /**
     * HTTP basic authentication credentials object.
     */
    private HttpBasic httpBasic;

    /**
     * OAuth2 credentials object.
     */
    private OAuth2 oAuth2;

    /**
     * Inner-class with certificate authentication credentials.
     */
    @Getter
    @Setter
    public static class Certificate {

        private boolean enabled;
        private boolean useCustomKeyStore;
        private String keyStoreLocation;
        @ToString.Exclude
        private String keyStoreContent;
        @ToString.Exclude
        private String keyStorePassword;
        private String keyAlias;
        @ToString.Exclude
        private String keyPassword;
        private boolean useCustomTrustStore;
        private String trustStoreLocation;
        @ToString.Exclude
        private String trustStoreContent;
        @ToString.Exclude
        private String trustStorePassword;
    }

    /**
     * Inner-class with Basic HTTP authentication credentials.
     */
    @Getter
    @Setter
    public static class HttpBasic {

        private boolean enabled;
        private String username;
        @ToString.Exclude
        private String password;
    }

    /**
     * OAuth2 credentials for client credentials flow.
     */
    @Getter
    @Setter
    public static class OAuth2 {

        private boolean enabled;
        private String tokenUri;
        private String clientId;
        @ToString.Exclude
        private String clientSecret;
        private String scope;
    }
}
