package com.example.helloandroid.auth;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.Charset;

public final class JwtUtil {

    private JwtUtil() {
    }

    public static JSONObject payload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed JWT");
        }
        byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        return new JSONObject(new String(decoded, Charset.forName("UTF-8")));
    }
}
