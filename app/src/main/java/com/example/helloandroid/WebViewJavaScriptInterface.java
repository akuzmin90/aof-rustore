package com.example.helloandroid;

import android.content.Context;
import android.webkit.JavascriptInterface;

/*
 * JavaScript Interface. Web code can access methods in here
 * (as long as they have the @JavascriptInterface annotation)
 */
public class WebViewJavaScriptInterface{

    private Context context;

    /*
     * Need a reference to the context in order to sent a post message
     */
    public WebViewJavaScriptInterface(Context context){
        this.context = context;
    }

    /*
     * This method can be called from Android. @JavascriptInterface
     * required after SDK version 17.
     */
    @JavascriptInterface
    public void makeToast(String message, boolean lengthLong){
        android.util.Log.d("WebView", message);
    }
}
