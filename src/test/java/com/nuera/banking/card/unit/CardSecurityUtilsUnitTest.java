package com.nuera.banking.card.unit;

import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.service.service.EncryptionService;
import com.nuera.banking.card.service.util.CardSecurityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests: Card Security & Cryptographic Utilities")
class CardSecurityUtilsUnitTest {

    private final EncryptionService encryptionService = new EncryptionService("c2VjdXJlX2JhbmtpbmdfY2FyZF9rZXlfMjAyNl9hZXM=");

    @Test
    @DisplayName("BCrypt PIN hashing produces salted hash and verifies successfully")
    void testPinHashingAndVerification() {
        String pin = "1234";
        String hashedPin = CardSecurityUtils.hashPin(pin);

        assertNotNull(hashedPin);
        assertNotEquals(pin, hashedPin);
        assertTrue(hashedPin.startsWith("$2a$") || hashedPin.startsWith("$2b$") || hashedPin.startsWith("$2y$"));

        // Match verification
        assertTrue(CardSecurityUtils.verifyPin(pin, hashedPin));
        assertFalse(CardSecurityUtils.verifyPin("9999", hashedPin));
        assertFalse(CardSecurityUtils.verifyPin("1235", hashedPin));
    }

    @Test
    @DisplayName("Card masking masks all digits except first 4 and last 4")
    void testCardMasking() {
        String pan = "4111222233334444";
        String masked = CardSecurityUtils.maskCardNumber(pan);

        assertNotNull(masked);
        assertTrue(masked.startsWith("4111"));
        assertTrue(masked.endsWith("4444"));
        assertTrue(masked.contains("*"));
        assertFalse(masked.contains("2222"));
        assertFalse(masked.contains("3333"));
    }

    @Test
    @DisplayName("Format masked card number inserts spaces for display")
    void testFormatMaskedCard() {
        String masked = "4111********4444";
        String formatted = CardSecurityUtils.formatMaskedCardNumber(masked);

        assertEquals("4111 **** **** 4444", formatted);
    }

    @Test
    @DisplayName("VISA PAN generation adheres to Luhn checksum algorithm")
    void testVisaPanGenerationAndLuhnChecksum() {
        String pan = CardSecurityUtils.generateCardNumber(CardNetwork.VISA);

        assertNotNull(pan);
        assertEquals(16, pan.length());
        assertTrue(pan.startsWith("4"));
        assertTrue(CardSecurityUtils.isValidLuhn(pan));
    }

    @Test
    @DisplayName("AES-256-GCM encryption and decryption of PAN")
    void testAesGcmEncryptionDecryption() {
        String originalPan = "4111222233334444";
        String encrypted = encryptionService.encrypt(originalPan);

        assertNotNull(encrypted);
        assertNotEquals(originalPan, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(originalPan, decrypted);
    }

    @Test
    @DisplayName("SHA-256 card number deterministic hashing for lookup")
    void testSha256CardHashing() {
        String pan = "4111222233334444";
        String hash1 = CardSecurityUtils.hashCardNumber(pan);
        String hash2 = CardSecurityUtils.hashCardNumber("4111 2222 3333 4444");

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2); // Whitespace normalized
    }
}
