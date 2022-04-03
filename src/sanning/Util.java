package sanning;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Base64;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class Util {

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

    static byte[] hash(String... vals) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 not available"); }
        for (String val : vals) {
            digest.update(toBytes(val));
        }
        return digest.digest();
    }

    public static SSLContext createSSLContext(String keyStorePath, String keyStorePass, boolean validateServer) throws GeneralSecurityException, IOException {
        // Load key store.
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(new FileInputStream(keyStorePath), keyStorePass.toCharArray());

        // Create key managers.
        KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmFactory.init(keyStore, keyStorePass.toCharArray());
        KeyManager[] keyManagers = kmFactory.getKeyManagers();

        // Create server trust managers.
        TrustManager[] trustManagers;
        if (validateServer) {
            TrustManagerFactory tmFactory = TrustManagerFactory.getInstance("X509");
            tmFactory.init(keyStore);
            trustManagers = tmFactory.getTrustManagers();
        } else {
            trustManagers = new TrustManager[] { new NaiveTrustManager() };
        }

        // Create TLS SSLContext.
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

    private static class NaiveTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
    }

}
