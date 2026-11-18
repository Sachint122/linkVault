package com.linkvault.app.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig

/**
 * Thin Kotlin wrapper around the LiteRT-LM runtime (spec §8: Kotlin API is
 * the supported integration surface for the Java app).
 *
 * CPU backend only (spec: CPU inference first). Thinking is disabled —
 * Qwen3-family models think by default and we need plain JSON, not reasoning.
 * All calls are synchronized; [generate] must be invoked off the main thread
 * (engine init alone can take ~10s).
 */
class LocalAiEngine(
    private val modelPath: String,
    private val cacheDir: String,
) {
    @Volatile
    private var engine: Engine? = null

    @Volatile
    var lastError: String? = null
        private set

    val isReady: Boolean
        get() = engine != null

    /** Loads the model. Returns false (with [lastError]) on any failure. */
    @Synchronized
    fun initialize(): Boolean {
        if (engine != null) return true
        return try {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = cacheDir,
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            lastError = null
            true
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            engine = null
            false
        }
    }

    /**
     * One synchronous generation. Returns the full reply text, or null on
     * error. Caller enforces a timeout (a hung native call cannot be
     * interrupted — the wrapper thread is abandoned instead).
     */
    @Synchronized
    fun generate(systemInstruction: String, prompt: String, maxOutputToken: Int): String? {
        val e = engine ?: return null
        return try {
            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.2),
                maxOutputToken = maxOutputToken,
                thinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = 0),
            )
            val conversation = e.createConversation(convConfig)
            try {
                val message = conversation.sendMessage(prompt)
                // Message has no .text in 0.19.1: reply text = concatenation
                // of Content.Text entries inside message.contents.contents.
                val sb = StringBuilder()
                for (content in message.contents.contents) {
                    if (content is Content.Text) sb.append(content.text)
                }
                sb.toString()
            } finally {
                try {
                    conversation.close()
                } catch (_: Throwable) {
                }
            }
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            null
        }
    }

    @Synchronized
    fun release() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
    }
}
