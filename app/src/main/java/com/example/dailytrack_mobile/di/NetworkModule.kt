package com.example.dailytrack_mobile.di

import com.example.dailytrack_mobile.BuildConfig
import com.example.dailytrack_mobile.data.remote.api.ApiConfig
import com.example.dailytrack_mobile.data.remote.api.DailyTrackApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(authManager: com.example.dailytrack_mobile.data.local.auth.AuthManager): OkHttpClient {
        // BODY-level logging prints request/response headers (including the auth Bearer
        // token) and full payloads to Logcat. Only safe in debug builds.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()

            // Do not add auth header to firebase-login or external URLs (e.g. GitHub Releases)
            if (original.url.encodedPath.contains("/api/auth/firebase-login")) {
                return@Interceptor chain.proceed(original)
            }

            val token = authManager.getCachedToken()
            val requestBuilder = original.newBuilder()

            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideDailyTrackApi(retrofit: Retrofit): DailyTrackApi =
        retrofit.create(DailyTrackApi::class.java)
}
