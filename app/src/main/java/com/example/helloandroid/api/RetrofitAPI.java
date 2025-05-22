package com.example.helloandroid.api;

import retrofit2.Call;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RetrofitAPI {
    @Headers("Accept: application/xml")
    @POST("api/integration/rustore/buy")
    public default Call<DataModel> createPost(@Query("product_id") String product_id,
                                              @Query("user_id") String user_id) {
        return null;
    }
}
