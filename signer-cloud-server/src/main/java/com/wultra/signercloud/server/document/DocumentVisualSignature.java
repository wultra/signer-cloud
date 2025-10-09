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

/**
 * TODO description
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public record DocumentVisualSignature(
        String image,
        Integer dpi,
        AlignmentHorizontal alignmentHorizontal,
        AlignmentVertical alignmentVertical,
        Integer zoom,
        String backgroundColor,
        ImageScaling imageScaling,
        FieldParameters fieldParameters,
        TextParameters textParameters
) {

    enum AlignmentHorizontal {
        NONE,
        LEFT,
        CENTER,
        RIGHT
    }

    enum AlignmentVertical {
        NONE,
        TOP,
        MIDDLE,
        BOTTOM
    }

    enum ImageScaling {
        STRETCH,
        ZOOM_AND_CENTER,
        CENTER
    }

    record FieldParameters(
            String fieldId,
            Integer page,
            Float originX,
            Float originY,
            Float width,
            Float height,
            Rotation rotation
    ) {
        enum Rotation {
            NONE,
            AUTOMATIC,
            ROTATE_90,
            ROTATE_180,
            ROTATE_270
        }
    }

    record TextParameters(
            String text,
            String textColor,
            String backgroundColor,
            Float padding,
            TextWrapping textWrapping,
            SignerTextPosition signerTextPosition,
            SignerTextHorizontalAlignment signerTextHorizontalAlignment,
            SignerTextVerticalAlignment signerTextVerticalAlignment,
            Standard14Font standard14Font,
            String customFont
    ) {
        enum TextWrapping {
            FILL_BOX,
            FILL_BOX_AND_LINEBREAK,
            FONT_BASED
        }

        enum SignerTextPosition {
            TOP,
            BOTTOM,
            RIGHT,
            LEFT
        }

        enum SignerTextHorizontalAlignment {
            LEFT,
            CENTER,
            RIGHT
        }

        enum SignerTextVerticalAlignment {
            TOP,
            MIDDLE,
            BOTTOM
        }

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
