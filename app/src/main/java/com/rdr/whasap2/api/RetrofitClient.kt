package com.rdr.whasap2.api

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "https://discord.com/api/v10/"

    fun getInstance(context: Context): DiscordApi {
        val prefs = context.getSharedPreferences("whasap_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("DISCORD_TOKEN", "") ?: ""

        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", token)
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        // Enable TLS 1.2 for older devices using Conscrypt
        if (android.os.Build.VERSION.SDK_INT < 22) {
            try {
                // Conscrypt is already installed as provider in WelcomeActivity
                // Just need to get TLS context - Conscrypt will be used automatically
                val sc = javax.net.ssl.SSLContext.getInstance("TLS")
                sc.init(null, null, null)
                
                val trustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as java.security.KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                
                if (trustManagers.isNotEmpty() && trustManagers[0] is javax.net.ssl.X509TrustManager) {
                    val trustManager = trustManagers[0] as javax.net.ssl.X509TrustManager
                    builder.sslSocketFactory(sc.socketFactory, trustManager)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("RetrofitClient", "SSL setup error: ${e.message}")
            }
        }

        val client = builder.build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DiscordApi::class.java)
    }
}
