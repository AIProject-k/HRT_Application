package com.hormonelog.app.ai

interface LlmEngine : AutoCloseable {
    fun generate(prompt: String, cancelled: ()->Boolean): String
    fun cancel()
}
