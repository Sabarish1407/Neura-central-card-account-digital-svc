package com.nuera.banking.card.unit;

import com.nuera.banking.card.service.service.EncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests: EncryptionService AES-256-GCM")
class EncryptionServiceUnitTest {

    private static final String VALID_KEY_32_BYTES = "c2VjdXJlX2JhbmtpbmdfY2FyZF9rZXlfMjAyNl9hZXM=";

    @Test
    @DisplayName("Encryption round-trips successfully")
    void testEncryptionRoundTrip() {
        EncryptionService service = new EncryptionService(VALID_KEY_32_BYTES);
        String originalText = "4111222233334444";

        String encrypted = service.encrypt(originalText);
        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);

        String decrypted = service.decrypt(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    @DisplayName("Decrypting with a wrong key fails with an exception")
    void testWrongKeyFails() {
        EncryptionService serviceA = new EncryptionService(VALID_KEY_32_BYTES);

        // Generate a different valid 32-byte key
        byte[] differentKeyBytes = new byte[32];
        new SecureRandom().nextBytes(differentKeyBytes);
        String differentKey = Base64.getEncoder().encodeToString(differentKeyBytes);
        EncryptionService serviceB = new EncryptionService(differentKey);

        String cipherText = serviceA.encrypt("4111222233334444");
        assertNotNull(cipherText);

        assertThrows(IllegalStateException.class, () -> serviceB.decrypt(cipherText));
    }

    @Test
    @DisplayName("Service refuses to start without a valid 32-byte key")
    void testAppWontStartWithoutValidKey() {
        // Null key
        assertThrows(IllegalStateException.class, () -> new EncryptionService(null));

        // Blank key
        assertThrows(IllegalStateException.class, () -> new EncryptionService("   "));

        // Invalid base64
        assertThrows(IllegalStateException.class, () -> new EncryptionService("not-valid-base64!@#$"));

        // Key length less than 32 bytes (16 bytes = 128-bit)
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new EncryptionService(shortKey));

        // Key length greater than 32 bytes (40 bytes)
        String longKey = Base64.getEncoder().encodeToString(new byte[40]);
        assertThrows(IllegalStateException.class, () -> new EncryptionService(longKey));
    }
}
