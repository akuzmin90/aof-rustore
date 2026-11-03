package com.example.helloandroid.auth;

import android.net.Uri;

import org.json.JSONObject;

public class AppleAuthManager {

    private static final String AUTH_ENDPOINT = "https://appleid.apple.com/auth/authorize";
    static final String CALLBACK_HOST = "apple-callback";
    static final String CALLBACK_SCHEME = AuthRedirectActivity.APPLE_SCHEME;

    private final String servicesId;
    private final String redirectUri;

    private String state;

    public AppleAuthManager(String servicesId, String redirectUri) {
        this.servicesId = servicesId;
        this.redirectUri = redirectUri;
    }

    public Uri buildAuthUri() {
        state = PkceUtil.random(24);

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", servicesId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code id_token")
                .appendQueryParameter("response_mode", "fragment")
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", PkceUtil.random(24))
                .build();
    }

    public boolean matches(Uri uri) {
        return uri != null
                && CALLBACK_SCHEME.equalsIgnoreCase(uri.getScheme())
                && CALLBACK_HOST.equalsIgnoreCase(uri.getHost());
    }

    public JSONObject read(Uri redirect) {
        String returnedState = redirect.getQueryParameter("state");
        if (state == null || !state.equals(returnedState)) {
            return AuthController.error("state_mismatch");
        }

        String error = redirect.getQueryParameter("error");
        if (error != null) {
            return AuthController.error(error);
        }

        String idToken = redirect.getQueryParameter("id_token");
        if (idToken == null || idToken.isEmpty()) {
            return AuthController.error("no_id_token");
        }

        try {
            JSONObject result = new JSONObject();
            result.put("idToken", idToken);
            return result;
        } catch (Exception e) {
            return AuthController.error("bad_id_token");
        }
    }
}
