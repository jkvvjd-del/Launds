package com.manu.launcher

import org.json.JSONObject

/**
 * App-level allowlist (не «официальный список Roblox»).
 * Категории: allowed / blocked / unknown.
 * Неверный тип -> флаг удаляется, не «исправляется» молча.
 */
object FlagWhitelist {

    data class FlagMeta(val type: String) // bool | int | string

    private val ALLOWED: Map<String, FlagMeta> = mapOf(
        "DFIntTaskSchedulerTargetFps" to FlagMeta("int"),
        "FFlagTaskSchedulerLimitTargetFpsTo2402" to FlagMeta("bool"),
        "DFIntTaskSchedulerMaxTempThrottledJobs" to FlagMeta("int"),
        "DFIntTaskSchedulerJobMaxProcessingTimeMs" to FlagMeta("int"),
        "FFlagDebugDisplayFPS" to FlagMeta("bool"),
        "FFlagDebugGraphicsPreferVulkan" to FlagMeta("bool"),
        "FFlagDebugGraphicsDisableOpenGL" to FlagMeta("bool"),
        "FFlagDebugGraphicsPreferOpenGLES3" to FlagMeta("bool"),
        "FFlagDebugGraphicsDisableVulkan" to FlagMeta("bool"),
        "FFlagDebugGraphicsPreferOpenGL" to FlagMeta("bool"),
        "FFlagDebugSkyGray" to FlagMeta("bool"),
        "FIntDebugForceMSAASamples" to FlagMeta("int"),
        "FFlagDebugForceMSAADisabled" to FlagMeta("bool"),
        "DFIntDebugFRMQualityLevelOverride" to FlagMeta("int"),
        "DFFlagTextureQualityOverrideEnabled" to FlagMeta("bool"),
        "DFIntTextureQualityOverride" to FlagMeta("int"),
        "FIntRenderShadowIntensity" to FlagMeta("int"),
        "FIntRenderShadowmapBias" to FlagMeta("int"),
        "FFlagDisablePostFx" to FlagMeta("bool"),
        "FIntFRMMinGrassDistance" to FlagMeta("int"),
        "FIntFRMMaxGrassDistance" to FlagMeta("int"),
        "FIntRenderGrassDetailStrands" to FlagMeta("int"),
        "DFFlagDebugPauseVoxelizer" to FlagMeta("bool"),
        "FFlagRenderWaterReflectionType" to FlagMeta("int"),
        "FFlagRenderFixFog" to FlagMeta("bool"),
        "FFlagDebugDisableFog" to FlagMeta("bool"),
        "FIntRenderLocalLightFadeInMs" to FlagMeta("int"),
        "FFlagDebugDisableTelemetryEphemeralCounter" to FlagMeta("bool"),
        "FFlagDebugDisableTelemetryEventIngest" to FlagMeta("bool"),
        "FFlagDebugDisableTelemetryPoint" to FlagMeta("bool"),
        "FFlagDebugDisableTelemetryV2Counter" to FlagMeta("bool"),
        "FFlagDebugDisableTelemetryV2Event" to FlagMeta("bool"),
        "FFlagDebugDisableTelemetryV2Point" to FlagMeta("bool"),
        "DFFlagDebugRenderForceTechnologyVoxel" to FlagMeta("bool"),
        "FFlagDebugForceFutureIsBrightPhase2" to FlagMeta("bool"),
        "FFlagDebugForceFutureIsBrightPhase3" to FlagMeta("bool"),
        "FFlagDebugDisplayNames" to FlagMeta("bool"),
        "FFlagDebugDisableMenuBlur" to FlagMeta("bool"),
        "FFlagDebugForceReducedMotion" to FlagMeta("bool"),
        "DFIntCSGLevelOfDetailSwitchingDistance" to FlagMeta("int"),
        "DFIntCSGLevelOfDetailSwitchingDistanceL12" to FlagMeta("int"),
        "DFIntPhysicsStepsPerFrame" to FlagMeta("int"),
        "DFIntMaxPhysicsSteps" to FlagMeta("int"),
        "FFlagDebugDisableAnimationCache" to FlagMeta("bool"),
        "FFlagUserHandleChatButtonClick" to FlagMeta("bool"),
        "FFlagDebugCheckRenderThreading" to FlagMeta("bool"),
        "FFlagEnableInGameMenuChromeABTest3" to FlagMeta("bool"),
        "FFlagEnableInGameMenuChromeABTest4" to FlagMeta("bool"),
        "DFIntRuntimeMaxNumOfThreads" to FlagMeta("int"),
        "DFIntHttpCurlConnectionCacheSize" to FlagMeta("int"),
        "DFIntTextureCompositorActiveJobs" to FlagMeta("int"),
        "DFIntTextureCompositorJobMaxMs" to FlagMeta("int"),
        "FFlagDebugDisableSurfaceLight" to FlagMeta("bool"),
        "FFlagDisableNewLightAttenuation" to FlagMeta("bool"),
        "DFIntParticleMaxCount" to FlagMeta("int"),
        "DFIntMaxFrameBufferSize" to FlagMeta("int"),
        "FFlagPreloadTextureItems2" to FlagMeta("bool"),
        "FFlagContentProviderPreloadEnabled" to FlagMeta("bool"),
        "DFIntConnectionMTUSize" to FlagMeta("int"),
        "DFIntHttpSocketSendBufferSize" to FlagMeta("int"),
        "DFIntHttpSocketReceiveBufferSize" to FlagMeta("int"),
        "DFIntNetworkSchemaTimeoutMs" to FlagMeta("int"),
        "DFIntJoinInfluxEventThrottleMs" to FlagMeta("int")
    )

    private val BLOCKED = setOf(
        // явный бан-риск / мусор — никогда не пишем
        "FFlagEnableCheat",
        "FFlagDebugBreak"
    )

    private val BLOCKED_PREFIXES = listOf(
        "FFlagRakNet", "DFIntRakNet", "FFlagDebugRak",
        "FFlagDesync", "DFFlagDesync", "FFlagNetworkDesync"
    )

    data class FilterResult(
        val json: String,
        val kept: List<String>,
        val removed: List<String>,
        val blocked: List<String>,
        val typeErrors: List<String>
    )

    fun isBlocked(key: String): Boolean {
        if (key in BLOCKED) return true
        return BLOCKED_PREFIXES.any { key.startsWith(it, ignoreCase = true) }
    }

    fun isAllowed(key: String): Boolean {
        if (isBlocked(key)) return false
        return ALLOWED.containsKey(key)
    }

    fun filter(rawJson: String, strict: Boolean = true): FilterResult {
        val kept = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val blocked = mutableListOf<String>()
        val typeErrors = mutableListOf<String>()
        val out = JSONObject()

        val src = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return FilterResult("{}", emptyList(), listOf("<invalid json>"), emptyList(), emptyList())
        }

        val keys = src.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isBlocked(key)) {
                blocked.add(key)
                continue
            }
            if (strict && !ALLOWED.containsKey(key)) {
                removed.add(key)
                continue
            }
            val meta = ALLOWED[key]
            val value = src.get(key)
            if (meta != null) {
                val coerced = coerceStrict(value, meta.type)
                if (coerced == null) {
                    typeErrors.add("$key (expected ${meta.type}, got ${value::class.simpleName})")
                    continue
                }
                out.put(key, coerced)
                kept.add(key)
            } else if (!strict) {
                out.put(key, value)
                kept.add(key)
            } else {
                removed.add(key)
            }
        }
        return FilterResult(out.toString(2), kept, removed, blocked, typeErrors)
    }

    /** Только корректные значения. Иначе null = drop. */
    private fun coerceStrict(value: Any?, type: String): Any? {
        return when (type) {
            "bool" -> when (value) {
                is Boolean -> value
                is Byte, is Short, is Int, is Long -> {
                    val n = (value as Number).toLong()
                    when (n) {
                        0L -> false
                        1L -> true
                        else -> null
                    }
                }
                is Float, is Double -> null // 0.5 и прочие дроби — не bool
                is String -> when (value.lowercase()) {
                    "true" -> true
                    "false" -> false
                    "1" -> true
                    "0" -> false
                    else -> null
                }
                else -> null
            }
            "int" -> when (value) {
                is Int -> value
                is Long -> if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else null
                is Float, is Double -> {
                    val d = (value as Number).toDouble()
                    if (d % 1.0 == 0.0 && d in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) d.toInt() else null
                }
                is String -> value.toIntOrNull()
                is Boolean -> null
                else -> null
            }
            "string" -> value as? String
            else -> null
        }
    }
}
