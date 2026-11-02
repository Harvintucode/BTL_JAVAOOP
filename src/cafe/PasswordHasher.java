package cafe;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Băm mật khẩu với salt riêng bằng thuật toán có sẵn trong JDK. */
public final class PasswordHasher {
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(char[] password) {
        if (password == null || password.length == 0)
            throw new IllegalArgumentException("Mật khẩu không được để trống.");
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] digest = derive(password, salt, ITERATIONS);
        try {
            return PREFIX + "$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(digest);
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    public static boolean verify(char[] password, String encoded) {
        if (password == null) return false;
        Parts parts = parse(encoded);
        byte[] actual = derive(password, parts.salt(), parts.iterations());
        try {
            return MessageDigest.isEqual(parts.digest(), actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(parts.digest(), (byte) 0);
        }
    }

    static void validateEncoding(String encoded) {
        Parts parts = parse(encoded);
        Arrays.fill(parts.digest(), (byte) 0);
    }

    private static Parts parse(String encoded) {
        try {
            if (encoded == null || encoded.length() > 256)
                throw new IllegalArgumentException();
            String[] fields = encoded.split("\\$", -1);
            if (fields.length != 4 || !PREFIX.equals(fields[0]))
                throw new IllegalArgumentException();
            int iterations = Integer.parseInt(fields[1]);
            if (iterations < 100_000 || iterations > 2_000_000)
                throw new IllegalArgumentException();
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] digest = Base64.getDecoder().decode(fields[3]);
            if (salt.length != SALT_BYTES || digest.length != HASH_BYTES)
                throw new IllegalArgumentException();
            return new Parts(iterations, salt, digest);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Dữ liệu băm mật khẩu không hợp lệ.", ex);
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("JDK không hỗ trợ băm mật khẩu PBKDF2.", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private record Parts(int iterations, byte[] salt, byte[] digest) {}
}
