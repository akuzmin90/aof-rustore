package com.example.helloandroid;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

import io.appmetrica.analytics.AppMetrica;
import io.appmetrica.analytics.AppMetricaConfig;
import ru.rustore.sdk.pay.IntentInteractor;
import ru.rustore.sdk.pay.RuStorePayClient;
import ru.rustore.sdk.pay.RuStorePayClientProvider;

public class MainActivity extends AppCompatActivity {

//    private static final String API_KEY = "fsdfsodfni43of43";
    private static final String API_KEY = "58cceae9-e3aa-4099-b49b-3fab2c8a5b7f";
    private IntentInteractor intentInteractor;

    private WebView webView = null;
    private LinearLayout noInternetLayout;
    private Button retryButton;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        intentInteractor = RuStorePayClient.Companion.getInstance().getIntentInteractor();

        if (savedInstanceState == null) {
            intentInteractor.proceedIntent(getIntent());
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        this.webView = findViewById(R.id.webview);
        noInternetLayout = findViewById(R.id.no_internet_layout);
        retryButton = findViewById(R.id.btn_retry);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        WebViewClientImpl webViewClient = new WebViewClientImpl(this);
        webView.setWebViewClient(webViewClient);

        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkConnectionAndLoad();
            }
        });

        checkConnectionAndLoad();

        // Initializing the AppMetrica SDK.
        AppMetricaConfig config = AppMetricaConfig.newConfigBuilder(API_KEY).build();
        AppMetrica.activate(this, config);
        AppMetrica.enableActivityAutoTracking(getApplication());

        webView.getSettings().setJavaScriptEnabled(true);
        AppMetrica.initWebViewReporting(webView);
        webView.loadUrl(Constants.GAME_URL);

        webView.addJavascriptInterface(new WebViewJavaScriptInterface(this), "app");

        boolean isFirstRun = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("first_run", true);

        if (isFirstRun) {
            AppMetrica.reportEvent("first_launch");
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean("first_run", false).apply();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && this.webView.canGoBack()) {
            this.webView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        intentInteractor.proceedIntent(intent);
    }
    private void checkConnectionAndLoad() {
        if (isConnected()) {
            webView.setVisibility(View.VISIBLE);
            noInternetLayout.setVisibility(View.GONE);
            webView.loadUrl(Constants.GAME_URL);
        } else {
            webView.setVisibility(View.GONE);
            noInternetLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network network = cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        return false;
    }
}