package com.example.helloandroid.auth;

import android.util.Base64;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class PkceUtil {

    private static final int BASE64_FLAGS = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PkceUtil() {
    }

    public static String random(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.encodeToString(buf, BASE64_FLAGS);
    }

    public static String codeVerifier() {
        return random(64);
    }

    public static String challengeS256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(Charset.forName("US-ASCII")));
            return Base64.encodeToString(hash, BASE64_FLAGS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
