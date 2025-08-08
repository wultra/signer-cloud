package com.wultra.signercloud.server.repository;

import com.wultra.signercloud.server.dao.Signer;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for accessing the <code>sc_signer</code> table.
 */
public interface SignerRepository extends CrudRepository<Signer, Long> {
}
