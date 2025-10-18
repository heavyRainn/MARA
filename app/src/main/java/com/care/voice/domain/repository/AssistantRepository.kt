package com.care.voice.domain.repository

interface AssistantRepository {
    suspend fun chat(userText: String): Result<String>
}
