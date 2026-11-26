package com.zippy.ear;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * v1.3: Stripe-style webhook signing for any non-whsec_ secret.
 *
 * Header: X-Zippy-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256("<t>.<raw body>")>
 * The secret is used as raw UTF-8 bytes. whsec_ secrets keep using Standard
 * Webhooks headers (handled in Webhook.sign before this is called).
 */
public final class Sig {
    public static final String HEADER = "X-Zippy-Signature";

    private Sig() {}

    /** Called from Webhook.sign() for non-empty secrets that do not start with whsec_. */
    public static void stripe(HttpURLConnection conn, String secret, byte[] body) {
        long t = System.currentTimeMillis() / 1000L;
        conn.setRequestProperty(HEADER, header(secret, t, body));
    }

    public static String header(String secret, long t, byte[] body) {
        return "t=" + t + ",v1=" + hmacHex(secret, t, body);
    }

    public static String hmacHex(String secret, long t, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((t + ".").getBytes(StandardCharsets.UTF_8));
            byte[] out = mac.doFinal(body);
            char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(hex[(b >> 4) & 0xf]).append(hex[b & 0xf]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
