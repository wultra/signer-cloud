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

import org.springframework.http.HttpRange;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TODO description
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
class DocumentPartsService {

    List<DocumentPart> getParts(final String ranges, final long fileSize) {
        final var parsedRanges = parseRanges(ranges);

        final var parts = new ArrayList<DocumentPart>(parsedRanges.size());

        for (final var parsedRange : parsedRanges) {
            final var start = parsedRange.getRangeStart(fileSize);
            final var end = parsedRange.getRangeEnd(fileSize);

            parts.add(new DocumentPart(start, end));
        }

        return mergeParts(parts);
    }

    private static List<HttpRange> parseRanges(final String ranges) {
        try {
            return HttpRange.parseRanges(ranges);
        } catch (final IllegalArgumentException e) {
            final var message = String.format("Invalid range header: %s Reason: %s", ranges, e.getMessage());
            throw new DownloadDocumentException(message);
        }
    }

    private static List<DocumentPart> mergeParts(final List<DocumentPart> parts) {
        final var size = parts.size();

        if (size <= 1) {
            return parts;
        }

        final var sortedParts = parts.stream()
                .sorted(Comparator.comparingLong(DocumentPart::start))
                .toList();

        final var mergedParts = new ArrayList<DocumentPart>(size);

        var currentPart = sortedParts.get(0);
        for (int i = 1; i < size; i++) {
            final var nextPart = parts.get(i);
            if (currentPart.end + 1 >= nextPart.start) {
                // Merge parts
                currentPart = new DocumentPart(currentPart.start, Math.max(currentPart.end, nextPart.end));
            } else {
                mergedParts.add(currentPart);
                currentPart = nextPart;
            }
        }
        mergedParts.add(currentPart);
        return mergedParts;
    }

    record DocumentPart(long start, long end) {};
}
