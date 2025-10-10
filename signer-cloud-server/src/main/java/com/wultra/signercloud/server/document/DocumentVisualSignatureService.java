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

import eu.europa.esig.dss.enumerations.*;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.pades.DSSFont;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDocumentReader;
import eu.europa.esig.dss.pdf.pdfbox.visible.PdfBoxNativeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * Service for visual signature.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
class DocumentVisualSignatureService {

    /**
     * Creates DSS PAdES parameters from {@link DocumentVisualSignature}.
     *
     * @param visualSignature visual signature definition
     * @param dssDocument the document in which the signature will be placed
     * @return DSS PAdES parameters
     * @throws DocumentVisualSignatureException when visual signature is invalid
     */
    SignatureImageParameters createVisualSignature(
            final DocumentVisualSignature visualSignature,
            final DSSDocument dssDocument
    ) {
        try {
            return createSignatureImageParameters(visualSignature, dssDocument);
        } catch (final RuntimeException e) {
            throw new DocumentVisualSignatureException("Issue when creating visual signature", e);
        }
    }

    private static SignatureImageParameters createSignatureImageParameters(
            final DocumentVisualSignature visualSignature,
            final DSSDocument dssDocument
    ) {
        final var params = new SignatureImageParameters();

        Optional.ofNullable(visualSignature.image())
                .map(imageBase64 -> new InMemoryDocument(Base64.getDecoder().decode(imageBase64)))
                .ifPresent(params::setImage);

        Optional.ofNullable(visualSignature.dpi())
                .ifPresent(params::setDpi);

        Optional.ofNullable(visualSignature.alignmentHorizontal())
                .map(DocumentVisualSignatureService::convertAlignmentHorizontal)
                .ifPresent(params::setAlignmentHorizontal);

        Optional.ofNullable(visualSignature.alignmentVertical())
                .map(DocumentVisualSignatureService::convertAlignmentVertical)
                .ifPresent(params::setAlignmentVertical);

        Optional.ofNullable(visualSignature.zoom())
                .ifPresent(params::setZoom);

        Optional.ofNullable(visualSignature.backgroundColor())
                .map(Color::decode)
                .ifPresent(params::setBackgroundColor);

        Optional.ofNullable(visualSignature.imageScaling())
                .map(DocumentVisualSignatureService::convertImageScaling)
                .ifPresent(params::setImageScaling);

        Optional.ofNullable(visualSignature.fieldParameters())
                .map(DocumentVisualSignatureService::createSignatureFieldParameters)
                .ifPresent(params::setFieldParameters);

        Optional.ofNullable(visualSignature.textParameters())
                .map(textParams -> createTextParameters(textParams, dssDocument))
                .ifPresent(params::setTextParameters);

        return params;
    }

    private static VisualSignatureAlignmentHorizontal convertAlignmentHorizontal(
            final DocumentVisualSignature.AlignmentHorizontal alignmentHorizontal
    ) {
        return switch (alignmentHorizontal) {
            case NONE -> VisualSignatureAlignmentHorizontal.NONE;
            case LEFT -> VisualSignatureAlignmentHorizontal.LEFT;
            case CENTER -> VisualSignatureAlignmentHorizontal.CENTER;
            case RIGHT -> VisualSignatureAlignmentHorizontal.RIGHT;
        };
    }

    private static VisualSignatureAlignmentVertical convertAlignmentVertical(
            final DocumentVisualSignature.AlignmentVertical alignmentVertical
    ) {
        return switch (alignmentVertical) {
            case NONE -> VisualSignatureAlignmentVertical.NONE;
            case TOP -> VisualSignatureAlignmentVertical.TOP;
            case MIDDLE -> VisualSignatureAlignmentVertical.MIDDLE;
            case BOTTOM -> VisualSignatureAlignmentVertical.BOTTOM;
        };
    }

    private static ImageScaling convertImageScaling(final DocumentVisualSignature.ImageScaling imageScaling) {
        return switch (imageScaling) {
            case STRETCH -> ImageScaling.STRETCH;
            case ZOOM_AND_CENTER -> ImageScaling.ZOOM_AND_CENTER;
            case CENTER -> ImageScaling.CENTER;
        };
    }

    private static SignatureFieldParameters createSignatureFieldParameters(
            final DocumentVisualSignature.FieldParameters fieldParameters
    ) {
        final var params = new SignatureFieldParameters();

        Optional.ofNullable(fieldParameters.fieldId())
                .ifPresent(params::setFieldId);

        Optional.ofNullable(fieldParameters.page())
                .ifPresent(params::setPage);

        Optional.ofNullable(fieldParameters.originX())
                .ifPresent(params::setOriginX);

        Optional.ofNullable(fieldParameters.originY())
                .ifPresent(params::setOriginY);

        Optional.ofNullable(fieldParameters.width())
                .ifPresent(params::setWidth);

        Optional.ofNullable(fieldParameters.height())
                .ifPresent(params::setHeight);

        Optional.ofNullable(fieldParameters.rotation())
                .map(DocumentVisualSignatureService::convertRotation)
                .ifPresent(params::setRotation);

        return params;
    }

    private static VisualSignatureRotation convertRotation(
            final DocumentVisualSignature.FieldParameters.Rotation rotation
    ) {
        return switch (rotation) {
            case NONE -> VisualSignatureRotation.NONE;
            case AUTOMATIC -> VisualSignatureRotation.AUTOMATIC;
            case ROTATE_90 -> VisualSignatureRotation.ROTATE_90;
            case ROTATE_180 -> VisualSignatureRotation.ROTATE_180;
            case ROTATE_270 -> VisualSignatureRotation.ROTATE_270;
        };
    }

    private static SignatureImageTextParameters createTextParameters(
            final DocumentVisualSignature.TextParameters textParameters,
            final DSSDocument dssDocument
    ) {
        final var params = new SignatureImageTextParameters();

        Optional.ofNullable(textParameters.text())
                .ifPresent(params::setText);

        Optional.ofNullable(textParameters.textColor())
                .map(Color::decode)
                .ifPresent(params::setTextColor);

        Optional.ofNullable(textParameters.backgroundColor())
                .map(Color::decode)
                .ifPresent(params::setBackgroundColor);

        Optional.ofNullable(textParameters.padding())
                .ifPresent(params::setPadding);

        Optional.ofNullable(textParameters.textWrapping())
                .map(DocumentVisualSignatureService::convertTextWrapping)
                .ifPresent(params::setTextWrapping);

        Optional.ofNullable(textParameters.signerTextPosition())
                .map(DocumentVisualSignatureService::convertSignerTextPosition)
                .ifPresent(params::setSignerTextPosition);

        Optional.ofNullable(textParameters.signerTextHorizontalAlignment())
                .map(DocumentVisualSignatureService::convertSignerTextHorizontalAlignment)
                .ifPresent(params::setSignerTextHorizontalAlignment);

        Optional.ofNullable(textParameters.signerTextVerticalAlignment())
                .map(DocumentVisualSignatureService::convertSignerTextVerticalAlignment)
                .ifPresent(params::setSignerTextVerticalAlignment);

        Optional.ofNullable(textParameters.standard14Font())
                .map(DocumentVisualSignatureService::convertStandard14Font)
                .ifPresent(params::setFont);

        if (params.getFont() == null) {
            Optional.ofNullable(textParameters.customFont())
                    .map(customFont -> createCustomFont(customFont, dssDocument))
                    .ifPresent(params::setFont);
        }

        return params;
    }

    private static TextWrapping convertTextWrapping(final DocumentVisualSignature.TextParameters.TextWrapping textWrapping) {
        return switch(textWrapping) {
            case FILL_BOX -> TextWrapping.FILL_BOX;
            case FILL_BOX_AND_LINEBREAK -> TextWrapping.FILL_BOX_AND_LINEBREAK;
            case FONT_BASED -> TextWrapping.FONT_BASED;
        };
    }

    private static SignerTextPosition convertSignerTextPosition(final  DocumentVisualSignature.TextParameters.SignerTextPosition signerTextPosition) {
        return switch (signerTextPosition) {
            case TOP -> SignerTextPosition.TOP;
            case BOTTOM -> SignerTextPosition.BOTTOM;
            case RIGHT -> SignerTextPosition.RIGHT;
            case LEFT -> SignerTextPosition.LEFT;
        };
    }

    private static SignerTextHorizontalAlignment convertSignerTextHorizontalAlignment(
            final DocumentVisualSignature.TextParameters.SignerTextHorizontalAlignment signerTextHorizontalAlignment
    ) {
        return switch (signerTextHorizontalAlignment) {
            case LEFT -> SignerTextHorizontalAlignment.LEFT;
            case CENTER -> SignerTextHorizontalAlignment.CENTER;
            case RIGHT -> SignerTextHorizontalAlignment.RIGHT;
        };
    }

    private static SignerTextVerticalAlignment convertSignerTextVerticalAlignment(
            final DocumentVisualSignature.TextParameters.SignerTextVerticalAlignment signerTextVerticalAlignment
    ) {
        return switch (signerTextVerticalAlignment) {
            case TOP -> SignerTextVerticalAlignment.TOP;
            case MIDDLE -> SignerTextVerticalAlignment.MIDDLE;
            case BOTTOM -> SignerTextVerticalAlignment.BOTTOM;
        };
    }

    private static DSSFont convertStandard14Font(final DocumentVisualSignature.TextParameters.Standard14Font standard14Font) {
        return new PdfBoxNativeFont(new PDType1Font(
                switch (standard14Font) {
                    case TIMES_ROMAN -> Standard14Fonts.FontName.TIMES_ROMAN;
                    case TIMES_BOLD -> Standard14Fonts.FontName.TIMES_BOLD;
                    case TIMES_ITALIC -> Standard14Fonts.FontName.TIMES_ITALIC;
                    case TIMES_BOLD_ITALIC -> Standard14Fonts.FontName.TIMES_BOLD_ITALIC;
                    case HELVETICA -> Standard14Fonts.FontName.HELVETICA;
                    case HELVETICA_BOLD -> Standard14Fonts.FontName.HELVETICA_BOLD;
                    case HELVETICA_OBLIQUE -> Standard14Fonts.FontName.HELVETICA_OBLIQUE;
                    case HELVETICA_BOLD_OBLIQUE -> Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE;
                    case COURIER -> Standard14Fonts.FontName.COURIER;
                    case COURIER_BOLD -> Standard14Fonts.FontName.COURIER_BOLD;
                    case COURIER_OBLIQUE -> Standard14Fonts.FontName.COURIER_OBLIQUE;
                    case COURIER_BOLD_OBLIQUE -> Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE;
                    case SYMBOL -> Standard14Fonts.FontName.SYMBOL;
                    case ZAPF_DINGBATS -> Standard14Fonts.FontName.ZAPF_DINGBATS;
                }
        ));
    }

    private static DSSFont createCustomFont(final String fontBase64, final DSSDocument dssDocument) {
        try (final var pdfReader = new PdfBoxDocumentReader(dssDocument)) {
            final var font = Base64.getDecoder().decode(fontBase64);
            final var pdType0Font = PDType0Font.load(
                    pdfReader.getPDDocument(),
                    new ByteArrayInputStream(font)
            );
            return new PdfBoxNativeFont(pdType0Font);
        } catch (final IOException e) {
            throw new DocumentVisualSignatureException("Problem with creating custom font", e);
        }
    }
}
