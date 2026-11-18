package com.manu.launcher

import org.json.JSONObject

object FlagBuilder {

    data class State(
        var fps: Int = 120,
        var showFps: Boolean = true,
        /** 0=auto, 1=Vulkan, 2=OpenGL ES 3 */
        var graphicsApi: Int = 1,
        var preferVulkan: Boolean = true,
        var graySky: Boolean = false,
        var noMsaa: Boolean = false,
        var msaaLevel: Int = -1, // -1 auto, 0 off, >0 samples
        var frmLevel: Int = -1,
        var renderMode: Int = 2,
        var dynamicRes: String = "auto",
        var minGraphics: Boolean = false,
        var reduceMotion: Boolean = false,
        var haptic: Boolean = true,
        var oneClickBoost: Boolean = false,
        var simplifyModels: Boolean = false,
        var lowTextures: Boolean = false,
        var meshOpt: Boolean = false,
        var preloadModels: Boolean = false,
        var lessShadows: Boolean = false,
        var noPostFx: Boolean = false,
        var noGrass: Boolean = false,
        var hideNames: Boolean = false,
        var noBlurMenu: Boolean = false,
        var noVfx: Boolean = false,
        var noTextures: Boolean = false,
        var animCache: Boolean = false,
        var physics60: Boolean = false,
        var disableTelemetry: Boolean = true,
        var pauseVoxelizer: Boolean = false,
        var noFog: Boolean = false,
        var lowWater: Boolean = false,
        var shadowIntensity: Int = -1,
        var textureOverride: Int = -1,
        var lightingMode: Int = 0,
        var futureBright: Boolean = false,
        var maxShadows: Boolean = false,
        var highMsaa: Boolean = false,
        var fixFog: Boolean = false,
        /** CPU / network friendly extras */
        var cpuRelief: Boolean = true,
        var netSmooth: Boolean = true
    )

    fun build(s: State): String {
        val o = JSONObject()

        // ===== FPS (Bloxstrap / Masterstrap style) =====
        o.put("DFIntTaskSchedulerTargetFps", s.fps)
        // Smoother scheduler, fewer stalls
        o.put("FFlagTaskSchedulerLimitTargetFpsTo2402", false)
        o.put("DFIntTaskSchedulerMaxTempThrottledJobs", 2)
        o.put("DFIntTaskSchedulerJobMaxProcessingTimeMs", 8)
        if (s.showFps) o.put("FFlagDebugDisplayFPS", true)

        // ===== Graphics API =====
        when (s.graphicsApi) {
            1 -> {
                o.put("FFlagDebugGraphicsPreferVulkan", true)
                o.put("FFlagDebugGraphicsDisableOpenGL", true)
            }
            2 -> {
                o.put("FFlagDebugGraphicsPreferOpenGLES3", true)
                o.put("FFlagDebugGraphicsDisableVulkan", true)
            }
            else -> {
                if (s.preferVulkan || s.renderMode == 2) {
                    o.put("FFlagDebugGraphicsPreferVulkan", true)
                } else if (s.renderMode == 1) {
                    o.put("FFlagDebugGraphicsPreferOpenGL", true)
                }
            }
        }

        if (s.graySky) o.put("FFlagDebugSkyGray", true)

        // MSAA: -1 = Auto (не трогаем), 0 = Off, >0 = samples
        when {
            s.highMsaa -> {
                o.put("FIntDebugForceMSAASamples", 4)
                o.put("FFlagDebugForceMSAADisabled", false)
            }
            s.noMsaa || s.msaaLevel == 0 -> {
                o.put("FIntDebugForceMSAASamples", 0)
                o.put("FFlagDebugForceMSAADisabled", true)
            }
            s.msaaLevel > 0 -> {
                o.put("FIntDebugForceMSAASamples", s.msaaLevel)
                o.put("FFlagDebugForceMSAADisabled", false)
            }
            // -1 Auto: no flags
        }

        // FRM / quality
        if (s.frmLevel > 0) o.put("DFIntDebugFRMQualityLevelOverride", s.frmLevel)
        else if (s.minGraphics || s.oneClickBoost) o.put("DFIntDebugFRMQualityLevelOverride", 1)

        // Textures
        val tex = when {
            s.textureOverride >= 0 -> s.textureOverride
            s.noTextures -> 0
            s.lowTextures || s.oneClickBoost -> 1
            else -> -1
        }
        if (tex >= 0) {
            o.put("DFFlagTextureQualityOverrideEnabled", true)
            o.put("DFIntTextureQualityOverride", tex)
        }

        // Shadows
        if (s.lessShadows || s.oneClickBoost || s.shadowIntensity == 0) {
            o.put("FIntRenderShadowIntensity", 0)
        } else if (s.shadowIntensity > 0 || s.maxShadows) {
            o.put("FIntRenderShadowIntensity", if (s.maxShadows) 100 else s.shadowIntensity)
            if (s.maxShadows) o.put("FIntRenderShadowmapBias", 50)
        }

        if (s.noPostFx || s.oneClickBoost || s.noVfx) o.put("FFlagDisablePostFx", true)
        if (s.futureBright && !s.noPostFx) o.put("FFlagDisablePostFx", false)

        if (s.noGrass) {
            o.put("FIntFRMMinGrassDistance", 0)
            o.put("FIntFRMMaxGrassDistance", 0)
            o.put("FIntRenderGrassDetailStrands", 0)
        }
        if (s.pauseVoxelizer || s.oneClickBoost) o.put("DFFlagDebugPauseVoxelizer", true)
        if (s.lowWater) o.put("FFlagRenderWaterReflectionType", 0)
        if (s.fixFog) o.put("FFlagRenderFixFog", true)
        if (s.noFog) {
            o.put("FFlagDebugDisableFog", true)
            o.put("FIntRenderLocalLightFadeInMs", 0)
        }

        // Telemetry off (always useful)
        if (s.disableTelemetry) {
            o.put("FFlagDebugDisableTelemetryEphemeralCounter", true)
            o.put("FFlagDebugDisableTelemetryEventIngest", true)
            o.put("FFlagDebugDisableTelemetryPoint", true)
            o.put("FFlagDebugDisableTelemetryV2Counter", true)
            o.put("FFlagDebugDisableTelemetryV2Event", true)
            o.put("FFlagDebugDisableTelemetryV2Point", true)
        }

        when (s.lightingMode) {
            1 -> o.put("DFFlagDebugRenderForceTechnologyVoxel", true)
            2 -> o.put("FFlagDebugForceFutureIsBrightPhase2", true)
            3 -> o.put("FFlagDebugForceFutureIsBrightPhase3", true)
        }
        if (s.futureBright) {
            o.put("FFlagDebugForceFutureIsBrightPhase3", true)
            if (s.graphicsApi == 0) o.put("FFlagDebugGraphicsPreferVulkan", true)
        }

        if (s.hideNames) o.put("FFlagDebugDisplayNames", false)
        if (s.noBlurMenu) o.put("FFlagDebugDisableMenuBlur", true)
        if (s.reduceMotion) o.put("FFlagDebugForceReducedMotion", true)
        if (s.simplifyModels || s.meshOpt) {
            o.put("DFIntCSGLevelOfDetailSwitchingDistance", 0)
            o.put("DFIntCSGLevelOfDetailSwitchingDistanceL12", 0)
        }
        if (s.physics60) {
            o.put("DFIntPhysicsStepsPerFrame", 1)
            o.put("DFIntMaxPhysicsSteps", 2)
        }
        if (s.animCache) o.put("FFlagDebugDisableAnimationCache", false)

        // ===== CPU relief (safe, Bloxstrap-like) =====
        if (s.cpuRelief || s.oneClickBoost || s.minGraphics) {
            // fewer background workers
            o.put("DFIntRuntimeMaxNumOfThreads", 3)
            o.put("DFIntHttpCurlConnectionCacheSize", 16)
            o.put("DFIntTextureCompositorActiveJobs", 1)
            o.put("DFIntTextureCompositorJobMaxMs", 4)
            o.put("FFlagDebugDisableSurfaceLight", true)
            o.put("FFlagDisableNewLightAttenuation", true)
            // less particle / secondary work
            o.put("DFIntParticleMaxCount", 128)
            o.put("DFIntMaxFrameBufferSize", 2)
            o.put("FFlagDebugCheckRenderThreading", false)
            // skip heavy preloads
            o.put("FFlagPreloadTextureItems2", false)
            o.put("FFlagContentProviderPreloadEnabled", false)
        }

        // ===== Network smooth (SAFE — no desync / no raknet hacks) =====
        // Только мягкие клиентские таймауты и буферы. Без трогания raknet MTU / desync exploits.
        if (s.netSmooth) {
            o.put("DFIntConnectionMTUSize", 1200)
            o.put("DFIntHttpSocketSendBufferSize", 262144)
            o.put("DFIntHttpSocketReceiveBufferSize", 262144)
            o.put("DFIntNetworkSchemaTimeoutMs", 30000)
            o.put("DFIntJoinInfluxEventThrottleMs", 10000)
            // меньше репортов → меньше нагрузки
            o.put("FFlagEnableInGameMenuChromeABTest3", false)
            o.put("FFlagEnableInGameMenuChromeABTest4", false)
        }

        // ===== One-click / min graphics bundle =====
        if (s.oneClickBoost || s.minGraphics) {
            o.put("FFlagDebugGraphicsPreferVulkan", s.graphicsApi != 2)
            o.put("FIntDebugForceMSAASamples", 0)
            o.put("FFlagDebugForceMSAADisabled", true)
            o.put("FFlagDisablePostFx", true)
            o.put("FIntRenderShadowIntensity", 0)
            o.put("DFIntDebugFRMQualityLevelOverride", 1)
            o.put("DFFlagTextureQualityOverrideEnabled", true)
            o.put("DFIntTextureQualityOverride", 1)
            o.put("DFFlagDebugPauseVoxelizer", true)
            o.put("FIntFRMMinGrassDistance", 0)
            o.put("FIntFRMMaxGrassDistance", 0)
        }

        // ===== Whitelist / anti-noise (client only) =====
        o.put("FFlagUserHandleChatButtonClick", false)
        o.put("FFlagDebugCheckRenderThreading", false)

        return o.toString(2)
    }

    fun applyPreset(name: String, s: State) {
        s.oneClickBoost = false; s.minGraphics = false; s.graySky = false
        s.noMsaa = false; s.noGrass = false; s.noPostFx = false; s.noVfx = false
        s.noTextures = false; s.lowTextures = false; s.lessShadows = false
        s.pauseVoxelizer = false; s.disableTelemetry = true
        s.futureBright = false; s.maxShadows = false; s.highMsaa = false; s.fixFog = false
        s.shadowIntensity = -1; s.textureOverride = -1; s.frmLevel = -1
        s.graphicsApi = 1; s.preferVulkan = true; s.renderMode = 2
        s.cpuRelief = true; s.netSmooth = true; s.noFog = false

        when (name) {
            "cyber" -> {
                s.fps = 240; s.showFps = true; s.graphicsApi = 1
                s.noMsaa = true; s.noPostFx = true; s.lessShadows = true
                s.pauseVoxelizer = true; s.noGrass = true; s.cpuRelief = true
            }
            "fps_max" -> {
                s.fps = 240; s.showFps = true; s.graphicsApi = 1
                s.oneClickBoost = true; s.noMsaa = true; s.noPostFx = true
                s.lessShadows = true; s.pauseVoxelizer = true; s.cpuRelief = true
            }
            "balance" -> {
                s.fps = 120; s.showFps = true; s.graphicsApi = 1; s.frmLevel = 10
                s.cpuRelief = true
            }
            "battery" -> {
                s.fps = 60; s.graphicsApi = 2; s.minGraphics = true
                s.lowTextures = true; s.noMsaa = true; s.noPostFx = true
                s.lessShadows = true; s.noGrass = true; s.frmLevel = 1
                s.cpuRelief = true; s.physics60 = true
            }
            "beauty" -> {
                s.fps = 60; s.showFps = true; s.graphicsApi = 1
                s.futureBright = true; s.lightingMode = 3; s.frmLevel = 21
                s.textureOverride = 3; s.highMsaa = true; s.maxShadows = true
                s.shadowIntensity = 100; s.fixFog = true; s.noPostFx = false
                s.cpuRelief = false
            }
            "future" -> {
                s.fps = 90; s.graphicsApi = 1; s.futureBright = true
                s.lightingMode = 3; s.frmLevel = 15; s.textureOverride = 2
                s.maxShadows = true; s.shadowIntensity = 80
            }
            "potato" -> {
                s.fps = 60; s.oneClickBoost = true; s.minGraphics = true
                s.graySky = true; s.noMsaa = true; s.noGrass = true
                s.noPostFx = true; s.noVfx = true; s.lowTextures = true
                s.textureOverride = 0; s.frmLevel = 1; s.graphicsApi = 2
                s.cpuRelief = true; s.physics60 = true; s.noFog = true
            }
            "compet" -> {
                s.fps = 240; s.showFps = true; s.graphicsApi = 1
                s.noMsaa = true; s.noPostFx = true; s.lessShadows = true
                s.noGrass = true; s.pauseVoxelizer = true; s.cpuRelief = true
                s.noFog = true; s.lowTextures = true
            }
            "quality" -> {
                s.fps = 60; s.frmLevel = 21; s.msaaLevel = 4; s.highMsaa = true
                s.graphicsApi = 1; s.shadowIntensity = 50; s.textureOverride = 3
                s.cpuRelief = false
            }
            "low" -> {
                s.fps = 90; s.lowTextures = true; s.lessShadows = true
                s.noPostFx = true; s.noGrass = true; s.frmLevel = 1; s.noMsaa = true
                s.cpuRelief = true
            }
            "gray_fps" -> {
                s.fps = 120; s.graySky = true; s.showFps = true; s.cpuRelief = true
            }
            "desync_safe" -> {
                // Только safe net + fps, без raknet/desync эксплойтов
                s.fps = 120; s.showFps = true; s.graphicsApi = 1
                s.netSmooth = true; s.cpuRelief = true
                s.noMsaa = true; s.lessShadows = true; s.noPostFx = true
            }
            "reset" -> {
                val d = State()
                s.fps = d.fps; s.showFps = d.showFps; s.graphicsApi = d.graphicsApi
                s.preferVulkan = d.preferVulkan; s.graySky = d.graySky
                s.noMsaa = d.noMsaa; s.msaaLevel = d.msaaLevel; s.frmLevel = d.frmLevel
                s.renderMode = d.renderMode; s.dynamicRes = d.dynamicRes
                s.minGraphics = d.minGraphics; s.reduceMotion = d.reduceMotion
                s.haptic = d.haptic; s.oneClickBoost = d.oneClickBoost
                s.simplifyModels = d.simplifyModels; s.lowTextures = d.lowTextures
                s.meshOpt = d.meshOpt; s.preloadModels = d.preloadModels
                s.lessShadows = d.lessShadows; s.noPostFx = d.noPostFx; s.noGrass = d.noGrass
                s.hideNames = d.hideNames; s.noBlurMenu = d.noBlurMenu; s.noVfx = d.noVfx
                s.noTextures = d.noTextures; s.animCache = d.animCache; s.physics60 = d.physics60
                s.disableTelemetry = d.disableTelemetry; s.pauseVoxelizer = d.pauseVoxelizer
                s.noFog = d.noFog; s.lowWater = d.lowWater
                s.shadowIntensity = d.shadowIntensity; s.textureOverride = d.textureOverride
                s.lightingMode = d.lightingMode; s.futureBright = d.futureBright
                s.maxShadows = d.maxShadows; s.highMsaa = d.highMsaa; s.fixFog = d.fixFog
                s.cpuRelief = d.cpuRelief; s.netSmooth = d.netSmooth
            }
        }
    }
}
