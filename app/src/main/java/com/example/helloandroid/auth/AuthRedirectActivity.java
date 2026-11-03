package com.example.helloandroid.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.example.helloandroid.MainActivity;

public class AuthRedirectActivity extends Activity {

    public static final String APPLE_SCHEME = "com.example.helloandroid.auth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent forward = new Intent(this, MainActivity.class);
        forward.setData(getIntent().getData());
        forward.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(forward);

        finish();
    }
}
