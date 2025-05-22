package com.example.helloandroid;

import android.app.Activity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.example.helloandroid.api.DataModel;
import com.example.helloandroid.api.RetrofitAPI;
import com.vk.id.AccessToken;
import com.vk.id.OAuth;
import com.vk.id.VKID;
import com.vk.id.VKIDAuthFail;
import com.vk.id.auth.AuthCodeData;
import com.vk.id.auth.Prompt;
import com.vk.id.auth.VKIDAuthCallback;
import com.vk.id.auth.VKIDAuthParams;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.rustore.sdk.*;
import ru.rustore.sdk.billingclient.RuStoreBillingClient;
import ru.rustore.sdk.billingclient.RuStoreBillingClientFactory;
import ru.rustore.sdk.billingclient.model.purchase.PaymentResult;
import ru.rustore.sdk.billingclient.model.purchase.PurchaseState;
import ru.rustore.sdk.billingclient.usecase.PurchasesUseCase;

/*
 * JavaScript Interface. Web code can access methods in here
 * (as long as they have the @JavascriptInterface annotation)
 */
public class WebViewJavaScriptInterface{
    private static final String TAG = "RuStoreBillingClient";
    private final Activity activity;
    RuStoreBillingClient billingClient;
    VKID VKIDInstance;
    LifecycleOwner lifecycleOwner;

    /*
     * Need a reference to the context in order to sent a post message
     */
    public WebViewJavaScriptInterface(Activity activity, LifecycleOwner lifecycleOwner) {
        final String consoleApplicationId = "5670079";
        final String deeplinkScheme = "zazer.mobi";

        final boolean debugLogs = false;

        this.activity = activity;

        billingClient = RuStoreBillingClientFactory.INSTANCE.create(
                activity.getApplicationContext(),
                consoleApplicationId,
                deeplinkScheme
                );

        VKID.Companion.init(activity.getApplicationContext());
        VKIDInstance = VKID.Companion.getInstance();
        this.lifecycleOwner = lifecycleOwner;
    }

    /*
     * This method can be called from Android. @JavascriptInterface
     * required after SDK version 17.
     */
    @JavascriptInterface
    public void makeToast(String message, boolean lengthLong){
        android.util.Log.d("WebView", message);
    }

    @JavascriptInterface
    public void initiateAuth() {
        //Toast.makeText(activity.getApplicationContext(),
        //        "AUTH", Toast.LENGTH_SHORT).show();
        String token;
        String userId;
        VKIDAuthCallback vkAuthCallback = new VKIDAuthCallback() {
            @Override
            public void onAuthCode(@NonNull AuthCodeData authCodeData, boolean b) {
                Log.i("TAG", "onAuthCode");
            }
            @Override
            public void onAuth(AccessToken accessToken) {
                String token = accessToken.getToken();
                Log.i("TAG", "token: " + token);
            }

            @Override
            public void onFail(VKIDAuthFail fail) {
                if (fail instanceof VKIDAuthFail.Canceled) {
                    Log.i("TAG", "onFail if");
                } else {
                    Log.i("TAG", "onFail else");
                }
            }
        };

        VKIDAuthParams vkidAuthParams = new VKIDAuthParams.Builder().build();

        VKIDInstance.authorize(lifecycleOwner, vkAuthCallback, vkidAuthParams);

    }
    @JavascriptInterface
    public void initiatePayment(String productId) {
        PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
        purchasesUseCase.purchaseProduct(productId)
                .addOnSuccessListener(result ->{
                    if (result instanceof PaymentResult.Success) {
                        confirmPurchase((PaymentResult.Success) result);
                    }
                })
                .addOnFailureListener(throwable -> {
                    Toast.makeText(activity.getApplicationContext(),
                            "Покупка отменена или не удалась", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmPurchase(PaymentResult.Success purchase) {
        PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
        purchasesUseCase.confirmPurchase(purchase.getPurchaseId())
                .addOnSuccessListener(confirm -> {
                    PurchasesUseCase purchasesUseCase1 = billingClient.getPurchases();
                    purchasesUseCase1.getPurchaseInfo(purchase.getPurchaseId())
                            .addOnSuccessListener(result -> {
                                if (result.getPurchaseState().equals(PurchaseState.PAID)
                                || result.getPurchaseState().equals(PurchaseState.CONSUMED)) {
                                    postRequest(Constants.GAME_URL, purchase.getProductId());
                                }
                            })
                            .addOnFailureListener(throwable -> {
                                Toast.makeText(activity.getApplicationContext(),
                                        "Ошибка при получении информации о покупке", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(throwable -> {
                    Toast.makeText(activity.getApplicationContext(),
                            "Ошибка при подтверждении покупки: " + throwable.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void postRequest(String url, String product_id) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitAPI retrofitAPI = retrofit.create(RetrofitAPI.class);

        Call call = retrofitAPI.createPost(product_id,"1308266");

        call.enqueue(new Callback<DataModel>() {
            @Override
            public void onResponse(Call<DataModel> call, Response<DataModel> response) {
                DataModel responseFromAPI = response.body();
                }

            @Override
            public void onFailure(Call<DataModel> call, Throwable t) {

            }
        });
    }
}
