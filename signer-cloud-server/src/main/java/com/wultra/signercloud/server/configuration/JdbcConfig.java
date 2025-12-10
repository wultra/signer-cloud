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
package com.wultra.signercloud.server.configuration;

import com.wultra.signercloud.server.document.DocumentVisualSignatureConverter;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.DialectResolver;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.OracleDialect;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.List;

/**
 * Customization of JDBC.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Configuration
public class JdbcConfig {

    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(
                new DocumentVisualSignatureConverter.VisualSignatureToJsonConverter(),
                new DocumentVisualSignatureConverter.JsonToVisualSignatureConverter()
        ));
    }

    @Bean
    public Dialect dialect(final JdbcOperations jdbcOperations) {
        final var defaultDialect = DialectResolver.getDialect(jdbcOperations);

        return defaultDialect instanceof OracleDialect ? new OracleLowerCaseDialect() : defaultDialect;
    }

    /**
     * Custom Oracle dialect with lower case object identifiers.
     *
     * @author Michal Rozehnal, michal.rozehnal@wultra.com
     */
    public static class OracleLowerCaseDialect extends OracleDialect {

        @Override
        @Nonnull
        public IdentifierProcessing getIdentifierProcessing() {
            return IdentifierProcessing.create(IdentifierProcessing.Quoting.ANSI, IdentifierProcessing.LetterCasing.LOWER_CASE);
        }

    }

}
