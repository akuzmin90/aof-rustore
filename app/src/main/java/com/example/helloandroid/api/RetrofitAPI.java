package com.example.helloandroid.api;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RetrofitAPI {
    @Headers("Accept: application/xml")
    @POST("api/integration/rustore/buy")
    public Call<Void> createPost(@Header("X-Timestamp") long timestamp,
                                      @Header("X-Signature") String signature,
                                      @Query("product_id") String product_id,
                                      @Query("player_id") String playerId,
                                      @Query("invoice_id") String invoiceId);
}
