package com.wultra.signercloud.server.encryption;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Test for {@link EncryptionService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("test")
class EncryptionServiceTest {

    @Autowired
    private EncryptionService tested;

    @Test
    void testDecrypt_noEncryption() {
        final String result = tested.decrypt("This is not encrypted", EncryptionMode.NO_ENCRYPTION, () -> null);

        assertEquals("This is not encrypted", result);
    }

    @Test
    void testDecrypt_aesHmac() {
        final Supplier<List<String>> encryptionKeyProvider = () -> List.of("1");
        final String input = """
                {"user":"user1","password":"secret"}
                """;

        final String encrypted = tested.encrypt(input, encryptionKeyProvider);

        assertNotEquals(input, encrypted);

        final String result = tested.decrypt(encrypted, EncryptionMode.AES_HMAC, encryptionKeyProvider);

        assertEquals(input, result);
    }
}
