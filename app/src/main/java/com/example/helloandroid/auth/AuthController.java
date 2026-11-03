package com.example.helloandroid.auth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import com.example.helloandroid.BuildConfig;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthController {

    private static final String TAG = "AuthController";

    private static final String GOOGLE_CALLBACK = "onGoogleSignInResult";
    private static final String APPLE_CALLBACK = "onAppleSignInResult";

    private final Activity activity;
    private final WebView webView;
    private final GoogleAuthManager google;
    private final AppleAuthManager apple;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String pending;

    public AuthController(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.google = new GoogleAuthManager(
                BuildConfig.GOOGLE_ANDROID_CLIENT_ID, BuildConfig.GOOGLE_REDIRECT_SCHEME);
        this.apple = new AppleAuthManager(
                BuildConfig.APPLE_SERVICES_ID, BuildConfig.APPLE_REDIRECT_URI);
    }

    public static boolean isAuthRedirect(Intent intent) {
        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null) {
            return false;
        }
        return BuildConfig.GOOGLE_REDIRECT_SCHEME.equalsIgnoreCase(uri.getScheme())
                || AppleAuthManager.CALLBACK_SCHEME.equalsIgnoreCase(uri.getScheme());
    }

    static JSONObject error(String message) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("error", message);
        } catch (Exception ignore) {
        }
        return payload;
    }

    public void startGoogle() {
        activity.runOnUiThread(() -> start("google", GOOGLE_CALLBACK, google.buildAuthUri()));
    }

    public void startApple() {
        activity.runOnUiThread(() -> start("apple", APPLE_CALLBACK, apple.buildAuthUri()));
    }

    private void start(String flow, String callback, Uri authUri) {
        pending = flow;
        if (!CustomTabsLauncher.launch(activity, authUri)) {
            pending = null;
            deliver(callback, error("no_browser"));
        }
    }

    public boolean handleRedirect(Intent intent) {
        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null) {
            return false;
        }

        if (google.matches(uri)) {
            pending = null;
            executor.execute(() -> {
                JSONObject payload = google.exchange(uri);
                deliver(GOOGLE_CALLBACK, payload);
            });
            return true;
        }

        if (apple.matches(uri)) {
            pending = null;
            deliver(APPLE_CALLBACK, apple.read(uri));
            return true;
        }

        return false;
    }

    public void onResume() {
        if (pending == null) {
            return;
        }
        String callback = "google".equals(pending) ? GOOGLE_CALLBACK : APPLE_CALLBACK;
        pending = null;
        deliver(callback, error("cancelled"));
    }

    private void deliver(String callback, JSONObject payload) {
        final String script = "window." + callback + " && window." + callback
                + "(" + JSONObject.quote(payload.toString()) + ")";

        activity.runOnUiThread(() -> {
            try {
                webView.evaluateJavascript(script, null);
            } catch (Exception e) {
                Log.e(TAG, "Could not deliver the auth result to the page", e);
            }
        });
    }
}
