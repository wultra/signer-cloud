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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/**
 * Controller for {@link Document} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@RestController
@RequestMapping("api/documents")
@AllArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    UploadDocumentResponse upload(
            @RequestParam("signerId") final String externalSignerId,
            @RequestParam("externalId") final String externalDocumentId,
            @RequestParam("name") final String documentName,
            @RequestParam("content") final MultipartFile fileContent
    ) throws IOException, NoSuchAlgorithmException {
        return documentService.uploadDocument(externalSignerId, externalDocumentId, documentName, fileContent);
    }
}
