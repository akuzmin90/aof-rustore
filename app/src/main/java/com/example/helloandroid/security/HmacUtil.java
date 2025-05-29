package com.example.helloandroid.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacUtil {
    private static final String SECRET = "DeFX@cQ_?JUdCQfQHgD~5npAVUD)W,";

    public static String generateSignature(String data) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC", e);
        }
    }
}
