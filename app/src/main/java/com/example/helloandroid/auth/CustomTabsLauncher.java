package com.example.helloandroid.auth;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;

public final class CustomTabsLauncher {

    private CustomTabsLauncher() {
    }

    public static boolean launch(Activity activity, Uri uri) {
        try {
            CustomTabsIntent intent = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            intent.launchUrl(activity, uri);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            } catch (ActivityNotFoundException e2) {
                return false;
            }
        }
    }
}
