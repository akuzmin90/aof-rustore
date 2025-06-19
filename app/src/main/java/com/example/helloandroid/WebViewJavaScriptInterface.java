package com.example.helloandroid;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import com.example.helloandroid.api.RetrofitAPI;
import com.example.helloandroid.security.HmacUtil;
import com.example.helloandroid.storage.StorageUtil;

import java.util.ArrayList;
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
import ru.rustore.sdk.billingclient.model.purchase.Purchase;
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

    private List<String> awaitingConfirmeBuys = new ArrayList<>();
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
//        TODO get current playerId
//        fillAwaitingConfirmeBuys();
//        billingClient.getPurchases().getPurchases()
//                .addOnSuccessListener(listPurchases -> {
//                    for (Purchase purchase : listPurchases) {
//                        Log.i("PURCHASE_INFO", purchase.getPurchaseId());
//                        if(awaitingConfirmeBuys.contains(purchase.getPurchaseId())) {
//                            billingClient.getPurchases().confirmPurchase(purchase.getPurchaseId());
//                            postRequest(Constants.GAME_URL, purchase.getProductId(), ,
//                                    purchase.getInvoiceId(), purchase.getPurchaseId());
//
//                        }
//                    }
//                });

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
                    Toast.makeText(activity.getApplicationContext(),
                            result.toString(), Toast.LENGTH_SHORT).show();
                    if (result instanceof PaymentResult.Success) {
                        confirmPurchase((PaymentResult.Success) result, playerId);
                    } else if (result instanceof PaymentResult.Cancelled) {
                        purchasesUseCase.deletePurchase(((PaymentResult.Cancelled) result).getPurchaseId());
                    } else if (result instanceof PaymentResult.Failure) {
                        purchasesUseCase.deletePurchase(((PaymentResult.Failure) result).getPurchaseId());
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
        try {
            Map<String, String> params = new HashMap<>();
            params.put("productId", purchase.getProductId());
            params.put("playerId", playerId);
            params.put("invoiceId", purchase.getInvoiceId());
            params.put("purchaseId", purchase.getPurchaseId());
            StorageUtil.saveRequest(activity.getApplicationContext(), params);
        } catch (Exception ignore) {}

        PurchasesUseCase purchasesUseCase = billingClient.getPurchases();
        purchasesUseCase.confirmPurchase(purchase.getPurchaseId())
                .addOnSuccessListener(confirm -> {
                    purchasesUseCase.getPurchaseInfo(purchase.getPurchaseId())
                            .addOnSuccessListener(result -> {
                                if (result.getPurchaseState().equals(PurchaseState.PAID)
                                || result.getPurchaseState().equals(PurchaseState.CONSUMED)
                                || result.getPurchaseState().equals(PurchaseState.CONFIRMED)){
                                    postRequest(Constants.GAME_URL, purchase.getProductId(), playerId, purchase.getInvoiceId(), purchase.getPurchaseId());
                                } else {
                                    purchasesUseCase.deletePurchase(purchase.getPurchaseId());
                                }
                            })
                            .addOnFailureListener(throwable -> {
                                purchasesUseCase.deletePurchase(purchase.getPurchaseId());
                                Log.i("ERROR", throwable.getLocalizedMessage());
                                Toast.makeText(activity.getApplicationContext(),
                                        "Ошибка при получении информации о покупке", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(throwable -> {
                    purchasesUseCase.deletePurchase(purchase.getPurchaseId());
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

    private void fillAwaitingConfirmeBuys() {
        awaitingConfirmeBuys.add("d77d9354-646a-4f54-85d3-b56de7078e67");
        awaitingConfirmeBuys.add("1e4d1c55-3a4b-4ec9-8992-f7f97a5631bc");
        awaitingConfirmeBuys.add("88bb669a-508b-4481-9694-3a5292a1bc25");
        awaitingConfirmeBuys.add("ab0ac51e-dc13-4b93-ba5f-862c714ab5f7");
        awaitingConfirmeBuys.add("7d386638-df55-4887-8ad8-83a2d34a9dda");
        awaitingConfirmeBuys.add("e626ca1d-3701-4c61-8c3f-2e9c54ac1d38");
        awaitingConfirmeBuys.add("1b82ffa0-6a39-4388-a8e1-c5bee5015571");
        awaitingConfirmeBuys.add("c9d7ad81-df06-4cbc-ae1d-c6f665e1544f");
        awaitingConfirmeBuys.add("024cf3fa-344b-47da-b27c-ba0c13636161");
        awaitingConfirmeBuys.add("e5bb6b7f-e744-4fbb-8e78-1d189573172a");
        awaitingConfirmeBuys.add("06b44d70-80b6-4ce1-90d5-5e1a8b70c98d");
        awaitingConfirmeBuys.add("d4414f7b-e92f-4a87-9000-a189841b884b");
        awaitingConfirmeBuys.add("2184ee62-373d-4797-aa03-977dadcc8ff5");
        awaitingConfirmeBuys.add("7942114a-9026-4edf-a098-2c0a64f610c1");
        awaitingConfirmeBuys.add("c87aa414-409e-4eb1-803d-0ff26ee52142");
        awaitingConfirmeBuys.add("4a58932a-ba8d-4158-b8c3-ce81e6dbe22a");
        awaitingConfirmeBuys.add("dd14dfeb-6635-4e6b-b55e-fe4865a03c26");
        awaitingConfirmeBuys.add("24876215-b239-450c-a671-36c37274908a");
        awaitingConfirmeBuys.add("19505528-77d0-44c4-a24c-d90f8edce982");
        awaitingConfirmeBuys.add("af4e293c-a487-4244-ac58-a701f4cb19aa");
        awaitingConfirmeBuys.add("9b28da1a-0a2c-4f6f-b1cf-41b9f56e78b1");
        awaitingConfirmeBuys.add("6423c9cc-e6a9-4d0f-9ceb-4ea271d5a05b");
        awaitingConfirmeBuys.add("83253ac3-e04c-41b3-a515-407e0fac8d72");
        awaitingConfirmeBuys.add("eac08735-4669-4377-9f2f-b7dac945abde");
        awaitingConfirmeBuys.add("8a3fae93-8080-42d2-81e4-a442d5282fa1");
        awaitingConfirmeBuys.add("80b2f9c8-e2e9-4ee9-a071-1884d3a0bf78");
        awaitingConfirmeBuys.add("bd46fe2d-88c1-46bd-81f7-db2cda4c301e");
        awaitingConfirmeBuys.add("807b0825-8cc0-412f-98fe-7f410723cb13");
        awaitingConfirmeBuys.add("5e8adece-3fe8-443b-b48a-af947572f6c8");
        awaitingConfirmeBuys.add("6ca2f488-ba6a-4824-91d9-60411325f916");
        awaitingConfirmeBuys.add("9e7db45c-de5f-46c2-8a82-21622f56be21");
        awaitingConfirmeBuys.add("a786a0e5-6d3d-40f5-bb22-aeb1e5e10dc3");
        awaitingConfirmeBuys.add("bd5ed90e-dcc0-46d9-ac72-dcb0a89a9e00");
        awaitingConfirmeBuys.add("18058893-fe28-405a-93c9-8b3671dde264");
        awaitingConfirmeBuys.add("9d85f3f4-ceb0-43ca-84c7-cb0f92a82d2b");
        awaitingConfirmeBuys.add("34da26e5-2f8a-4a76-81e9-fc77e0ba7f24");
        awaitingConfirmeBuys.add("ccac07d2-3da8-4dc6-9961-3a1b431afed2");
        awaitingConfirmeBuys.add("69f51940-20e0-46cd-9db9-5694c68b6674");
        awaitingConfirmeBuys.add("0d8573cf-2fdc-44cc-bf2e-d0754ba51547");
        //testing buys
        awaitingConfirmeBuys.add("437260be-4d53-4952-b90c-279ca22c87bd");
        awaitingConfirmeBuys.add("d660b2c4-47a8-4120-a533-e985f387b011");
        awaitingConfirmeBuys.add("09f5d2b5-bca8-43b4-bbd2-f1cd9bfcb1e6");
        awaitingConfirmeBuys.add("437260be-4d53-4952-b90c-279ca22c87bd");
        awaitingConfirmeBuys.add("d660b2c4-47a8-4120-a533-e985f387b011");
        awaitingConfirmeBuys.add("60114c27-f897-40c0-ab90-6f406b4aeff7");
        awaitingConfirmeBuys.add("deb7154d-135a-458d-8ac9-144e52a4d6cd");
        awaitingConfirmeBuys.add("671a5fb8-a030-4065-969a-fdbb57258089");
    }
}
