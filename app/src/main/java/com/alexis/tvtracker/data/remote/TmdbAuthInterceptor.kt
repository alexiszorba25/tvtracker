package com.alexis.tvtracker.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class TmdbAuthInterceptor(private val apiKeyProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return chain.proceed(request)
        }

        val url = request.url.newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
