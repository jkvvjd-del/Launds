package com.manu.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var contentHost: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var deviceInfoText: TextView
    private lateinit var hzWarnText: TextView
    private lateinit var logText: TextView
    private lateinit var placeInput: EditText

    private val state = FlagBuilder.State()
    private val prefs by lazy { getSharedPreferences("manu_ui", MODE_PRIVATE) }

    private var keepScreenOn = false
    private var dnd = false
    private var killBg = false
    private var cleanRam = false
    private var noBatteryOpt = false

    private val pages = mutableMapOf<String, View>()

    private val shizukuListener =
        Shizuku.OnRequestPermissionResultListener { _, result ->
            updateStatus()
            toast(if (result == PackageManager.PERMISSION_GRANTED) "Shizuku OK" else "Shizuku отказано")
        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadState()
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
            FlagManager.requestShizukuPermission(0)
        } catch (_: Exception) {}

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.bg))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "MANU Launcher"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "FastFlag · Masterstrap / Bloxstrap style"
            setTextColor(color(R.color.text_sec))
            textSize = 12f
        })
        statusText = TextView(this).apply {
            setTextColor(color(R.color.accent2))
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
        }
        header.addView(statusText)

        // Device info widget
        val deviceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        deviceInfoText = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        hzWarnText = TextView(this).apply {
            setTextColor(0xFFFFC107.toInt())
            textSize = 12f
            setPadding(0, dp(6), 0, 0)
            visibility = View.GONE
        }
        deviceCard.addView(deviceInfoText)
        deviceCard.addView(hzWarnText)
        header.addView(deviceCard)
        root.addView(header)

        contentHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(contentHost)
        root.addView(buildBottomNav())
        setContentView(root)

        pages["home"] = buildHomePage()
        pages["ff"] = buildFastFlagPage()
        pages["settings"] = buildSettingsPage()
        pages["game"] = buildGameModePage()
        pages["presets"] = buildPresetsPage()
        showTab("home")
        updateStatus()
        refreshDeviceInfo()
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceInfo()
        updateStatus()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (_: Exception) {}
        saveState()
        super.onDestroy()
    }

    // ───────────── HOME ─────────────

    private fun buildHomePage(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(100))
        }
        scroll.addView(col)

        col.addView(card {
            addView(section("Быстрые профили"))
            addView(btn("Max FPS Boost (240)") {
                FlagBuilder.applyPreset("fps_max", state)
                clearManualJson()
                saveState()
                toast("Max FPS Boost — жми ▶")
            })
            addView(btn("Киберспорт", secondary = true) {
                FlagBuilder.applyPreset("cyber", state)
                clearManualJson()
                saveState()
                toast("Киберспорт — жми ▶")
            })
            addView(btn("Баланс", secondary = true) {
                FlagBuilder.applyPreset("balance", state)
                clearManualJson()
                saveState()
                toast("Баланс")
            })
            addView(btn("Экономия батареи", secondary = true) {
                FlagBuilder.applyPreset("battery", state)
                clearManualJson()
                saveState()
                toast("Экономия батареи")
            })
            addView(btn("Сохранить мой пресет", secondary = true) {
                saveState()
                prefs.edit().putBoolean("custom_saved", true).apply()
                toast("Текущие настройки сохранены")
            })
            if (prefs.getBoolean("custom_saved", false)) {
                addView(btn("Загрузить мой пресет", secondary = true) {
                    loadState()
                    toast("Мой пресет загружен")
                })
            }
        })

        col.addView(card {
            addView(section("Быстрый вход по Place ID / ссылке"))
            placeInput = EditText(this@MainActivity).apply {
                hint = "142823291 или https://www.roblox.com/games/..."
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF666680.toInt())
                setBackgroundColor(0xFF12141F.toInt())
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setText(prefs.getString("place_id", "") ?: "")
            }
            addView(placeInput)
            addView(TextView(this@MainActivity).apply {
                text = "Пусто = обычный запуск Roblox"
                setTextColor(color(R.color.text_sec))
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
            })
        })

        col.addView(card {
            addView(section("Инструменты"))
            addView(btn("Очистить кэш Roblox", secondary = true) {
                FlagManager.requestShizukuPermission(0)
                val ok = FlagManager.clearRobloxCache { runOnUiThread { appendLog(it) } }
                toast(if (ok) "Кэш очищен" else "Нужен Shizuku/Root")
            })
            addView(btn("Открыть Delta Executor", secondary = true) {
                val ok = FlagManager.launchDelta(this@MainActivity) { msg -> runOnUiThread { appendLog(msg) } }
                if (!ok) toast("Delta не найдена — установи и попробуй снова")
            })
            addView(btn("Открыть Shizuku", secondary = true) {
                try {
                    startActivity(packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api"))
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")))
                    } catch (_: Exception) {
                        toast("Установи Shizuku")
                    }
                }
            })
            addView(btn("Запросить доступ Shizuku", secondary = true) {
                FlagManager.requestShizukuPermission(0)
                updateStatus()
            })
            addView(btn("Выдать «Все файлы» (NoRoot)", secondary = true) {
                if (FlagManager.needsAllFilesAccess()) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                        try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) {}
                    }
                } else {
                    toast("Доступ к файлам уже есть")
                }
            })
        })

        col.addView(btn("ПРИМЕНИТЬ И ЗАПУСТИТЬ (Delta-Roblox)") { applyAndLaunch() })

        logText = TextView(this).apply {
            setTextColor(0xFF666680.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(12), 0, 0)
        }
        col.addView(logText)
        return scroll
    }

    // ───────────── FF ─────────────

    private fun buildFastFlagPage(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(100))
        }
        scroll.addView(col)

        col.addView(card {
            addView(label("Частота кадров (FPS)"))
            val fpsLabel = TextView(this@MainActivity).apply {
                text = "${state.fps}"
                setTextColor(color(R.color.accent))
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(fpsLabel)
            addView(SeekBar(this@MainActivity).apply {
                max = 180
                progress = (state.fps - 60).coerceIn(0, 180)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                        state.fps = 60 + p
                        fpsLabel.text = "${state.fps}"
                        saveState()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        })

        col.addView(card {
            addView(section("Графический API"))
            addView(dropdownRow(
                "Движок",
                listOf("Авто", "Vulkan", "OpenGL ES 3"),
                state.graphicsApi
            ) { idx ->
                state.graphicsApi = idx
                state.preferVulkan = idx == 1
                state.renderMode = when (idx) { 1 -> 2; 2 -> 1; else -> 0 }
                saveState()
            })
            addView(TextView(this@MainActivity).apply {
                text = "Vulkan — стабильность на Snapdragon/Dimensity. GLES3 — запасной вариант."
                setTextColor(color(R.color.text_sec))
                textSize = 11f
                setPadding(0, dp(6), 0, 0)
            })
        })

        col.addView(card {
            addView(section("Производительность"))
            toggle("show_fps", "Счётчик FPS", state.showFps) { state.showFps = it; clearManualJson(); saveState() }
            toggle("one_click", "Ускорение графики", state.oneClickBoost) { state.oneClickBoost = it; clearManualJson(); saveState() }
            toggle("min_gfx", "Мин. графика", state.minGraphics) { state.minGraphics = it; clearManualJson(); saveState() }
            toggle("low_tex", "Низкие текстуры", state.lowTextures) { state.lowTextures = it; clearManualJson(); saveState() }
            toggle("telemetry", "Отключить telemetry", state.disableTelemetry) { state.disableTelemetry = it; clearManualJson(); saveState() }
            toggle("voxel", "Pause Voxelizer", state.pauseVoxelizer) { state.pauseVoxelizer = it; clearManualJson(); saveState() }
        })

        col.addView(card {
            addView(section("Рендер / красота"))
            toggle("future", "Future is Bright", state.futureBright) { state.futureBright = it; clearManualJson(); saveState() }
            toggle("maxsh", "Макс. тени", state.maxShadows) { state.maxShadows = it; clearManualJson(); saveState() }
            toggle("hmsaa", "MSAA 4x", state.highMsaa) { state.highMsaa = it; clearManualJson(); saveState() }
            toggle("gray", "Серое небо", state.graySky) { state.graySky = it; clearManualJson(); saveState() }
            toggle("no_msaa", "Выкл MSAA", state.noMsaa) { state.noMsaa = it; clearManualJson(); saveState() }
            toggle("no_post", "Выкл PostFX", state.noPostFx) { state.noPostFx = it; clearManualJson(); saveState() }
            toggle("no_grass", "Без травы", state.noGrass) { state.noGrass = it; clearManualJson(); saveState() }
            toggle("fog", "Fix Fog", state.fixFog) { state.fixFog = it; clearManualJson(); saveState() }
        })

        col.addView(btn("ПРИМЕНИТЬ ФЛАГИ") { applyFlagsOnly() })
        col.addView(btn("СБРОСИТЬ ФЛАГИ", secondary = true) {
            FlagManager.clearFlags(this) { runOnUiThread { appendLog(it) } }
            toast("Сброшено")
        })
        col.addView(btn("Редактор JSON", secondary = true) { showJsonEditor() })
        return scroll
    }

    private fun buildSettingsPage(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(100))
        }
        scroll.addView(col)
        col.addView(card {
            addView(section("Качество"))
            val msaaIdx = when {
                state.highMsaa || state.msaaLevel == 4 -> 3
                state.noMsaa || state.msaaLevel == 0 -> 1
                state.msaaLevel == 2 -> 2
                state.msaaLevel == 8 -> 4
                else -> 0 // Auto (-1)
            }
            addView(dropdownRow("MSAA", listOf("Авто", "Выкл", "2x", "4x", "8x"), msaaIdx) { idx ->
                state.msaaLevel = when (idx) { 1 -> 0; 2 -> 2; 3 -> 4; 4 -> 8; else -> -1 }
                state.noMsaa = state.msaaLevel == 0
                state.highMsaa = state.msaaLevel == 4
                clearManualJson()
                saveState()
            })
            val frmIdx = when (state.frmLevel) {
                1 -> 1; 10 -> 2; 21 -> 3; else -> 0
            }
            addView(dropdownRow("FRM", listOf("Авто", "1", "10", "21"), frmIdx) { idx ->
                state.frmLevel = when (idx) { 1 -> 1; 2 -> 10; 3 -> 21; else -> -1 }
                clearManualJson()
                saveState()
            })
        })
        col.addView(btn("ПРИМЕНИТЬ И ЗАПУСТИТЬ") { applyAndLaunch() })
        return scroll
    }

    private fun buildGameModePage(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(100))
        }
        scroll.addView(col)
        col.addView(card {
            addView(section("Игровой режим"))
            toggle("screen", "Экран всегда вкл", keepScreenOn) { keepScreenOn = it }
            toggle("dnd", "Не беспокоить", dnd) { dnd = it }
            toggle("battery", "Без ограничений питания", noBatteryOpt) { noBatteryOpt = it }
            toggle("ram", "Очистка RAM", cleanRam) { cleanRam = it }
            toggle("kill", "Kill background (Shizuku)", killBg) { killBg = it }
        })
        col.addView(btn("Очистить кэш Roblox", secondary = true) {
            val ok = FlagManager.clearRobloxCache { runOnUiThread { appendLog(it) } }
            toast(if (ok) "Кэш очищен" else "Нужен Shizuku/Root")
        })
        col.addView(btn("ПРИМЕНИТЬ + ЗАПУСК") { applyAndLaunch() })
        return scroll
    }

    private fun buildPresetsPage(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(100))
        }
        scroll.addView(col)
        data class P(val id: String, val title: String, val desc: String)
        listOf(
            P("fps_max", "Max FPS Boost", "240 FPS + CPU relief (Bloxstrap-style)"),
            P("cyber", "Киберспорт", "240 FPS, минимум эффектов"),
            P("compet", "Competitive", "240 + low load + no fog"),
            P("desync_safe", "Net Smooth (safe)", "Буферы/таймауты, БЕЗ raknet/desync"),
            P("balance", "Баланс", "120 FPS + CPU relief"),
            P("low", "Low Load", "90 FPS, тени/grass/postfx off"),
            P("potato", "Potato", "Слабые устройства, макс. разгрузка CPU"),
            P("battery", "Экономия батареи", "60 FPS, GLES, low"),
            P("beauty", "Future / Масло", "Макс. красота, тяжело"),
            P("future", "Future Balanced", "Future + 90 FPS"),
            P("quality", "Quality", "FRM 21 + MSAA 4"),
            P("gray_fps", "Gray + FPS", "Серое небо + 120"),
            P("reset", "Сброс", "По умолчанию")
        ).forEach { p ->
            col.addView(card {
                addView(TextView(this@MainActivity).apply {
                    text = p.title
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@MainActivity).apply {
                    text = p.desc
                    setTextColor(color(R.color.text_sec))
                    textSize = 12f
                    setPadding(0, dp(4), 0, dp(8))
                })
                addView(btn("Применить") {
                    FlagBuilder.applyPreset(p.id, state)
                    prefs.edit().remove("manual_json").apply()
                    saveState()
                    toast("${p.title} OK")
                })
            })
        }
        col.addView(btn("ПРИМЕНИТЬ И ЗАПУСТИТЬ") { applyAndLaunch() })
        return scroll
    }

    // ───────────── ACTIONS ─────────────

    private fun applyFlagsOnly(): Boolean {
        // Shizuku: request if available but not granted; NoRoot — основной путь, elevated опционален
        try {
            if (rikka.shizuku.Shizuku.pingBinder() &&
                rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                FlagManager.requestShizukuPermission(0)
                appendLog("Shizuku: запрошено разрешение (elevated опционален, NoRoot основной)")
            }
        } catch (_: Exception) {}
        if (FlagManager.needsAllFilesAccess()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) {}
            }
            toast("Сначала выдай «Все файлы», потом снова Применить")
            appendLog("WAIT: MANAGE_EXTERNAL_STORAGE required")
            return false
        }
        saveState()
        val manual = prefs.getString("manual_json", null)
        val json = if (!manual.isNullOrBlank()) {
            appendLog("using manual JSON from editor")
            manual
        } else {
            FlagBuilder.build(state)
        }
        val report = FlagManager.writeFlagsDetailed(this, json) { msg -> runOnUiThread { appendLog(msg) } }
        if (report.ok && !manual.isNullOrBlank()) {
            // one-shot: clear manual after successful apply so UI presets work again
            // keep until user changes preset — actually keep until explicit clear
        }
        runOnUiThread {
            appendLog("--- APPLY REPORT ---")
            appendLog(report.summary())
            if (report.removed.isNotEmpty()) {
                appendLog("Whitelist removed: ${report.removed.take(20).joinToString()}")
            }
        }
        toast(when {
            report.ok -> "OK: ${report.verifiedPaths.size} path verified"
            FlagManager.needsAllFilesAccess() -> "Выдай «Все файлы» и повтори"
            else -> "APPLY FAIL — смотри лог"
        })
        updateStatus()
        return report.ok
    }

    private fun applyAndLaunch() {
        applyGameMode()
        val ok = applyFlagsOnly()
        if (ok) {
            launchRoblox()
        } else {
            toast("Флаги не применены — Roblox не запущен")
            appendLog("LAUNCH skipped: apply failed")
        }
    }

    private fun applyGameMode() {
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (dnd) enableDnd()
        if (noBatteryOpt) requestBatteryOpt()
        if (killBg || cleanRam) FlagManager.killBackground { appendLog(it) }
    }

    private fun enableDnd() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            toast("Выдай доступ «Не беспокоить»")
            return
        }
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun requestBatteryOpt() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        } catch (_: Exception) {}
    }

    private fun launchRoblox() {
        prefs.edit().putString("place_id", placeInput.text?.toString()?.trim() ?: "").apply()
        val raw = placeInput.text?.toString()?.trim().orEmpty()
        val placeId = extractPlaceId(raw)
        val ok = FlagManager.launchRobloxSmart(this, placeId) { msg ->
            runOnUiThread { appendLog(msg) }
        }
        if (!ok) {
            toast("Delta/Roblox не найден — установи Delta Executor Roblox")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.roblox.client")))
            } catch (_: Exception) {}
        }
    }

    private fun extractPlaceId(raw: String): String? {
        if (raw.isBlank()) return null
        if (raw.all { it.isDigit() }) return raw
        val regex = Regex("""(?:games/|placeId=|place\?id=)(\d+)""")
        return regex.find(raw)?.groupValues?.getOrNull(1)
    }

    private fun showJsonEditor() {
        val edit = EditText(this).apply {
            setText(FlagBuilder.build(state))
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF12141F.toInt())
            typeface = Typeface.MONOSPACE
            minLines = 14
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("JSON редактор")
            .setView(edit)
            .setPositiveButton("Применить") { _, _ ->
                if (FlagManager.needsAllFilesAccess()) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                    toast("Выдай «Все файлы» и нажми Применить ещё раз")
                    return@setPositiveButton
                }
                val raw = edit.text.toString()
                val report = FlagManager.writeFlagsDetailed(this, raw) { appendLog(it) }
                appendLog(report.summary())
                if (report.ok) {
                    // не даём UI-State перезатереть ручной JSON при следующем apply
                    prefs.edit().putString("manual_json", report.filteredJson).apply()
                    toast("JSON OK verified=${report.verifiedPaths.size}")
                } else {
                    toast("JSON APPLY FAIL")
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }


    // ───────────── STATE ─────────────

    private fun clearManualJson() {
        prefs.edit().remove("manual_json").apply()
    }

    private fun saveState() {
        prefs.edit()
            .putInt("fps", state.fps)
            .putBoolean("showFps", state.showFps)
            .putInt("graphicsApi", state.graphicsApi)
            .putBoolean("preferVulkan", state.preferVulkan)
            .putBoolean("graySky", state.graySky)
            .putBoolean("noMsaa", state.noMsaa)
            .putInt("msaaLevel", state.msaaLevel)
            .putInt("frmLevel", state.frmLevel)
            .putInt("renderMode", state.renderMode)
            .putString("dynamicRes", state.dynamicRes)
            .putBoolean("minGraphics", state.minGraphics)
            .putBoolean("reduceMotion", state.reduceMotion)
            .putBoolean("haptic", state.haptic)
            .putBoolean("oneClickBoost", state.oneClickBoost)
            .putBoolean("simplifyModels", state.simplifyModels)
            .putBoolean("lowTextures", state.lowTextures)
            .putBoolean("meshOpt", state.meshOpt)
            .putBoolean("preloadModels", state.preloadModels)
            .putBoolean("lessShadows", state.lessShadows)
            .putBoolean("noPostFx", state.noPostFx)
            .putBoolean("noGrass", state.noGrass)
            .putBoolean("hideNames", state.hideNames)
            .putBoolean("noBlurMenu", state.noBlurMenu)
            .putBoolean("noVfx", state.noVfx)
            .putBoolean("noTextures", state.noTextures)
            .putBoolean("animCache", state.animCache)
            .putBoolean("physics60", state.physics60)
            .putBoolean("disableTelemetry", state.disableTelemetry)
            .putBoolean("pauseVoxelizer", state.pauseVoxelizer)
            .putBoolean("noFog", state.noFog)
            .putBoolean("lowWater", state.lowWater)
            .putInt("shadowIntensity", state.shadowIntensity)
            .putInt("textureOverride", state.textureOverride)
            .putInt("lightingMode", state.lightingMode)
            .putBoolean("futureBright", state.futureBright)
            .putBoolean("maxShadows", state.maxShadows)
            .putBoolean("highMsaa", state.highMsaa)
            .putBoolean("fixFog", state.fixFog)
            .putBoolean("cpuRelief", state.cpuRelief)
            .putBoolean("netSmooth", state.netSmooth)
            .putBoolean("keepScreenOn", keepScreenOn)
            .putBoolean("dnd", dnd)
            .putBoolean("killBg", killBg)
            .putBoolean("cleanRam", cleanRam)
            .putBoolean("noBatteryOpt", noBatteryOpt)
            .apply()
    }


    private fun loadState() {
        state.fps = prefs.getInt("fps", state.fps)
        state.showFps = prefs.getBoolean("showFps", state.showFps)
        state.graphicsApi = prefs.getInt("graphicsApi", state.graphicsApi)
        state.preferVulkan = prefs.getBoolean("preferVulkan", state.preferVulkan)
        state.graySky = prefs.getBoolean("graySky", state.graySky)
        state.noMsaa = prefs.getBoolean("noMsaa", state.noMsaa)
        state.msaaLevel = prefs.getInt("msaaLevel", state.msaaLevel)
        state.frmLevel = prefs.getInt("frmLevel", state.frmLevel)
        state.renderMode = prefs.getInt("renderMode", state.renderMode)
        state.dynamicRes = prefs.getString("dynamicRes", state.dynamicRes) ?: state.dynamicRes
        state.minGraphics = prefs.getBoolean("minGraphics", state.minGraphics)
        state.reduceMotion = prefs.getBoolean("reduceMotion", state.reduceMotion)
        state.haptic = prefs.getBoolean("haptic", state.haptic)
        state.oneClickBoost = prefs.getBoolean("oneClickBoost", state.oneClickBoost)
        state.simplifyModels = prefs.getBoolean("simplifyModels", state.simplifyModels)
        state.lowTextures = prefs.getBoolean("lowTextures", state.lowTextures)
        state.meshOpt = prefs.getBoolean("meshOpt", state.meshOpt)
        state.preloadModels = prefs.getBoolean("preloadModels", state.preloadModels)
        state.lessShadows = prefs.getBoolean("lessShadows", state.lessShadows)
        state.noPostFx = prefs.getBoolean("noPostFx", state.noPostFx)
        state.noGrass = prefs.getBoolean("noGrass", state.noGrass)
        state.hideNames = prefs.getBoolean("hideNames", state.hideNames)
        state.noBlurMenu = prefs.getBoolean("noBlurMenu", state.noBlurMenu)
        state.noVfx = prefs.getBoolean("noVfx", state.noVfx)
        state.noTextures = prefs.getBoolean("noTextures", state.noTextures)
        state.animCache = prefs.getBoolean("animCache", state.animCache)
        state.physics60 = prefs.getBoolean("physics60", state.physics60)
        state.disableTelemetry = prefs.getBoolean("disableTelemetry", state.disableTelemetry)
        state.pauseVoxelizer = prefs.getBoolean("pauseVoxelizer", state.pauseVoxelizer)
        state.noFog = prefs.getBoolean("noFog", state.noFog)
        state.lowWater = prefs.getBoolean("lowWater", state.lowWater)
        state.shadowIntensity = prefs.getInt("shadowIntensity", state.shadowIntensity)
        state.textureOverride = prefs.getInt("textureOverride", state.textureOverride)
        state.lightingMode = prefs.getInt("lightingMode", state.lightingMode)
        state.futureBright = prefs.getBoolean("futureBright", state.futureBright)
        state.maxShadows = prefs.getBoolean("maxShadows", state.maxShadows)
        state.highMsaa = prefs.getBoolean("highMsaa", state.highMsaa)
        state.fixFog = prefs.getBoolean("fixFog", state.fixFog)
        state.cpuRelief = prefs.getBoolean("cpuRelief", state.cpuRelief)
        state.netSmooth = prefs.getBoolean("netSmooth", state.netSmooth)
        keepScreenOn = prefs.getBoolean("keepScreenOn", keepScreenOn)
        dnd = prefs.getBoolean("dnd", dnd)
        killBg = prefs.getBoolean("killBg", killBg)
        cleanRam = prefs.getBoolean("cleanRam", cleanRam)
        noBatteryOpt = prefs.getBoolean("noBatteryOpt", noBatteryOpt)
    }


    // ───────────── DEVICE INFO ─────────────

    @SuppressLint("SetTextI18n")
    private fun refreshDeviceInfo() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeMb = mi.availMem / (1024 * 1024)
        val totalMb = mi.totalMem / (1024 * 1024)

        var tempC = "?"
        try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            tempC = String.format("%.1f", temp / 10.0)
        } catch (_: Exception) {}

        val hz = try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                display?.mode?.refreshRate ?: windowManager.defaultDisplay.refreshRate
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.refreshRate
            }
        } catch (_: Exception) { 60f }

        deviceInfoText.text = "RAM свободно: ${freeMb} / ${totalMb} МБ\nБатарея: ${tempC}°C\nЭкран: ${hz.toInt()} Гц"
        if (hz < 90f) {
            hzWarnText.visibility = View.VISIBLE
            hzWarnText.text = "Экран ~${hz.toInt()} Гц — включи 120 Гц в настройках телефона, иначе FPS будет ограничен"
        } else {
            hzWarnText.visibility = View.GONE
        }
    }

    // ───────────── NAV / UI ─────────────

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_nav)
            setPadding(dp(4), dp(10), dp(4), dp(16))
            gravity = Gravity.CENTER
        }
        fun tab(id: String, title: String) = TextView(this).apply {
            text = title
            setTextColor(color(R.color.text_sec))
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showTab(id) }
        }
        bar.addView(tab("home", "Дом"))
        bar.addView(tab("ff", "FastFlag"))
        val launch = TextView(this).apply {
            text = "▶"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_launch)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                marginStart = dp(4); marginEnd = dp(4)
            }
            setOnClickListener { applyAndLaunch() }
        }
        bar.addView(launch)
        bar.addView(tab("game", "Режим"))
        bar.addView(tab("presets", "Пресеты"))
        return bar
    }

    private fun showTab(id: String) {
        contentHost.removeAllViews()
        // rebuild home to refresh custom preset button
        if (id == "home") pages["home"] = buildHomePage()
        if (id == "ff") pages["ff"] = buildFastFlagPage()
        contentHost.addView(pages[id] ?: pages["home"])
        refreshDeviceInfo()
    }

    private fun updateStatus() {
        val mode = FlagManager.accessMode(this)
        val rb = if (FlagManager.isRobloxInstalled(this)) " | client: com.roblox.client" else " | client: нет"
        val delta = FlagManager.findDeltaPackage(this)
        val deltaPart = if (delta != null) " | DeltaUI: $delta" else ""
        statusText.text = "Доступ: $mode$rb$deltaPart"
    }

    private fun appendLog(msg: String) {
        if (::logText.isInitialized) logText.append("\n$msg")
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            block()
        }

    private fun section(t: String) = TextView(this).apply {
        text = t
        setTextColor(color(R.color.accent))
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(8))
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        setTextColor(color(R.color.text_sec))
        textSize = 12f
        setPadding(0, 0, 0, dp(6))
    }

    private fun LinearLayout.toggle(key: String, title: String, default: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this@MainActivity).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(SwitchCompat(this@MainActivity).apply {
            isChecked = default
            setOnCheckedChangeListener { _, v -> onChange(v) }
        })
        addView(row)
    }

    private fun dropdownRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        })
        val value = TextView(this).apply {
            text = options.getOrElse(selected) { options[0] }
            setTextColor(color(R.color.accent2))
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setItems(options.toTypedArray()) { _, which ->
                        text = options[which]
                        onSelect(which)
                    }
                    .show()
            }
        }
        row.addView(value)
        return row
    }

    private fun btn(text: String, secondary: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(if (secondary) 0xFFCCCCDD.toInt() else 0xFFFFFFFF.toInt())
            background = ContextCompat.getDrawable(
                this@MainActivity,
                if (secondary) R.drawable.bg_btn_secondary else R.drawable.bg_btn_primary
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
