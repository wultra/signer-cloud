package com.wultra.signercloud.server.dao;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Data Access Object for <code>sc_document_content</code> table.
 */
@Getter
@Builder
@Table("sc_document_content")
public class DocumentContent {

    @Id
    private long id;

    private byte[] content;
}
