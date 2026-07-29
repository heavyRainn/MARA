package com.care.voice.data.net

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface VisionApi {
    @POST("chat/completions")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun chat(@Body req: VisionChatRequestDto): VisionChatResponseDto

    companion object {
        fun groq(apiKey: String): VisionApi {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val auth = Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    .build()
                chain.proceed(req)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(auth)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.groq.com/openai/v1/")
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
                .create(VisionApi::class.java)
        }
    }
}
