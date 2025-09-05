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
 * Service for parsing HTTP {@code Range} header and merging overlapping ranges.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
class DocumentRangesService {

    /**
     * Parse HTTP {@code Range} header from String to {@link  List<DocumentPart>}.
     *
     * Parts are merged if they overlap or are adjacent. They are returned in the same order as they were specified in the input.
     *
     * @param ranges ranges header value
     * @param fileSize size of the file to which the ranges apply
     * @return list of ranges
     */
    List<DocumentPart> getParts(final String ranges, final long fileSize) {
        final var parsedRanges = parseRanges(ranges);

        final var parts = new ArrayList<DocumentPart>(parsedRanges.size());

        for (int i = 0; i < parsedRanges.size(); i++) {
            final var parsedRange = parsedRanges.get(i);
            final var start = Math.toIntExact(parsedRange.getRangeStart(fileSize));
            final var end = Math.toIntExact(parsedRange.getRangeEnd(fileSize));

            parts.add(new DocumentPart(start, end, i));
        }

        return mergeParts(parts);
    }

    private static List<HttpRange> parseRanges(final String ranges) {
        try {
            return HttpRange.parseRanges(ranges);
        } catch (final IllegalArgumentException e) {
            final var message = "Invalid range header: %s Reason: %s".formatted(ranges, e.getMessage());
            throw new DownloadDocumentException(message);
        }
    }

    private static List<DocumentPart> mergeParts(final List<DocumentPart> parts) {
        final var size = parts.size();

        if (size <= 1) {
            return parts;
        }

        final var partsSortedByStart = parts.stream()
                .sorted(Comparator.comparingInt(DocumentPart::start))
                .toList();

        final var mergedParts = new ArrayList<DocumentPart>(size);

        var currentPart = partsSortedByStart.get(0);
        for (int i = 1; i < size; i++) {
            final var nextPart = partsSortedByStart.get(i);
            if (currentPart.end + 1 >= nextPart.start) {
                // Merge parts
                currentPart = new DocumentPart(currentPart.start, Math.max(currentPart.end, nextPart.end), currentPart.order);
            } else {
                mergedParts.add(currentPart);
                currentPart = nextPart;
            }
        }
        mergedParts.add(currentPart);

        return mergedParts.stream()
                .sorted(Comparator.comparingInt(DocumentPart::order))
                .toList();
    }

    record DocumentPart(int start, int end, int order) {};
}
