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
package com.wultra.signercloud.server.encryption;

import io.getlime.security.powerauth.crypto.lib.generator.KeyGenerator;
import io.getlime.security.powerauth.crypto.lib.model.exception.CryptoProviderException;
import io.getlime.security.powerauth.crypto.lib.model.exception.GenericCryptoException;
import io.getlime.security.powerauth.crypto.lib.util.AESEncryptionUtils;
import io.getlime.security.powerauth.crypto.lib.util.KeyConvertor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

/**
 * Encryption service.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EncryptionService {

    private final KeyGenerator keyGenerator = new KeyGenerator();
    private final AESEncryptionUtils aesEncryptionUtils = new AESEncryptionUtils();
    private final KeyConvertor keyConvertor = new KeyConvertor();

    private final EncryptionConfigurationProperties configuration;

    /**
     * Decrypt the given string.
     *
     * @param dataString String to decrypt.
     * @param encryptionMode Encryption mode.
     * @param encryptionKeyProvider Provider for values used for derivation of secret key.
     * @return Decrypted value.
     * @apiNote is not determined to encrypt binary data.
     */
    public String decrypt(final String dataString, final EncryptionMode encryptionMode, final Supplier<List<String>> encryptionKeyProvider) {
        if (encryptionMode == EncryptionMode.NO_ENCRYPTION) {
            return dataString;
        } else if (encryptionMode == EncryptionMode.AES_HMAC) {
            final byte[] dataBytes = Base64.getDecoder().decode(dataString);
            final byte[] decrypted = decrypt(dataBytes, encryptionKeyProvider);
            return new String(decrypted, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported encryption mode: " + encryptionMode);
        }
    }

    private byte[] decrypt(final byte[] data, final Supplier<List<String>> encryptionKeyProvider) {
        final String masterDbEncryptionKeyBase64 = configuration.getMasterDbEncryptionKey();
        Assert.hasText(masterDbEncryptionKeyBase64, "Missing master DB encryption key");
        // Check that the length of the byte array is sufficient to avoid AIOOBE on the next calls
        Assert.isTrue(data.length >= 16, "The byte array is too short");

        try {
            final SecretKey masterDbEncryptionKey = keyConvertor.convertBytesToSharedSecretKey(Base64.getDecoder().decode(masterDbEncryptionKeyBase64));

            final SecretKey secretKey = deriveSecretKey(masterDbEncryptionKey, encryptionKeyProvider);

            // IV is present in the first 16 bytes
            final byte[] iv = Arrays.copyOfRange(data, 0, 16);

            // Encrypted data hash is present after IV
            final byte[] encryptedData = Arrays.copyOfRange(data, 16, data.length);

            return aesEncryptionUtils.decrypt(encryptedData, iv, secretKey);
        } catch (InvalidKeyException | GenericCryptoException | CryptoProviderException e) {
            logger.error(e.getMessage(), e);
            throw new IllegalStateException("Failed to decrypt data", e);
        }
    }

    /**
     * Derive a secret key from the the master DB encryption key and the given derivations.
     *
     * @param masterDbEncryptionKey Master DB encryption key.
     * @param encryptionKeyProvider Provider for values used for derivation of the secret key.
     * @return Derived secret key.
     * @throws GenericCryptoException In case key derivation fails.
     * @see <a href="https://github.com/wultra/powerauth-server/blob/develop/docs/Encrypting-Records-in-Database.md">Encrypting Records in Database</a>
     */
    private SecretKey deriveSecretKey(SecretKey masterDbEncryptionKey, final Supplier<List<String>> encryptionKeyProvider) throws GenericCryptoException, CryptoProviderException {
        // Use concatenated value bytes as index for KDF_INTERNAL
        final byte[] index = String.join("&", encryptionKeyProvider.get()).getBytes(StandardCharsets.UTF_8);

        // Derive secretKey from master DB encryption key using KDF_INTERNAL with constructed index
        return keyGenerator.deriveSecretKeyHmac(masterDbEncryptionKey, index);
    }
}
