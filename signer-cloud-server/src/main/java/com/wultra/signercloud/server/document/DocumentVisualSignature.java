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

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Visual signature definition in a document, using the same structure as {@link eu.europa.esig.dss.pades.SignatureImageParameters}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Definition of visual signature in document")
public record DocumentVisualSignature(
        @Schema(
                description = "Image in signature",
                example = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNgYAAAAAMAAWgmWQ0AAAAASUVORK5CYII=",
                format = "byte"
        )
        String image,

        @Schema(
                description = "DPI of the signature image",
                example = "100"
        )
        Integer dpi,

        @Schema(
                description = "Horizontal alignment of the visual signature",
                example = "CENTER"
        )
        AlignmentHorizontal alignmentHorizontal,

        @Schema(
                description = "Vertical alignment of the visual signature",
                example = "MIDDLE"
        )
        AlignmentVertical alignmentVertical,

        @Schema(
                description = "A percent to zoom the image (100% means no scaling). This do not touch zooming of the text representation.",
                example = "75"
        )
        Integer zoom,

        @Schema(
                description = "Color of image background",
                example = "#2BCB9A",
                format = "hex color"
        )
        String backgroundColor,

        @Schema(
                description = "Image scaling behavior within a signature field with a fixed size."
        )
        ImageScaling imageScaling,

        FieldParameters fieldParameters,

        TextParameters textParameters
) {

    /**
     * Visual signature horizontal position, using same values as {@link eu.europa.esig.dss.enumerations.VisualSignatureAlignmentHorizontal}.
     */
    enum AlignmentHorizontal {
        NONE,
        LEFT,
        CENTER,
        RIGHT
    }

    /**
     * Visual signature vertical position, using same values as {@link eu.europa.esig.dss.enumerations.VisualSignatureAlignmentVertical}.
     */
    enum AlignmentVertical {
        NONE,
        TOP,
        MIDDLE,
        BOTTOM
    }

    /**
     * Visual signature image scaling, using same values as {@link eu.europa.esig.dss.enumerations.ImageScaling}.
     */
    enum ImageScaling {
        STRETCH,
        ZOOM_AND_CENTER,
        CENTER
    }

    /**
     * Visual signature field definition, using same structure as {@link eu.europa.esig.dss.pades.SignatureFieldParameters}.
     *
     */
    @Schema(description = "Definition of field for visual signature in document")
    record FieldParameters(
            @Schema(
                    description = "Field ID where the signature will be placed.",
                    example = "signature-field-1"
            )
            String fieldId,

            @Schema(
                    description = "Page number where the signature will be placed. First page is 1.",
                    example = "1"
            )
            Integer page,

            @Schema(
                    description = "Coordinate X where the signature will be placed.",
                    example = "300"
            )
            Float originX,

            @Schema(
                    description = "Coordinate Y where the signature will be placed.",
                    example = "600"
            )
            Float originY,

            @Schema(
                    description = "Signature field width.",
                    example = "150"
            )
            Float width,

            @Schema(
                    description = "Signature field height.",
                    example = "75"
            )
            Float height,

            @Schema(
                    description = "Rotation of the signature field.",
                    example = "AUTOMATIC"
            )
            Rotation rotation
    ) {
        /**
         * Visual signature field rotation, using same values as {@link eu.europa.esig.dss.enumerations.VisualSignatureRotation}.
         */
        enum Rotation {
            NONE,
            AUTOMATIC,
            ROTATE_90,
            ROTATE_180,
            ROTATE_270
        }
    }

    /**
     * Visual signature text definition, using same values as {@link eu.europa.esig.dss.pades.SignatureImageTextParameters}.
     */
    @Schema(description = "Definition of text for visual signature in document")
    record TextParameters(
            @Schema(
                    description = "Text value",
                    example = "Joh Doe"
            )
            String text,

            @Schema(
                    description = "Color of text",
                    example = "#2BCB9A",
                    format = "hex color"
            )
            String textColor,

            @Schema(
                    description = "Color of text background",
                    example = "#2BCB9A",
                    format = "hex color"
            )
            String backgroundColor,

            @Schema(
                    description = "Padding in pixels to bound text around",
                    example = "15"
            )
            Float padding,

            @Schema(
                    description = "Wrapping of the text within the signature field's box",
                    example = "FONT_BASED"
            )
            TextWrapping textWrapping,

            @Schema(
                    description = "Position of signer name on the image",
                    example = "LEFT"
            )
            SignerTextPosition signerTextPosition,

            @Schema(
                    description = "Horizontal alignment of signer name within a visual signature's text area",
                    example = "RIGHT"
            )
            SignerTextHorizontalAlignment signerTextHorizontalAlignment,

            @Schema(
                    description = "Vertical alignment of signer name within a visual signature's text area",
                    example = "MIDDLE"
            )
            SignerTextVerticalAlignment signerTextVerticalAlignment,

            @Schema(
                    description = "Text font from the PDF Type 1 fonts specification. It overrides `customFont` if both are provided.",
                    example = "HELVETICA_BOLD_OBLIQUE"
            )
            Standard14Font standard14Font,

            @Schema(
                    description = "Custom text font in TrueType (.ttf) or OpenType (.otf) format. It is overridden by `standard14Font` if both are provided.",
                    example = "AAEAAAALAIAAAwAwT1MvMggAAAC8AAAAYGNtYXABdQAABFQA...",
                    format = "byte"
            )
            String customFont
    ) {
        /**
         * Visual signature text wrapping, using same values as {@link eu.europa.esig.dss.enumerations.TextWrapping}.
         */
        enum TextWrapping {
            FILL_BOX,
            FILL_BOX_AND_LINEBREAK,
            FONT_BASED
        }

        /**
         * Visual signature signer name position, using same values as {@link eu.europa.esig.dss.enumerations.SignerTextPosition}.
         */
        enum SignerTextPosition {
            TOP,
            BOTTOM,
            RIGHT,
            LEFT
        }

        /**
         * Visual signature signer name horizontal alignment, using same values as {@link eu.europa.esig.dss.enumerations.SignerTextHorizontalAlignment}.
         */
        enum SignerTextHorizontalAlignment {
            LEFT,
            CENTER,
            RIGHT
        }

        /**
         * Visual signature signer name vertical alignment, using same values as {@link eu.europa.esig.dss.enumerations.SignerTextVerticalAlignment}.
         */
        enum SignerTextVerticalAlignment {
            TOP,
            MIDDLE,
            BOTTOM
        }

        /**
         * Visual signature text font, using same values as {@link Standard14Fonts.FontName}.
         */
        enum Standard14Font {
            TIMES_ROMAN,
            TIMES_BOLD,
            TIMES_ITALIC,
            TIMES_BOLD_ITALIC,
            HELVETICA,
            HELVETICA_BOLD,
            HELVETICA_OBLIQUE,
            HELVETICA_BOLD_OBLIQUE,
            COURIER,
            COURIER_BOLD,
            COURIER_OBLIQUE,
            COURIER_BOLD_OBLIQUE,
            SYMBOL,
            ZAPF_DINGBATS
        }
    }
}
