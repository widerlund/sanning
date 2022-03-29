package sanning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;

final class Util {

    static final char[] HEX_CHAR = "0123456789abcdef".toCharArray();
    static final SimpleDateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    static final int ISO_8601_LEN = ISO_8601.format(0).length();

    static String toHex(byte... bytes) { return toHex(bytes, 0, bytes.length); }
    static String toHex(byte[] bytes, int offset, int len) {
        char[] hexChars = new char[len * 2];
        for (int j = 0; j < len; j++) {
            int v = bytes[offset + j] & 0xff;
            hexChars[j * 2] = HEX_CHAR[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHAR[v & 0x0f];
        }
        return new String(hexChars);
    }

    static String toBase64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    static byte[] toBytes(CharSequence... s) {
        return String.join("", s).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] toBytes(long val, int len) {
       byte[] buf = new byte[len];
       int shift = (len - 1) * 8;
       for (int i = 0; i < len; i++) {
          buf[i] = (byte) (((val & (0xffL << shift)) >> shift) & 0xffL);
          shift -= 8;
       }
       return buf;
    }

    static String toISO8601(long ts) { return ISO_8601.format(ts); }

    static byte[] hash(byte[]... bytes) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 not available"); }
        for (byte[] b : bytes) {
            digest.update(b);
        }
        return digest.digest();
    }

}
