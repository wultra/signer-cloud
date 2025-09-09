package com.wultra.signercloud.server.signer;

import com.wultra.signercloud.server.callback.CallbackEventStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for {@link SignerService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql
class SignerServiceIntegrationTest {

    @Autowired
    private SignerService tested;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testCleanupSigners() {
        final long result = tested.cleanupSigners(1);

        assertEquals(1, result);

        final Map<String, Object> callbackEvent = jdbcTemplate.queryForMap("SELECT * FROM sc_callback_event ORDER BY timestamp_created DESC LIMIT 1");
        assertNotNull(callbackEvent);
        assertEquals(1L, callbackEvent.get("CALLBACK_ID"));
        assertEquals(CallbackEventStatus.PROCESSING.toString(), callbackEvent.get("STATUS"));
        assertEquals("{\"externalSignerId\": \"signer1\", \"userId\": \"user1\"}", callbackEvent.get("CALLBACK_DATA"));
        assertNotNull(callbackEvent.get("IDEMPOTENCY_KEY"));
        assertNotNull(callbackEvent.get("TIMESTAMP_CREATED"));
    }
}
