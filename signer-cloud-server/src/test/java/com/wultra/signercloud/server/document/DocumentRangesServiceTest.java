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

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static com.wultra.signercloud.server.document.DocumentRangesService.DocumentPart;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TODO description
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class DocumentRangesServiceTest {
    private static final long FILE_SIZE = 100L;

    @InjectMocks
    private DocumentRangesService documentRangesService;

    private static Stream<Arguments> validRanges() {
        return Stream.of(
                Arguments.of("bytes=0-9", List.of(new DocumentPart(0, 9, 0))),
                Arguments.of("bytes=0-9,20-29", List.of(new DocumentPart(0, 9, 0), new DocumentPart(20, 29, 1))),
                Arguments.of("bytes=0-9,5-14", List.of(new DocumentPart(0, 14, 0))),
                Arguments.of("bytes=0-9,10-19", List.of(new DocumentPart(0, 19, 0))),
                Arguments.of("bytes=-10", List.of(new DocumentPart(90, 99, 0))),
                Arguments.of("bytes=95-", List.of(new DocumentPart(95, 99, 0))),
                Arguments.of("", List.of()),
                Arguments.of("bytes=0-9,0-9,5-15", List.of(new DocumentPart(0, 15, 0))),
                Arguments.of("bytes=90-150", List.of(new DocumentPart(90, 99, 0))),
                Arguments.of("bytes=20-29,10-15", List.of(new DocumentPart(20, 29, 0), new DocumentPart(10, 15, 1)))
        );
    }

    private static Stream<Arguments> invalidRanges() {
        return Stream.of(
                Arguments.of("bytes=-5-10", "Invalid range header: bytes=-5-10 Reason: Error at index 1 in: \"5-10\""),
                Arguments.of("bytes=20-10", "Invalid range header: bytes=20-10 Reason: firstBytePosition=20 should be less then or equal to lastBytePosition=10"),
                Arguments.of("malformedHeader", "Invalid range header: malformedHeader Reason: Range 'malformedHeader' does not start with 'bytes='"),
                Arguments.of("bytes=malformedRange", "Invalid range header: bytes=malformedRange Reason: Range 'malformedRange' does not contain \"-\"")
        );
    }

    @ParameterizedTest(name = "{index} => header={0}")
    @MethodSource("validRanges")
    void testGetPartsWhenRangesAreValidThenCorrectRangesAreReturned(final String header, final List<DocumentPart> expected) {
        // given
        // -

        // when
        final var parts = documentRangesService.getParts(header, FILE_SIZE);

        // then
        assertArrayEquals(expected.toArray(), parts.toArray());
    }

    @ParameterizedTest(name = "{index} => header={0}")
    @MethodSource("invalidRanges")
    void testGetPartsWhenRangesAreNotValidThenExceptionWithCorrectMessageIsReturned(final String header, final String expectedMessage) {
        // given

        // when
        final var exception = assertThrows(DownloadDocumentException.class, () -> documentRangesService.getParts(header, FILE_SIZE));

        // then
        assertEquals(expectedMessage, exception.getMessage());
    }
}
