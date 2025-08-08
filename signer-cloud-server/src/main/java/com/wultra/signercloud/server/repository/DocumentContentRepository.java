package com.wultra.signercloud.server.repository;

import com.wultra.signercloud.server.dao.DocumentContent;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for accessing the <code>sc_document_content</code> table.
 */
public interface DocumentContentRepository extends CrudRepository<DocumentContent, Long> {
}
