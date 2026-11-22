package com.nuera.banking.card.service.util;

import com.nuera.banking.card.model.enums.CardNetwork;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class CardSecurityUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM/yy");
    private static final BCryptPasswordEncoder BCRYPT_ENCODER = new BCryptPasswordEncoder(10, RANDOM);

    private CardSecurityUtils() {
    }

    /**
     * Hashes 4-digit PIN using BCrypt with a randomly generated salt.
     */
    public static String hashPin(String rawPin) {
        if (rawPin == null) {
            return null;
        }
        return BCRYPT_ENCODER.encode(rawPin);
    }

    /**
     * Computes deterministic SHA-256 hash for secure O(1) indexed lookup of card numbers.
     */
    public static String hashCardNumber(String rawCardNumber) {
        if (rawCardNumber == null) {
            return null;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawCardNumber.replaceAll("\\s+", "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Computes SHA-256 hash for CVV.
     */
    public static String hashCvv(String rawCvv) {
        return hashCardNumber(rawCvv);
    }

    /**
     * Verifies raw PIN against the salted BCrypt hash.
     */
    public static boolean verifyPin(String rawPin, String storedHash) {
        if (rawPin == null || storedHash == null) {
            return false;
        }
        return BCRYPT_ENCODER.matches(rawPin, storedHash);
    }

    /**
     * Masks card number for PCI-DSS compliance, displaying only the first 4 and last 4 digits.
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "**** **** **** ****";
        }
        String clean = cardNumber.replaceAll("\\s+", "");
        if (clean.length() == 16) {
            return clean.substring(0, 4) + " **** **** " + clean.substring(12);
        }
        String last4 = clean.substring(clean.length() - 4);
        return "**** **** **** " + last4;
    }

    public static String formatMaskedCardNumber(String masked) {
        if (masked == null) {
            return null;
        }
        if (masked.contains(" ")) {
            return masked;
        }
        // e.g. 4111********1111 -> 4111 **** **** 1111
        if (masked.length() == 16) {
            return masked.substring(0, 4) + " " + masked.substring(4, 8) + " " +
                    masked.substring(8, 12) + " " + masked.substring(12, 16);
        }
        return masked;
    }

    /**
     * Generates a unique opaque card token for tokenized lookups.
     */
    public static String generateCardToken() {
        return "tok-" + UUID.randomUUID();
    }

    /**
     * Generates a 16-digit VISA card number with valid Luhn check digit.
     */
    public static String generateVisaCardNumber() {
        StringBuilder sb = new StringBuilder("4");
        while (sb.length() < 15) {
            sb.append(RANDOM.nextInt(10));
        }
        int checkDigit = calculateLuhnCheckDigit(sb.toString());
        sb.append(checkDigit);
        return sb.toString();
    }

    public static String generateCardNumber(CardNetwork network) {
        return generateVisaCardNumber();
    }

    /**
     * Validates whether a card number conforms to the Luhn checksum algorithm.
     */
    public static boolean isValidLuhn(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 13) {
            return false;
        }
        String clean = cardNumber.replaceAll("\\s+", "");
        int sum = 0;
        boolean alternate = false;
        for (int i = clean.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(clean.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    /**
     * Generates a 3-digit CVV.
     */
    public static String generateCvv() {
        int cvv = RANDOM.nextInt(100, 1000);
        return String.valueOf(cvv);
    }

    /**
     * Generates card expiration date 3 years into the future (MM/yy).
     */
    public static String generateExpiryDate(int yearsValid) {
        return LocalDate.now().plusYears(yearsValid).format(EXPIRY_FORMATTER);
    }

    /**
     * Generates a unique transaction authorization code.
     */
    public static String generateAuthorizationCode() {
        int code = RANDOM.nextInt(100000, 999999);
        return "AUTH" + code;
    }

    /**
     * Generates a unique transaction reference number.
     */
    public static String generateReferenceNumber() {
        long timestamp = System.currentTimeMillis();
        int suffix = RANDOM.nextInt(1000, 9999);
        return "TXN-" + timestamp + "-" + suffix;
    }

    private static int calculateLuhnCheckDigit(String numberWithoutCheck) {
        int sum = 0;
        boolean alternate = true;
        for (int i = numberWithoutCheck.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(numberWithoutCheck.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }
}
