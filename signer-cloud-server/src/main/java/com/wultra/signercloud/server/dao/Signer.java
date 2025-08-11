package com.wultra.signercloud.server.dao;

import com.wultra.signercloud.server.status.SignerStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Data Access Object for <code>sc_signer</code> table.
 */
@Getter
@Builder
@Table("sc_signer")
public class Signer {

    @Id
    @Sequence("sc_signer_seq")
    private long id;

    private Instant timestampCreated;

    private Instant timestampLastUpdated;

    private String signerExternalId;

    private String userId;

    private String csr;

    private String certificate;

    private Instant timestampCertificateExpiration;

    private SignerStatus signerStatus;
}
