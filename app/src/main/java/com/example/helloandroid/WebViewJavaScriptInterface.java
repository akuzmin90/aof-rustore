package com.example.helloandroid;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.helloandroid.api.RetrofitAPI;
import com.example.helloandroid.security.HmacUtil;
import com.example.helloandroid.storage.StorageUtil;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.appmetrica.analytics.Revenue;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.rustore.sdk.pay.PurchaseInteractor;
import ru.rustore.sdk.pay.RuStorePayClient;
import ru.rustore.sdk.pay.UserInteractor;
import ru.rustore.sdk.pay.model.DeveloperPayload;
import ru.rustore.sdk.pay.model.PreferredPurchaseType;
import ru.rustore.sdk.pay.model.ProductId;
import ru.rustore.sdk.pay.model.ProductPurchaseParams;
import ru.rustore.sdk.pay.model.PurchaseId;
import ru.rustore.sdk.pay.model.RuStorePaymentException;
import ru.rustore.sdk.review.RuStoreReviewManager;
import ru.rustore.sdk.review.RuStoreReviewManagerFactory;
import ru.rustore.sdk.review.model.ReviewInfo;

import io.appmetrica.analytics.AppMetrica;
/*
 * JavaScript Interface. Web code can access methods in here
 * (as long as they have the @JavascriptInterface annotation)
 */
public class WebViewJavaScriptInterface{
    private final Activity activity;

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
            List<Map<String, String>> failRequests = StorageUtil.getSavedQueries(activity.getApplicationContext());
            for (Map<String, String> f : failRequests) {
                postRequest(Constants.GAME_URL, f.get("productId"), State.PLAYER_ID, f.get("invoiceId"),
                        f.get("purchaseId"));
            }
        } catch (Exception ignore) {

        }
    }

    @JavascriptInterface
    public void initiatePayment(String productId, String playerId) {
        State.PLAYER_ID = playerId;

        String developerPayloadStr = "PlayerId="+playerId+";ProductId="+productId;
        DeveloperPayload developerPayload = new DeveloperPayload(developerPayloadStr);

        ProductPurchaseParams params = new ProductPurchaseParams(new ProductId(productId), null, null, developerPayload, null, null);

        RuStorePayClient ruStorePayClient = RuStorePayClient.Companion.getInstance();
        PurchaseInteractor purchaseInteractor = ruStorePayClient.getPurchaseInteractor();

        purchaseInteractor.purchase(params, PreferredPurchaseType.ONE_STEP)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, String> params1 = convert(result.getProductId().toString(), playerId, result.getInvoiceId().toString(), result.getPurchaseId().toString());
                        StorageUtil.saveRequest(activity.getApplicationContext(), params1);
                    } catch (Exception ignore) {}

                    postRequest(Constants.GAME_URL, productId, playerId, result.getInvoiceId().toString(), result.getPurchaseId().toString());
                    appmetricaEvent(productId, result.getPurchaseId().toString(), playerId);

                })
                .addOnFailureListener(throwable -> {
                    if (throwable instanceof RuStorePaymentException.ProductPurchaseException) {
                        Toast.makeText(activity, "Ошибка при покупке продукта", Toast.LENGTH_LONG).show();
                    } else if (throwable instanceof RuStorePaymentException.ProductPurchaseCancelled) {
                        Toast.makeText(activity, "Покупка отменена", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, "Ошибка при покупке продукта", Toast.LENGTH_LONG).show();
                    }
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

    @JavascriptInterface
    public int getAppVersion() {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            int versionCode = pInfo.versionCode;
            return versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 27;
    }

    private void confirmPurchase(String purchaseId, String productId, String playerId, String invoiceId) {
        PurchaseInteractor purchaseInteractor = RuStorePayClient.Companion.getInstance().getPurchaseInteractor();

        purchaseInteractor.confirmTwoStepPurchase(
                new PurchaseId(purchaseId),
                null
        ).addOnSuccessListener( success -> {
            postRequest(Constants.GAME_URL, productId, playerId, invoiceId, purchaseId);

            appmetricaEvent(productId, purchaseId, playerId);
            // Логика успешного подтверждения покупки
        }).addOnFailureListener(throwable -> {
            Toast.makeText(activity, "Ошибка при подтверждении покупки, подождите несколько минут или обратитесь в поддержку", Toast.LENGTH_LONG).show();
            // Обработка ошибки
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

    private void appmetricaEvent(String productId, String purchaseId, String playerId) {
        boolean isFirstBuy = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext())
                .getBoolean("first_buy", true);

        String[] parts = productId.split("_");
        String price = parts[parts.length - 1];
        if (isFirstBuy) {
            String firstBuyParameters = "{\"price\":" + price + "}";
            AppMetrica.reportEvent("first_purchase", firstBuyParameters);
            PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext())
                    .edit().putBoolean("first_buy", false).apply();
        }

        Revenue revenue = Revenue.newBuilder(Math.round(Long.parseLong(price) * 1_000_000), Currency.getInstance("RUB"))
                .withProductID(productId)
                .withQuantity(1)
                .withPayload("{\"OrderID\":\""+purchaseId+"\", \"source\":\"Rustore\"}")
                .build();
        AppMetrica.reportRevenue(revenue);

        Map<String, Object> paramsEvent = new HashMap<>();
        paramsEvent.put("playerId", playerId);
        paramsEvent.put("price", price);
        AppMetrica.reportEvent("purchase", paramsEvent);
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
