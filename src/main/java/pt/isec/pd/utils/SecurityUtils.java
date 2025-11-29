package pt.isec.pd.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

public final class SecurityUtils {
    private SecurityUtils() {} // Private constructor to prevent instantiation of this utility class.

    // The number of iterations for the hashing algorithm. A higher number increases the time
    // and computational cost required to calculate the hash, making brute-force attacks much slower.
    private static final int ITERATIONS = 65536;

    // The desired length of the generated hash key, in bits.
    private static final int KEY_LENGTH = 256;

    // The length of the salt, in bytes. A salt is random data that is combined with the password before hashing.
    private static final int SALT_LENGTH = 16; // bytes

    // A cryptographically strong random number generator used to create the salt.
    // This ensures that the salt is unpredictable.
    private static final SecureRandom RAND = new SecureRandom();

    // A utility to convert byte arrays to and from hexadecimal strings, which is a safe
    // way to store the binary salt and hash data in a text-based format (e.g., in a database).
    private static final HexFormat HEX = HexFormat.of();

    // The specific algorithm used for hashing. PBKDF2 with HMAC-SHA256 is a strong and widely used standard.
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    public static String createHash(String secretPlain) {
        // 1. Generate a new, random salt for this specific password.
        byte[] salt = new byte[SALT_LENGTH];
        RAND.nextBytes(salt);

        // 2. Compute the hash using the password, the generated salt, and the configured parameters.
        byte[] hash = pbkdf2(secretPlain.toCharArray(), salt, ITERATIONS, KEY_LENGTH);

        // 3. Combine iterations, salt (in hex), and hash (in hex) into a single string for storage.
        return ITERATIONS + ":" + HEX.formatHex(salt) + ":" + HEX.formatHex(hash);
    }

    public static boolean verify(String secretPlain, String stored) {
        if (secretPlain == null || stored == null) return false;

        // 1. Split the stored string into its three parts: iterations, salt, and hash.
        String[] parts = stored.split(":", 3);
        if (parts.length != 3) {
            // If the format is incorrect, it cannot be a valid hash from our system.
            return false;
        }

        try {
            // 2. Parse the parts back into their original data types.
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = HEX.parseHex(parts[1]);
            byte[] storedHash = HEX.parseHex(parts[2]);

            // 3. Re-compute the hash of the provided plain text secret using the *exact same* salt and parameters.
            byte[] computedHash = pbkdf2(secretPlain.toCharArray(), salt, iterations, KEY_LENGTH);

            return MessageDigest.isEqual(storedHash, computedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            // PBEKeySpec holds the parameters for the key derivation.
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);

            // Get an instance of the SecretKeyFactory for the specified algorithm.
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);

            // Generate the secret key (the hash) and return its raw byte representation.
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // These exceptions should not happen if the JRE is configured correctly,
            // as PBKDF2WithHmacSHA256 is a standard algorithm.
            // Throwing an unchecked exception indicates a critical configuration error.
            throw new IllegalStateException("Failed to compute PBKDF2 hash: " + e.getMessage(), e);
        }
    }
}
