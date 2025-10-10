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
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pdf.pdfbox.visible.PdfBoxNativeFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.awt.*;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link DocumentVisualSignature}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class DocumentVisualSignatureServiceTest {

    private static final String SIGNATURE_IMAGE_BASE_64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNgYAAAAAMAAWgmWQ0AAAAASUVORK5CYII=";
    private static final String CUSTOM_FONT_BASE_64 = "AAEAAAALAIAAAwAwT1MvMggAAAC8AAAAYGNtYXABdQAABFQAAABIZ2x5ZgZs+gAAAVgAAABMaGVhZB4U6wAAAYwAAAA2aGhlYQBrAGoAAAHwAAAAJGhtdHgAAAAAAAACAQAAAAxsb2NhBoYAAAIgAAAACm1heHAAAAAQAAACMAAAACBuYW1lzys9HwAAAjwAAAJvcG9zdAEbLgAAAtgAAABQAAEAAAABAAD//wADAAEAAAAAAAIAAQAAAAEAAAABAACAAAAAAgAAAAAAAAABAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @InjectMocks
    private DocumentVisualSignatureService documentVisualSignatureService;

    private static DSSDocument dssDocument;

    @BeforeAll
    static void setUp() throws IOException {
        final var documentContent = new ClassPathResource("input.pdf").getContentAsByteArray();
        dssDocument = new InMemoryDocument(documentContent);
    }

    @Test
    void testCreateVisualSignatureWhenAllValuesAreSetThenAllAreMapped() {
        // given
        final var documentVisualSignature = createDocumentVisualSignature();

        // when
        final var signatureImageParameters = documentVisualSignatureService.createVisualSignature(documentVisualSignature, dssDocument);

        // when
        assertSignatureImageParameters(signatureImageParameters);
    }

    private static DocumentVisualSignature createDocumentVisualSignature() {
        final var fieldParams = new DocumentVisualSignature.FieldParameters(
                "signature-1",
                2,
                150f,
                300f,
                200f,
                50f,
                DocumentVisualSignature.FieldParameters.Rotation.ROTATE_270
        );

        final var textParams = new DocumentVisualSignature.TextParameters(
                "Text Signature",
                "#E65C8A",
                "#2BCB9A",
                15f,
                DocumentVisualSignature.TextParameters.TextWrapping.FILL_BOX_AND_LINEBREAK,
                DocumentVisualSignature.TextParameters.SignerTextPosition.LEFT,
                DocumentVisualSignature.TextParameters.SignerTextHorizontalAlignment.RIGHT,
                DocumentVisualSignature.TextParameters.SignerTextVerticalAlignment.BOTTOM,
                DocumentVisualSignature.TextParameters.Standard14Font.COURIER_BOLD_OBLIQUE,
                CUSTOM_FONT_BASE_64
        );

        return new DocumentVisualSignature(
                SIGNATURE_IMAGE_BASE_64,
                300,
                DocumentVisualSignature.AlignmentHorizontal.RIGHT,
                DocumentVisualSignature.AlignmentVertical.BOTTOM,
                75,
                "#3A7DFF",
                DocumentVisualSignature.ImageScaling.ZOOM_AND_CENTER,
                fieldParams,
                textParams
        );
    }

    private static void assertSignatureImageParameters(final SignatureImageParameters params) {
        final var image = new InMemoryDocument(Base64.getDecoder().decode(SIGNATURE_IMAGE_BASE_64));
        assertEquals(image, params.getImage());
        assertEquals(300, params.getDpi());
        assertEquals(VisualSignatureAlignmentHorizontal.RIGHT, params.getVisualSignatureAlignmentHorizontal());
        assertEquals(VisualSignatureAlignmentVertical.BOTTOM, params.getVisualSignatureAlignmentVertical());
        assertEquals(75, params.getZoom());
        assertEquals(Color.decode("#3A7DFF"), params.getBackgroundColor());
        assertEquals(ImageScaling.ZOOM_AND_CENTER, params.getImageScaling());

        final var fieldParams = params.getFieldParameters();
        assertEquals("signature-1", fieldParams.getFieldId());
        assertEquals(2, fieldParams.getPage());
        assertEquals(150f, fieldParams.getOriginX());
        assertEquals(300f, fieldParams.getOriginY());
        assertEquals(200f, fieldParams.getWidth());
        assertEquals(50f, fieldParams.getHeight());
        assertEquals(VisualSignatureRotation.ROTATE_270, fieldParams.getRotation());

        final var textParams = params.getTextParameters();
        assertEquals("Text Signature", textParams.getText());
        assertEquals(Color.decode("#E65C8A"), textParams.getTextColor());
        assertEquals(Color.decode("#2BCB9A"), textParams.getBackgroundColor());
        assertEquals(15f, textParams.getPadding());
        assertEquals(TextWrapping.FILL_BOX_AND_LINEBREAK, textParams.getTextWrapping());
        assertEquals(SignerTextPosition.LEFT, textParams.getSignerTextPosition());
        assertEquals(SignerTextHorizontalAlignment.RIGHT, textParams.getSignerTextHorizontalAlignment());
        assertEquals(SignerTextVerticalAlignment.BOTTOM, textParams.getSignerTextVerticalAlignment());
        assertEquals(
                Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE.getName(),
                ((PdfBoxNativeFont) textParams.getFont()).getFont().getName()
        );
    }
}
