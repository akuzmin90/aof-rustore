package com.example.helloandroid.auth;

import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GoogleAuthManager {

    private static final String TAG = "GoogleAuthManager";

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final String clientId;
    private final String redirectScheme;
    private final String redirectUri;

    private String state;
    private String codeVerifier;

    public GoogleAuthManager(String clientId, String redirectScheme) {
        this.clientId = clientId;
        this.redirectScheme = redirectScheme;
        this.redirectUri = redirectScheme + ":/oauth2redirect";
    }

    public Uri buildAuthUri() {
        state = PkceUtil.random(24);
        codeVerifier = PkceUtil.codeVerifier();

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "openid email profile")
                .appendQueryParameter("code_challenge", PkceUtil.challengeS256(codeVerifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .appendQueryParameter("prompt", "select_account")
                .build();
    }

    public boolean matches(Uri uri) {
        return uri != null && redirectScheme.equalsIgnoreCase(uri.getScheme());
    }

    public JSONObject exchange(Uri redirect) {
        String returnedState = redirect.getQueryParameter("state");
        if (state == null || !state.equals(returnedState)) {
            return AuthController.error("state_mismatch");
        }

        String error = redirect.getQueryParameter("error");
        if (error != null) {
            return AuthController.error(error);
        }

        String code = redirect.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            return AuthController.error("no_code");
        }

        try {
            FormBody body = new FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("client_id", clientId)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", codeVerifier)
                    .build();

            Request request = new Request.Builder()
                    .url(TOKEN_ENDPOINT)
                    .post(body)
                    .build();

            String responseBody;
            boolean ok;
            try (Response response = new OkHttpClient().newCall(request).execute()) {
                ResponseBody rb = response.body();
                responseBody = rb != null ? rb.string() : "";
                ok = response.isSuccessful();
            }

            if (!ok) {
                Log.e(TAG, "Token endpoint returned an error: " + responseBody);
                String reason = "";
                try {
                    reason = new JSONObject(responseBody).optString("error", "");
                } catch (Exception ignore) {
                }
                return AuthController.error(
                        reason.isEmpty() ? "token_exchange_failed" : "token_exchange_failed: " + reason);
            }

            String idToken = new JSONObject(responseBody).optString("id_token", "");
            if (idToken.isEmpty()) {
                return AuthController.error("no_id_token");
            }

            JSONObject claims = JwtUtil.payload(idToken);
            String sub = claims.optString("sub", "");
            if (sub.isEmpty()) {
                return AuthController.error("no_subject");
            }

            JSONObject result = new JSONObject();
            result.put("googleId", sub);
            result.put("givenName", claims.optString("given_name", ""));
            result.put("familyName", claims.optString("family_name", ""));
            result.put("name", claims.optString("name", ""));
            result.put("email", claims.optString("email", ""));
            return result;

        } catch (IOException e) {
            Log.e(TAG, "Token exchange failed", e);
            return AuthController.error("network_error");
        } catch (Exception e) {
            Log.e(TAG, "Could not read the Google id_token", e);
            return AuthController.error("bad_id_token");
        }
    }
}
