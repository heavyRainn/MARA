package com.care.voice.data.net

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/** OpenAI-совместимый Chat Completions */
interface DeepSeekApi {

    @POST("chat/completions")
    @Headers("Accept: application/json", "Content-Type: application/json")
    suspend fun chat(@Body req: ChatRequest): ChatResponse

    companion object {
        private const val BASE_URL = "https://api.deepseek.com/"

        fun create(apiKey: String): DeepSeekApi {
            // Moshi: работает и с @JsonClass(generateAdapter = true), и без него
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val auth = Interceptor { chain ->
                val apiKeyMasked = if (apiKey.length > 8) apiKey.take(4) + "..." + apiKey.takeLast(4) else "EMPTY"
                Log.d("DeepSeek", "Using key len=${apiKey.length}, $apiKeyMasked")
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(auth)      // <— сначала авторизация
                .addInterceptor(logging)   // <— потом логгер, чтобы видел заголовки
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
                .create(DeepSeekApi::class.java)
        }
    }
}

/* ---------- МОДЕЛИ ---------- */

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val stream: Boolean = false,
    val temperature: Double = 0.3
)

@JsonClass(generateAdapter = true)
data class Message(
    val role: String,                 // "system" | "user" | "assistant"
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val created: Long? = null,
    val model: String? = null,
    val object_: String? = null // некоторые провайдеры присылают "object"
) {
    @JsonClass(generateAdapter = true)
    data class Choice(
        val index: Int? = null,
        val message: Message? = null,
        @Json(name = "finish_reason") val finishReason: String? = null
    )
}

/** На случай, если сервер вернёт JSON-ошибку (например, при 401/429) */
@JsonClass(generateAdapter = true)
data class ErrorBody(
    val error: ErrorDetail?
) {
    @JsonClass(generateAdapter = true)
    data class ErrorDetail(
        val message: String?,
        val type: String? = null,
        val code: String? = null
    )
}
