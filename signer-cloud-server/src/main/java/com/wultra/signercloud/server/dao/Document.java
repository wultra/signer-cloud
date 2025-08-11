package com.wultra.signercloud.server.dao;

import com.wultra.signercloud.server.status.DocumentStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Data Access Object for <code>sc_document</code> table.
 */
@Getter
@Builder
@Table("sc_document")
public class Document {

    @Id
    @Sequence("sc_document_seq")
    private long id;

    private Instant timestampCreated;

    private Instant timestampLastUpdated;

    private String documentId;

    private long signerId;

    private String externalId;

    private String documentName;

    private String fileName;

    private long fileSize;

    private long documentContentId;

    private String hash;

    private DocumentStatus documentStatus;

    private String signature;
}
