package com.example.data.ai

import com.example.domain.model.AiCommandPlan
import com.example.domain.model.AssistantContext

interface AiRepository {
    val isCloudAiConnected: Boolean
    suspend fun processCommand(input: String, context: AssistantContext): AiCommandPlan
}
