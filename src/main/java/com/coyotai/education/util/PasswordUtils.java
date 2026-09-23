package com.coyotai.education.util;


import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


public final class PasswordUtils {

    /** The application's encoder: new BCryptPasswordEncoder() = version 2a, cost (strength) 10. */
    public static final int DEFAULT_COST = 10;
    /** Rules for passwords people choose in the application (user forms, change password). */
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    /** BCrypt ignores everything after the first 72 bytes of a password. */
    public static final int BCRYPT_MAX_BYTES = 72;

    /** Development logins created by the demo data, and the default password of new student logins. */
    public static final List<String> KNOWN_PASSWORDS = List.of(
            "admin123", "academic123", "mentor123", "faculty123", "student123", "Welcome@123");

    private static final List<String> COMMON_PASSWORDS = List.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890", "11111111",
            "qwerty123", "qwertyuiop", "iloveyou", "welcome123", "admin@123", "abc12345", "letmein1");

    // Look-alike characters (0/O, 1/l/I) are left out so generated passwords are easy to read out.
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    // Symbols that are safe in .env files and on command lines.
    private static final String SYMBOLS = "@%+=-_.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {
    }

    // =================================================================================================
    // What the tool can do
    // =================================================================================================

    /** BCrypt hash with a fresh random salt, in the same format the application stores. */
    public static String hash(String password, int cost) {
        requirePassword(password);
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("The cost must be between 4 and 31 (the application uses " + DEFAULT_COST + ")");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return bcrypt(password, salt, cost, "2a");
    }

    /** True when the password belongs to the stored hash. Comparing is the only way to "read" a BCrypt hash. */
    public static boolean matches(String password, String storedHash) {
        if (password == null) {
            return false;
        }
        HashParts parts = HashParts.parse(storedHash);
        String computed = bcrypt(password, parts.salt(), parts.cost(), parts.version());
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                parts.hash().getBytes(StandardCharsets.US_ASCII));
    }

    /** A random password with upper and lower case letters, digits and a symbol, starting with a letter. */
    public static String generate(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("Choose a length between " + MIN_LENGTH + " and " + MAX_LENGTH);
        }
        List<Character> chars = new ArrayList<>();
        for (String group : List.of(UPPER, LOWER, DIGITS, SYMBOLS)) {
            chars.add(randomChar(group));
        }
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        while (chars.size() < length) {
            chars.add(randomChar(all));
        }
        Collections.shuffle(chars, RANDOM);
        // A leading letter means the password is never mistaken for a command-line option.
        for (int i = 0; i < chars.size(); i++) {
            if (Character.isLetter(chars.get(i))) {
                Collections.swap(chars, 0, i);
                break;
            }
        }
        StringBuilder password = new StringBuilder(length);
        chars.forEach(password::append);
        return password.toString();
    }

    /** Why the application's forms would refuse this password; empty when they accept it. */
    public static List<String> appProblems(String password) {
        List<String> problems = new ArrayList<>();
        if (password.length() < MIN_LENGTH) {
            problems.add("needs at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("may have at most " + MAX_LENGTH + " characters");
        }
        return problems;
    }

    /** Everything worth knowing about a typed password: the exact characters, strength and acceptance. */
    public static List<String> describe(String password) {
        requirePassword(password);
        int characters = password.codePointCount(0, password.length());
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean space = password.codePoints().anyMatch(PasswordUtils::isSpace);
        boolean symbol = password.codePoints().anyMatch(c -> !Character.isLetterOrDigit(c) && !isSpace(c));
        int groups = (upper ? 1 : 0) + (lower ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);

        List<String> notes = new ArrayList<>();
        String known = KNOWN_PASSWORDS.stream().filter(p -> p.equalsIgnoreCase(password)).findFirst().orElse(null);
        boolean isKnown = known != null && known.equals(password);
        boolean isCommon = COMMON_PASSWORDS.stream().anyMatch(p -> p.equalsIgnoreCase(password));
        boolean repeats = hasRepeatedRun(password);
        boolean sequence = hasSequence(password);

        if (isKnown) {
            notes.add("This is a well-known development password. Never use it on a real deployment.");
        } else if (known != null) {
            notes.add("Only the upper/lower case differs from the development password " + known
                    + " - passwords are case-sensitive, so this is a different password.");
        }
        if (isCommon) {
            notes.add("One of the most common passwords - very easy to guess.");
        }
        if (!password.isEmpty() && (isSpace(password.codePointAt(0)) || isSpace(password.codePointBefore(password.length())))) {
            notes.add("Starts or ends with a space. That is usually a copy/paste mistake; the space is part of the password.");
        }
        if (password.codePoints().anyMatch(c -> c > 126 || c < 32)) {
            notes.add("Contains characters that are not on a standard keyboard (shown as [U+....] above), often from copy/paste.");
        }
        if (repeats) {
            notes.add("Repeats the same character three or more times in a row.");
        }
        if (sequence) {
            notes.add("Contains an easy sequence such as 1234 or abcd.");
        }
        if (bytes > BCRYPT_MAX_BYTES) {
            notes.add("Longer than " + BCRYPT_MAX_BYTES + " bytes: BCrypt only uses the first " + BCRYPT_MAX_BYTES + " bytes.");
        }

        int score = (characters >= MIN_LENGTH ? 1 : 0) + (characters >= 12 ? 1 : 0) + (characters >= 16 ? 1 : 0)
                + (groups >= 3 ? 1 : 0) + (groups == 4 ? 1 : 0) - (repeats || sequence ? 1 : 0);
        if (isKnown || isCommon) {
            score = 0;
        }
        score = Math.max(0, Math.min(5, score));
        String strength = switch (score) {
            case 0, 1 -> "Very weak";
            case 2 -> "Weak";
            case 3 -> "Fair";
            case 4 -> "Good";
            default -> "Strong";
        };
        List<String> problems = appProblems(password);

        List<String> lines = new ArrayList<>();
        lines.add("You typed       : " + visible(password));
        lines.add("Length          : " + characters + " characters" + (bytes != characters ? " (" + bytes + " bytes)" : ""));
        lines.add("Contains        : " + yesNo("uppercase", upper) + ", " + yesNo("lowercase", lower) + ", "
                + yesNo("digits", digit) + ", " + yesNo("symbols", symbol) + (space ? ", spaces" : ""));
        lines.add("Strength        : " + strength + " (" + score + "/5)");
        lines.add("Accepted by app : " + (problems.isEmpty()
                ? "yes (" + MIN_LENGTH + " to " + MAX_LENGTH + " characters)"
                : "no - " + String.join(", ", problems)));
        if (!notes.isEmpty()) {
            lines.add("Notes           :");
            notes.forEach(note -> lines.add("  - " + note));
        }
        return lines;
    }

    /** The password with invisible characters made visible, e.g. a trailing space or a no-break space. */
    public static String visible(String password) {
        if (password.isEmpty()) {
            return "[empty]";
        }
        StringBuilder out = new StringBuilder();
        password.codePoints().forEach(c -> {
            if (c == ' ') {
                out.append("[space]");
            } else if (c == '\t') {
                out.append("[tab]");
            } else if (isSpace(c) || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) {
                out.append(String.format("[U+%04X]", c));
            } else {
                out.appendCodePoint(c);
            }
        });
        return out.toString();
    }

    private static boolean isSpace(int c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private static boolean hasRepeatedRun(String password) {
        for (int i = 2; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1) && password.charAt(i) == password.charAt(i - 2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSequence(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        for (int i = 3; i < lower.length(); i++) {
            char a = lower.charAt(i - 3);
            boolean letterOrDigit = Character.isLetterOrDigit(a);
            if (letterOrDigit && lower.charAt(i - 2) == a + 1 && lower.charAt(i - 1) == a + 2 && lower.charAt(i) == a + 3) {
                return true;
            }
        }
        return false;
    }

    private static String yesNo(String label, boolean present) {
        return label + " " + (present ? "yes" : "no");
    }

    private static char randomChar(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private static void requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("No password given");
        }
    }

    // =================================================================================================
    // BCrypt (the OpenBSD algorithm used by Spring Security)
    // =================================================================================================

    private static final String BCRYPT_ALPHABET = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    /** "OrpheanBeholderScryDoubt" - the text BCrypt encrypts 64 times. */
    private static final int[] MAGIC_TEXT = {0x4f727068, 0x65616e42, 0x65686f6c, 0x64657253, 0x63727944, 0x6f756274};
    private static final int P_SIZE = 18;
    private static final int S_SIZE = 4 * 256;
    private static int[] piWords;

    /** A stored hash split into its parts: $2a$ + cost + $ + 22 characters of salt + 31 characters of hash. */
    private record HashParts(String hash, String version, int cost, byte[] salt) {

        static HashParts parse(String storedHash) {
            String hash = storedHash == null ? "" : storedHash.trim();
            // Forgive quotes that were copied along with the hash.
            if (hash.length() > 1 && (hash.startsWith("'") || hash.startsWith("\"")) && hash.endsWith(hash.substring(0, 1))) {
                hash = hash.substring(1, hash.length() - 1).trim();
            }
            boolean shape = hash.length() == 60 && hash.startsWith("$2") && "aby".indexOf(hash.charAt(2)) >= 0
                    && hash.charAt(3) == '$' && Character.isDigit(hash.charAt(4)) && Character.isDigit(hash.charAt(5))
                    && hash.charAt(6) == '$';
            if (!shape) {
                throw new IllegalArgumentException("That is not a BCrypt hash. A value from users.password looks like "
                        + "$2a$10$ followed by 53 more characters (60 in total). On a PowerShell or bash command line put it in "
                        + "single quotes ('$2a$10$...'), or run the tool without arguments and paste it at the prompt.");
            }
            int cost = Integer.parseInt(hash.substring(4, 6));
            if (cost < 4 || cost > 31) {
                throw new IllegalArgumentException("Unsupported BCrypt cost " + cost);
            }
            return new HashParts(hash, hash.substring(1, 3), cost, decodeBase64(hash.substring(7, 29), 16));
        }
    }

    private static String bcrypt(String password, byte[] salt, int cost, String version) {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        // $2a$, $2b$ and $2y$ hash the password with its terminating zero byte.
        byte[] key = Arrays.copyOf(passwordBytes, passwordBytes.length + 1);
        int[] initial = piWords();
        int[] p = Arrays.copyOfRange(initial, 0, P_SIZE);
        int[] s = Arrays.copyOfRange(initial, P_SIZE, P_SIZE + S_SIZE);

        // "Expensive key setup": the salt and password are mixed in 2^cost times.
        expandKey(p, s, key, salt);
        for (long round = 0, rounds = 1L << cost; round < rounds; round++) {
            expandKey(p, s, key, null);
            expandKey(p, s, salt, null);
        }

        int[] text = MAGIC_TEXT.clone();
        for (int i = 0; i < 64; i++) {
            for (int block = 0; block < text.length; block += 2) {
                encipher(p, s, text, block);
            }
        }
        byte[] raw = new byte[text.length * 4];
        for (int i = 0; i < text.length; i++) {
            raw[i * 4] = (byte) (text[i] >>> 24);
            raw[i * 4 + 1] = (byte) (text[i] >>> 16);
            raw[i * 4 + 2] = (byte) (text[i] >>> 8);
            raw[i * 4 + 3] = (byte) text[i];
        }
        // Only 23 of the 24 bytes are part of the hash, exactly as in the original implementation.
        return "$" + version + "$" + String.format("%02d", cost) + "$" + encodeBase64(salt, 16) + encodeBase64(raw, 23);
    }

    /** Blowfish key schedule; with a salt this is the extra step BCrypt adds to Blowfish. */
    private static void expandKey(int[] p, int[] s, byte[] key, byte[] salt) {
        int[] keyOffset = {0};
        int[] saltOffset = {0};
        int[] block = {0, 0};
        for (int i = 0; i < P_SIZE; i++) {
            p[i] ^= nextWord(key, keyOffset);
        }
        for (int i = 0; i < P_SIZE; i += 2) {
            mixSalt(block, salt, saltOffset);
            encipher(p, s, block, 0);
            p[i] = block[0];
            p[i + 1] = block[1];
        }
        for (int i = 0; i < S_SIZE; i += 2) {
            mixSalt(block, salt, saltOffset);
            encipher(p, s, block, 0);
            s[i] = block[0];
            s[i + 1] = block[1];
        }
    }

    private static void mixSalt(int[] block, byte[] salt, int[] offset) {
        if (salt != null) {
            block[0] ^= nextWord(salt, offset);
            block[1] ^= nextWord(salt, offset);
        }
    }

    /** One Blowfish block encryption of the two words at data[offset] and data[offset + 1]. */
    private static void encipher(int[] p, int[] s, int[] data, int offset) {
        int left = data[offset] ^ p[0];
        int right = data[offset + 1];
        for (int i = 1; i <= 16; i += 2) {
            right ^= feistel(s, left) ^ p[i];
            left ^= feistel(s, right) ^ p[i + 1];
        }
        data[offset] = right ^ p[17];
        data[offset + 1] = left;
    }

    private static int feistel(int[] s, int x) {
        return ((s[x >>> 24] + s[0x100 | ((x >>> 16) & 0xff)]) ^ s[0x200 | ((x >>> 8) & 0xff)]) + s[0x300 | (x & 0xff)];
    }

    /** The next four bytes as a big-endian word, wrapping around to the start. */
    private static int nextWord(byte[] data, int[] offset) {
        int word = 0;
        for (int i = 0; i < 4; i++) {
            word = (word << 8) | (data[offset[0]] & 0xff);
            offset[0] = (offset[0] + 1) % data.length;
        }
        return word;
    }

    /**
     * Blowfish starts from the hexadecimal digits of pi: first the 18 P-array words, then the four
     * S-boxes. Instead of pasting 1,042 constants they are computed once, with
     * pi = 16 atan(1/5) - 4 atan(1/239) in exact integer arithmetic.
     */
    private static synchronized int[] piWords() {
        if (piWords == null) {
            int words = P_SIZE + S_SIZE;
            int guardBits = 64;
            BigInteger one = BigInteger.ONE.shiftLeft(words * 32 + guardBits);
            BigInteger pi = arctanOfInverse(5, one).shiftLeft(4).subtract(arctanOfInverse(239, one).shiftLeft(2));
            BigInteger fraction = pi.subtract(one.multiply(BigInteger.valueOf(3))).shiftRight(guardBits);

            byte[] raw = fraction.toByteArray();
            byte[] bytes = new byte[words * 4];
            int length = Math.min(raw.length, bytes.length);
            System.arraycopy(raw, raw.length - length, bytes, bytes.length - length, length);

            int[] result = new int[words];
            for (int i = 0; i < words; i++) {
                int b = i * 4;
                result[i] = ((bytes[b] & 0xff) << 24) | ((bytes[b + 1] & 0xff) << 16)
                        | ((bytes[b + 2] & 0xff) << 8) | (bytes[b + 3] & 0xff);
            }
            // The published first P-array word and last S-box word.
            if (result[0] != 0x243f6a88 || result[words - 1] != 0x3ac372e6) {
                throw new IllegalStateException("Computed Blowfish constants are wrong");
            }
            piWords = result;
        }
        return piWords;
    }

    /** atan(1/x) scaled by "one": 1/x - 1/(3x^3) + 1/(5x^5) - ... */
    private static BigInteger arctanOfInverse(int x, BigInteger one) {
        BigInteger xSquared = BigInteger.valueOf((long) x * x);
        BigInteger power = one.divide(BigInteger.valueOf(x));
        BigInteger sum = power;
        for (int k = 1; power.signum() != 0; k++) {
            power = power.divide(xSquared);
            BigInteger term = power.divide(BigInteger.valueOf(2L * k + 1));
            sum = (k % 2 == 1) ? sum.subtract(term) : sum.add(term);
        }
        return sum;
    }

    /** BCrypt's base64: its own alphabet, no padding. */
    private static String encodeBase64(byte[] data, int length) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < length; i++) {
            buffer = (buffer << 8) | (data[i] & 0xff);
            bits += 8;
            while (bits >= 6) {
                bits -= 6;
                out.append(BCRYPT_ALPHABET.charAt((buffer >>> bits) & 0x3f));
            }
        }
        if (bits > 0) {
            out.append(BCRYPT_ALPHABET.charAt((buffer << (6 - bits)) & 0x3f));
        }
        return out.toString();
    }

    private static byte[] decodeBase64(String text, int length) {
        byte[] out = new byte[length];
        int buffer = 0;
        int bits = 0;
        int count = 0;
        for (int i = 0; i < text.length() && count < length; i++) {
            int value = BCRYPT_ALPHABET.indexOf(text.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("The hash contains a character BCrypt never uses: " + text.charAt(i));
            }
            buffer = (buffer << 6) | value;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[count++] = (byte) (buffer >>> bits);
            }
        }
        if (count != length) {
            throw new IllegalArgumentException("The salt part of the hash is too short");
        }
        return out;
    }

    // @@COMMAND_LINE@@
}
