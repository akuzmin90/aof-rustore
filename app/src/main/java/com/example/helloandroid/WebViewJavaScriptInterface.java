package com.example.helloandroid;

import android.app.Activity;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.helloandroid.api.RetrofitAPI;
import com.example.helloandroid.security.HmacUtil;
import com.example.helloandroid.storage.StorageUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.rustore.sdk.billingclient.RuStoreBillingClient;
import ru.rustore.sdk.billingclient.RuStoreBillingClientFactory;
import ru.rustore.sdk.billingclient.model.purchase.PaymentResult;
import ru.rustore.sdk.billingclient.model.purchase.Purchase;
import ru.rustore.sdk.billingclient.model.purchase.PurchaseState;
import ru.rustore.sdk.billingclient.usecase.PurchasesUseCase;
import ru.rustore.sdk.billingclient.usecase.UserInfoUseCase;
import ru.rustore.sdk.review.RuStoreReviewManager;
import ru.rustore.sdk.review.RuStoreReviewManagerFactory;
import ru.rustore.sdk.review.model.ReviewInfo;

/*
 * JavaScript Interface. Web code can access methods in here
 * (as long as they have the @JavascriptInterface annotation)
 */
public class WebViewJavaScriptInterface{
    private final Activity activity;
    RuStoreBillingClient billingClient;

    private final RuStoreReviewManager reviewManager;
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

        reviewManager = RuStoreReviewManagerFactory.INSTANCE.create(activity.getApplicationContext());
    }

    @JavascriptInterface
    public void initPaymentApi(String playerId) {
        State.PLAYER_ID = playerId;

        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkPayments();
            }
        }, 0L, 10000L);
    }

    synchronized public void checkPayments() {
        try {
            UserInfoUseCase uiu = billingClient.getUserInfo();
            uiu.getAuthorizationStatus().addOnSuccessListener(status -> {
                if (status.getAuthorized()) {
                    PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
                    purchasesUseCase.getPurchases()
                            .addOnSuccessListener(purchases -> {
                                for (Purchase purchase : purchases) {
                                    if (purchase != null && purchase.getPurchaseId() != null && State.PLAYER_ID != null) {
                                        confirmPurchase(purchasesUseCase, purchase.getPurchaseId());
                                    }
                                }
                            })
                            .addOnFailureListener(throwable -> {
                                logThrowable(throwable, "Покупка отменена или не удалась");
                            });
                }
            });

            List<Map<String, String>> failRequests = StorageUtil.getSavedQueries(activity.getApplicationContext());
            for (Map<String, String> f : failRequests) {
                postRequest(Constants.GAME_URL, f.get("productId"), State.PLAYER_ID, f.get("invoiceId"), f.get("purchaseId"));
            }
        } catch (Exception ignore) {

        }
    }

    @JavascriptInterface
    public void initiatePayment(String productId, String playerId) {
        State.PLAYER_ID = playerId;
        PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
        purchasesUseCase.purchaseProduct(productId)
                .addOnSuccessListener(result ->{
                    if (result instanceof PaymentResult.Success) {
                        confirmPurchase(purchasesUseCase, ((PaymentResult.Success) result).getPurchaseId());
                    }
                })
                .addOnFailureListener(throwable -> {
                    logThrowable(throwable, "Покупка отменена или не удалась");
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

    private void confirmPurchase(PurchasesUseCase purchasesUseCase, String purchaseId) {
        purchasesUseCase.getPurchaseInfo(purchaseId)
                .addOnSuccessListener(p -> {
                    PurchaseState state = p.getPurchaseState();
                    if (state != null && (state.equals(PurchaseState.PAID) || state.equals(PurchaseState.CONSUMED) || state.equals(PurchaseState.CONFIRMED))) {
                        try {
                            Map<String, String> params = convert(p.getProductId(), State.PLAYER_ID, p.getInvoiceId(), purchaseId);
                            StorageUtil.saveRequest(activity.getApplicationContext(), params);
                        } catch (Exception ignore) {}

                        postRequest(Constants.GAME_URL, p.getProductId(), State.PLAYER_ID, p.getInvoiceId(), p.getPurchaseId());
                        purchasesUseCase.confirmPurchase(p.getPurchaseId());
                    }
                })
                .addOnFailureListener(throwable -> {
                    logThrowable(throwable, "Ошибка при получении информации о покупке");
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

        Call<Void> c = retrofitAPI.createPost(signature, productId, playerId, invoiceId);

        c.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Map<String, String> params = convert(productId, playerId, invoiceId, purchaseId);
                StorageUtil.removeQuery(activity.getApplicationContext(), params);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                logThrowable(throwable, "Ошибка при подтверждении покупки: ");
            }
        });
    }

    private void postRequestLog(String url, String productId, String playerId, String invoiceId) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitAPI retrofitAPI = retrofit.create(RetrofitAPI.class);

        Call<Void> call = retrofitAPI.createPostLogs(productId, playerId, invoiceId);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                Log.i("LOGGING", "Success added log");
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                logThrowable(throwable, "Ошибка при подтверждении покупки: ");
            }
        });
    }

    private void logThrowable(Throwable throwable, String errorText) {
        if (throwable.getLocalizedMessage() != null) {
            Log.i("ERROR", throwable.getLocalizedMessage());
        }
    }

    private static Map<String, String> convert(String productId, String playerId, String invoiceId, String purchaseId) {
        Map<String, String> params = new HashMap<>();

        params.put("productId", productId);
        params.put("playerId", playerId);
        params.put("invoiceId", invoiceId);
        params.put("purchaseId", purchaseId);
        return params;
    }

}
