package com.example.helloandroid.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StorageUtil {
    private static final String PREFS_NAME = "retry_prefs";
    private static final String KEY_QUERY_LIST = "query_list";

    public static void saveRequest(Context context, Map<String, String> params) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();

        List<Map<String, String>> currentList = getSavedQueries(context);
        if (currentList == null) {
            currentList = new ArrayList<>();
        }

        if (!currentList.contains(params))
            currentList.add(params);

        String json = gson.toJson(currentList);
        prefs.edit().putString(KEY_QUERY_LIST, json).apply();
    }
    public static List<Map<String, String>> getSavedQueries(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_QUERY_LIST, null);
        if (json != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Map<String, String>>>() {}.getType();
            return gson.fromJson(json, type);
        }
        return new ArrayList<>();
    }
    public static void removeQuery(Context context, Map<String, String> params) {
        List<Map<String, String>> currentList = getSavedQueries(context);
        currentList.remove(params);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        String json = gson.toJson(currentList);
        prefs.edit().putString(KEY_QUERY_LIST, json).apply();
    }
}
