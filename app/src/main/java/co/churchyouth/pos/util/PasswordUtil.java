package co.churchyouth.pos.util;

import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing for the local staff accounts.
 *
 * Deliberately PBKDF2WithHmacSHA1, not SHA256: the SHA256 family of
 * PBKDF2 algorithms was only added to Android in API 26 (Oreo). The
 * Sunmi V2 Pro ships Android 7.1 = API 25, so SHA256 would throw
 * NoSuchAlgorithmException at runtime on the actual target device — this
 * was verified against Android's own published SecretKeyFactory support
 * table before writing this class. The iteration count is raised to
 * compensate for SHA-1 being cheaper per round.
 *
 * Also deliberately android.util.Base64, not java.util.Base64: the
 * latter is *also* API 26+ only, for the same reason. NO_WRAP is used
 * throughout so the encoded string never contains embedded newlines,
 * since it's stored as a single SQLite TEXT column.
 *
 * This runs on a background thread (see LoginActivity) — never call
 * hash()/verify() from the UI thread, they take a few hundred
 * milliseconds by design.
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private PasswordUtil() { }

    public static final class Hashed {
        public final String hash;
        public final String salt;
        Hashed(String hash, String salt) { this.hash = hash; this.salt = salt; }
    }

    public static Hashed hash(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            random.nextBytes(salt);
            byte[] derived = pbkdf2(password.toCharArray(), salt);
            return new Hashed(
                Base64.encodeToString(derived, Base64.NO_WRAP),
                Base64.encodeToString(salt, Base64.NO_WRAP)
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Should never happen — PBKDF2WithHmacSHA1 is guaranteed since API 10.
            throw new RuntimeException("Password hashing unavailable on this device", e);
        }
    }

    public static boolean verify(String password, String storedHashB64, String storedSaltB64) {
        try {
            byte[] salt = Base64.decode(storedSaltB64, Base64.NO_WRAP);
            byte[] expected = Base64.decode(storedHashB64, Base64.NO_WRAP);
            byte[] actual = pbkdf2(password.toCharArray(), salt);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
        return skf.generateSecret(spec).getEncoded();
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) result |= a[i] ^ b[i];
        return result == 0;
    }
}
