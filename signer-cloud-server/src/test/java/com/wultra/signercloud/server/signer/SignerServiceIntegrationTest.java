package com.wultra.signercloud.server.signer;

import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
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
    void testCleanupSigners() throws Exception {
        final long result = tested.cleanupSigners(1);

        assertEquals(1, result);

        final Map<String, Object> callbackEvent = jdbcTemplate.queryForMap("SELECT * FROM sc_callback_event ORDER BY timestamp_created DESC LIMIT 1");
        assertNotNull(callbackEvent);
        assertEquals("EXPIRED", callbackEvent.get("CALLBACK_TYPE"));
        assertEquals("PROCESSING", callbackEvent.get("STATUS"));
        JSONAssert.assertEquals("""
                {"externalSignerId": "signer1", "userId": "user1", "callbackType": "EXPIRED", "certificateSerialNumber": "64309416018842723591211913217267439625813315032", "certificateExpiration": "2027-08-11T09:14:46Z"}""",
                callbackEvent.get("CALLBACK_DATA").toString(), false);
        assertNotNull(callbackEvent.get("IDEMPOTENCY_KEY"));
        assertNotNull(callbackEvent.get("TIMESTAMP_CREATED"));
    }
}
