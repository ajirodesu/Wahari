package dev.citali.needle.engine

import android.content.Context
import dev.citali.needle.BuildConfig

/**
 * One-tap on-device check (Settings → On-device model → Run engine self-test).
 *
 * Loads the weights, builds the phone session prefix and runs a single
 * harmless completion WITHOUT executing any tool it names, so the user can
 * verify inference works without logcat. Uses the same [NeedleEngine] path as
 * chat, so a passing self-test means chat works too.
 */
object EngineSelfTest {

    data class Result(
        val engineAvailable: Boolean,
        val models: Int,
        val engineVersion: String,
        val prefixTokens: Int,
        val replyType: String?,
        /** Raw JSON envelope (truncated for display), or null on failure. */
        val rawEnvelope: String?,
        /** Engine's real error text, or null on success. */
        val error: String?,
    ) {
        val ok: Boolean get() = error == null
    }

    suspend fun run(context: Context): Result {
        val app = context.applicationContext
        val available = runCatching { NeedleNative.nativeEngineAvailable() }.getOrDefault(false)
        if (!available) {
            return Result(
                engineAvailable = false,
                models = 0,
                engineVersion = BuildConfig.NEEDLE_ENGINE_VERSION,
                prefixTokens = 0,
                replyType = null,
                rawEnvelope = null,
                error = "This build has no Needle engine for this CPU architecture.",
            )
        }
        val models = runCatching { NeedleNative.nativeModels() }.getOrDefault(-1)
        val spec = NeedleSessions.phoneSpec(app)
        val prepared = NeedleEngine.prepare(app, spec)
        if (prepared.isFailure) {
            val message = prepared.exceptionOrNull()?.message
                ?: NeedleNative.nativeLastError().ifBlank { "engine prepare failed" }
            return Result(
                engineAvailable = true,
                models = models,
                engineVersion = BuildConfig.NEEDLE_ENGINE_VERSION,
                prefixTokens = NeedleEngine.state.value.prefixTokens,
                replyType = null,
                rawEnvelope = null,
                error = message,
            )
        }
        // Same call chat makes, but the resulting tool call is never executed.
        val raw = NeedleEngine.completeEnvelope(app, spec, "what's my battery at?", maxNewTokens = 256)
        return raw.fold(
            onSuccess = { envelope ->
                val parsed = runCatching { NeedleEngine.parseEnvelope(envelope) }.getOrNull()
                Result(
                    engineAvailable = true,
                    models = models,
                    engineVersion = BuildConfig.NEEDLE_ENGINE_VERSION,
                    prefixTokens = NeedleEngine.state.value.prefixTokens,
                    replyType = parsed?.type,
                    rawEnvelope = envelope.take(1500),
                    error = null,
                )
            },
            onFailure = { error ->
                Result(
                    engineAvailable = true,
                    models = models,
                    engineVersion = BuildConfig.NEEDLE_ENGINE_VERSION,
                    prefixTokens = NeedleEngine.state.value.prefixTokens,
                    replyType = null,
                    rawEnvelope = null,
                    error = error.message
                        ?: NeedleNative.nativeLastError().ifBlank { "engine returned nothing" },
                )
            },
        )
    }
}
