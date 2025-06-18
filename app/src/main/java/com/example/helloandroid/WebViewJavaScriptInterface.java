package com.example.helloandroid;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.example.helloandroid.api.RetrofitAPI;
import com.example.helloandroid.security.HmacUtil;
import com.example.helloandroid.storage.StorageUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.rustore.sdk.billingclient.RuStoreBillingClient;
import ru.rustore.sdk.billingclient.RuStoreBillingClientFactory;
import ru.rustore.sdk.billingclient.model.purchase.PaymentResult;
import ru.rustore.sdk.billingclient.model.purchase.PurchaseState;
import ru.rustore.sdk.billingclient.usecase.PurchasesUseCase;
import ru.rustore.sdk.review.RuStoreReviewManager;
import ru.rustore.sdk.review.RuStoreReviewManagerFactory;
import ru.rustore.sdk.review.model.ReviewInfo;

/*
 * JavaScript Interface. Web code can access methods in here
 * (as long as they have the @JavascriptInterface annotation)
 */
public class WebViewJavaScriptInterface{
    private static final String TAG = "RuStoreBillingClient";
    private final Activity activity;
    RuStoreBillingClient billingClient;

    private RuStoreReviewManager reviewManager;
    private ReviewInfo reviewInfo = null;

    /*
     * Need a reference to the context in order to sent a post message
     */
    public WebViewJavaScriptInterface(Activity activity) {
        final String consoleApplicationId = "5670079";
        final String deeplinkScheme = "zazer.mobi";

        final boolean debugLogs = false;

        this.activity = activity;

        billingClient = RuStoreBillingClientFactory.INSTANCE.create(
                activity.getApplicationContext(),
                consoleApplicationId,
                deeplinkScheme
                );

        List<Map<String, String>> failRequests = StorageUtil.getSavedQueries(activity.getApplicationContext());
        for (Map<String, String> failRequest : failRequests) {
            billingClient.getPurchases().confirmPurchase(failRequest.get("purchaseId"));
            postRequest(Constants.GAME_URL, failRequest.get("productId"), failRequest.get("playerId"),
            failRequest.get("invoiceId"), failRequest.get("purchaseId"));
        }

        reviewManager = RuStoreReviewManagerFactory.INSTANCE.create(activity.getApplicationContext());
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
    public void initiatePayment(String productId, String playerId) {
        PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
        purchasesUseCase.purchaseProduct(productId)
                .addOnSuccessListener(result ->{
                    postRequestLog(Constants.GAME_URL, productId, playerId, "INIT_PAYMENT");
                    if (result instanceof PaymentResult.Success) {
                        confirmPurchase((PaymentResult.Success) result, playerId);
                    }
                })
                .addOnFailureListener(throwable -> {
                    Log.i("ERROR", throwable.getLocalizedMessage());
                    Toast.makeText(activity.getApplicationContext(),
                            "Покупка отменена или не удалась", Toast.LENGTH_SHORT).show();
                });
    }

    @JavascriptInterface
    public boolean isWebView() {
        return true;
    }

    @JavascriptInterface
    public void initRateApp() {
        if (reviewInfo != null) return;

        reviewManager.requestReviewFlow()
                .addOnSuccessListener(reviewInfo -> {
                    Log.i("requestReviewFlow", reviewInfo.toString());
                    this.reviewInfo = reviewInfo;
                    reviewManager.launchReviewFlow(reviewInfo)
                            .addOnSuccessListener(unit -> Log.w("ReviewFlow", "Review Flow started UNIT: " + unit.toString()))
                            .addOnFailureListener(throwable -> Log.e("launchReviewFlow", "Review Flow error" + throwable));
                })
                .addOnFailureListener(throwable -> {
                    Log.w("requestReviewFlow", throwable.toString());
                });
    }

    private void confirmPurchase(PaymentResult.Success purchase, String playerId) {
        Map<String, String> params = new HashMap<>();
        params.put("productId", purchase.getProductId());
        params.put("playerId", playerId);
        params.put("invoiceId", purchase.getInvoiceId());
        params.put("purchaseId", purchase.getPurchaseId());
        StorageUtil.saveRequest(activity.getApplicationContext(), params);

        PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
        purchasesUseCase.confirmPurchase(purchase.getPurchaseId())
                .addOnSuccessListener(confirm -> {
                    postRequestLog(Constants.GAME_URL, purchase.getProductId(), playerId, purchase.getInvoiceId());
                    purchasesUseCase.getPurchaseInfo(purchase.getPurchaseId())
                            .addOnSuccessListener(result -> {
                                postRequestLog(Constants.GAME_URL, purchase.getProductId(), playerId, result.getPurchaseState().name());
                                if (result.getPurchaseState().equals(PurchaseState.PAID)
                                || result.getPurchaseState().equals(PurchaseState.CONSUMED)
                                || result.getPurchaseState().equals(PurchaseState.CONFIRMED)){
                                    postRequest(Constants.GAME_URL, purchase.getProductId(), playerId, purchase.getInvoiceId(), purchase.getPurchaseId());
                                    postRequestLog(Constants.GAME_URL, purchase.getProductId(), playerId, "CONFIRMED BUY");
                                }
                            })
                            .addOnFailureListener(throwable -> {
                                Log.i("ERROR", throwable.getLocalizedMessage());
                                Toast.makeText(activity.getApplicationContext(),
                                        "Ошибка при получении информации о покупке", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(throwable -> {
                    Log.i("ERROR", throwable.getLocalizedMessage());
                    Toast.makeText(activity.getApplicationContext(),
                            "Ошибка при подтверждении покупки: " + throwable.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void postRequest(String url, String productId, String playerId, String invoiceId,
                             String purchaseId) {
        String signature = HmacUtil.generateSignature(productId + playerId + invoiceId);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitAPI retrofitAPI = retrofit.create(RetrofitAPI.class);

        Call call = retrofitAPI.createPost(signature, productId, playerId, invoiceId);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Map<String, String> params = new HashMap<>();

                params.put("productId", productId);
                params.put("playerId", playerId);
                params.put("invoiceId", invoiceId);
                params.put("purchaseId", purchaseId);

                StorageUtil.removeQuery(activity.getApplicationContext(), params);
                Log.i("BILLING", "Success buy");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable throwable) {
                Log.i("ERROR", throwable.getLocalizedMessage());
                Toast.makeText(activity.getApplicationContext(),
                        "Ошибка при подтверждении покупки: " + throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();

            }
        });
    }

    private void postRequestLog(String url, String productId, String playerId, String invoiceId) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitAPI retrofitAPI = retrofit.create(RetrofitAPI.class);

        Call call = retrofitAPI.createPostLogs(productId, playerId, invoiceId);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                Log.i("LOGGING", "Success added log");
            }

            @Override
            public void onFailure(Call<Void> call, Throwable throwable) {
                Log.i("ERROR", throwable.getLocalizedMessage());
                Toast.makeText(activity.getApplicationContext(),
                        "Ошибка при подтверждении покупки: " + throwable.getLocalizedMessage(), Toast.LENGTH_LONG).show();

            }
        });
    }
}
