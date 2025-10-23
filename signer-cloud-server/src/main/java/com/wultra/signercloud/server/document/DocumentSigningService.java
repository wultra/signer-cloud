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
package com.wultra.signercloud.server.document;

import com.wultra.signercloud.server.configuration.PAdESConfigurationProperties;
import com.wultra.signercloud.server.signer.CertificateProcessingException;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.utils.CertificateUtils;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.AnnotationBox;
import eu.europa.esig.dss.pdf.PdfSignatureFieldPositionChecker;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDocumentReader;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.*;

/**
 * Document signing service handling PAdES signatures.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
@AllArgsConstructor
class DocumentSigningService {

    private final PAdESService padesService;
    private final PAdESConfigurationProperties pAdESConfigurationProperties;
    private final DocumentVisualSignatureService documentVisualSignatureService;
    private final PdfSignatureFieldPositionChecker visualSignatureChecker;

    /**
     * Computes {@link eu.europa.esig.dss.model.ToBeSigned} for given document and signature parameters.
     *
     * @param content document content to be signed
     * @param signer the signer
     * @param timestampSigned signature timestamp
     * @param visualSignature optional visual signature parameters
     * @return {@link eu.europa.esig.dss.model.ToBeSigned} as base64 string
     */
    String computeToBeSigned(
            final byte[] content,
            final Signer signer,
            final Instant timestampSigned,
            final DocumentVisualSignature visualSignature
    ) {
        final var certificate = convertCertificate(signer);
        final var certificateChain = convertCertificateChain(signer);

        final var dssDocument = new InMemoryDocument(content);

        final var signatureParams = createSignatureParameters(
                certificate,
                certificateChain,
                timestampSigned,
                pAdESConfigurationProperties.getSignatureLevel(),
                visualSignature,
                dssDocument
        );

        // A newly created PAdESSignatureParameters instance has its imageParameters field set to null.
        // However, if the getImageParameters() method is called, a new instance of SignatureImageParameters
        // with default values is created and assigned to the imageParameters field (it is no longer null after the call).
        // Therefore, we check whether (visualSignature != null) instead. A check like
        // (signatureParams.getImageParameters() != null) would always evaluate to true.
        if (visualSignature != null) {
            verifyVisualSignature(dssDocument, signatureParams.getImageParameters());
        }

        final var toSignBytes = padesService.getDataToSign(dssDocument, signatureParams);

        return Base64.getEncoder().encodeToString(toSignBytes.getBytes());
    }

    private void verifyVisualSignature(
            final DSSDocument dssDocument,
            final SignatureImageParameters signatureImageParameters
    ) {
        try (final var pdfReader = new PdfBoxDocumentReader(dssDocument)) {
            final var fieldParams = signatureImageParameters.getFieldParameters();
            visualSignatureChecker.assertSignatureFieldPositionValid(pdfReader, new AnnotationBox(fieldParams), fieldParams.getPage());
        } catch (final IOException | RuntimeException e) {
            throw new DocumentVisualSignatureException(e.getMessage(), e);
        }
    }

    private static CertificateToken convertCertificate(final Signer signer) {
        try {
            final var x509Certificate = signer.getX509Certificate();
            return new CertificateToken(x509Certificate);
        } catch (final CertificateException e) {
            throw new CertificateProcessingException("Exception when processing certificate: " + e.getMessage(), e);
        }
    }

    private static List<CertificateToken> convertCertificateChain(final Signer signer) {
        try {
            final var chain = new ArrayList<CertificateToken>();

            for (final var base64Certificate : signer.getCertificateChain()) {
                final var x509Certificate = CertificateUtils.base64ToX509Certificate(base64Certificate);
                chain.add(new CertificateToken(x509Certificate));
            }

            return chain;
        } catch (final CertificateException e) {
            throw new CertificateProcessingException("Exception when processing certificate chain: " + e.getMessage(), e);
        }
    }

    /**
     * Sign document with given signature.
     *
     * For successful signing, exactly same parameters must be used as for computing the hash via {@link #computeToBeSigned(byte[], Signer, Instant, DocumentVisualSignature)}.
     * Any difference will cause different hash computation and the passed signature will be invalid.
     *
     * @param signer the signer
     * @param signatureBase64 signature of {@link eu.europa.esig.dss.model.ToBeSigned}
     * @param documentBytes document content to be signed
     * @param timestampSigned signature timestamp
     * @param requestedSignatureLevel requested signature level, or null to use default from configuration
     * @param visualSignature optional visual signature parameters
     * @return signed document
     */
    SignedDocument sign(
            final Signer signer,
            final String signatureBase64,
            final byte[] documentBytes,
            final Instant timestampSigned,
            final DocumentSignatureLevel requestedSignatureLevel,
            final DocumentVisualSignature visualSignature) {

        final var certificate = convertCertificate(signer);
        final var certificateChain = convertCertificateChain(signer);

        final var unsignedDocument = new InMemoryDocument(documentBytes);

        final var signatureLevel = resolveDocumentSignatureLevel(requestedSignatureLevel);

        final var signatureParams = createSignatureParameters(
                certificate,
                certificateChain,
                timestampSigned,
                signatureLevel,
                visualSignature,
                unsignedDocument
        );

        final var documentHash = padesService.getDataToSign(unsignedDocument, signatureParams);

        final var signatureBytes = Base64.getDecoder().decode(signatureBase64);
        final var signatureValue = new SignatureValue(pAdESConfigurationProperties.getSignatureAlgorithm(), signatureBytes);

        final var isSignatureValid = padesService.isValidSignatureValue(documentHash, signatureValue, certificate);
        if (!isSignatureValid) {
            throw new DocumentInvalidSignatureException("Invalid signature");
        }

        final var signedDocument = padesService.signDocument(unsignedDocument, signatureParams, signatureValue);
        final var signedContent = readSignedDocumentBytes(signedDocument);

        return new SignedDocument(signedContent, signatureLevel);
    }

    private DocumentSignatureLevel resolveDocumentSignatureLevel(final DocumentSignatureLevel requestedSignatureLevel) {
        final var signatureLevel = Optional.ofNullable(requestedSignatureLevel)
                .orElse(pAdESConfigurationProperties.getSignatureLevel());

        if (signatureLevel == DocumentSignatureLevel.PADES_B_T && StringUtils.isEmpty(pAdESConfigurationProperties.getTsaUrl())) {
            throw new TimestampAuthorityException("TSA URL not set in configuration");
        }

        return signatureLevel;
    }

    /**
     * Creates signature parameters with a deterministic context.
     *
     * This method always creates a new instance. These parameters affect the {@link eu.europa.esig.dss.model.ToBeSigned}
     * value returned by {@link PAdESService#getDataToSign(DSSDocument, PAdESSignatureParameters)}
     * and computed internally in {@link PAdESService#signDocument(DSSDocument, PAdESSignatureParameters, SignatureValue)}.
     * Therefore, it is important that the same context is used for the same signature, so that {@link eu.europa.esig.dss.model.ToBeSigned}
     * is computed deterministically (i.e., the value is always the same).
     * Pay attention to the {@link eu.europa.esig.dss.model.BLevelParameters#setSigningDate(Date)} in {@link PAdESSignatureParameters#bLevel}. From this value, the
     * {@code deterministicId} in {@link PAdESSignatureParameters#getContext()} is computed.
     * If {@code signingDate} is not explicitly set, the current machine time is used by default, and each call
     * to obtain {@link eu.europa.esig.dss.model.ToBeSigned} will produce a different value.
     * A list of signed parameters is available <a href="https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html#_table_with_all_attributes_per_format_and_class">here</a>.
     *
     * @param certificateToken signature certificate
     * @param certificateChain signature certificate chain
     * @param timestampSigned timestamp in UTC set as {@code signingDate} in order to create deterministic context
     * @return parameters with deterministic context
     */
    private PAdESSignatureParameters createSignatureParameters(
            final CertificateToken certificateToken,
            final List<CertificateToken> certificateChain,
            final Instant timestampSigned,
            final DocumentSignatureLevel documentSignatureLevel,
            final DocumentVisualSignature visualSignature,
            final DSSDocument dssDocument
    ) {
        final var params = new PAdESSignatureParameters();
        params.setDigestAlgorithm(pAdESConfigurationProperties.getSignatureAlgorithm().getDigestAlgorithm());
        params.setSigningCertificate(certificateToken);
        params.setCertificateChain(certificateChain);

        final var signatureLevel = switch (documentSignatureLevel) {
            case PADES_B_B -> SignatureLevel.PAdES_BASELINE_B;
            case PADES_B_T -> SignatureLevel.PAdES_BASELINE_T;
        };

        params.setSignatureLevel(signatureLevel);

        Optional.ofNullable(visualSignature)
                .map(vs -> documentVisualSignatureService.createVisualSignature(vs, dssDocument))
                .ifPresent(params::setImageParameters);

        params.bLevel().setSigningDate(Date.from(timestampSigned));
        params.setSigningTimeZone(TimeZone.getTimeZone("UTC"));

        return params;
    }

    private static byte[] readSignedDocumentBytes(final DSSDocument signedDocument) {
        try (final var stream = signedDocument.openStream()) {
            return stream.readAllBytes();
        } catch (final IOException e) {
            throw new DocumentSigningException("Exception when reading bytes of signed document: " + e.getMessage(), e);
        }
    }

    record SignedDocument(
            byte[] content,
            DocumentSignatureLevel signatureLevel
    ) {}

}
