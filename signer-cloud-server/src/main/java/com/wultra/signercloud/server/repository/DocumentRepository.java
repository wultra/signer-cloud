package com.wultra.signercloud.server.repository;

import com.wultra.signercloud.server.dao.Document;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for accessing the <code>sc_document</code> table.
 */
public interface DocumentRepository extends CrudRepository<Document, Long> {
}
