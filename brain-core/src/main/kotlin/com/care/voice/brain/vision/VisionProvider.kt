package com.care.voice.brain.vision

interface VisionProvider {
    suspend fun analyze(request: VisionRequest): VisionResult
}
