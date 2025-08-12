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
package com.wultra.signercloud.server.ejbca;

import lombok.Builder;

/**
 * Parameter object for revoking a certificate in EJBCA, {@link EjbcaRestClient#revokeCertificate(CertificateRevocationParameters)}
 *
 * @param issuerDn Subject DN of the issuing CA.
 * @param certificateSerialNumber The hex serial number (without prefix, e.g. '00') of the certificate to be revoked
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Builder
record CertificateRevocationParameters(
        String issuerDn,
        String certificateSerialNumber
) {
}
