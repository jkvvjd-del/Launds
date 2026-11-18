package com.manu.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import rikka.shizuku.Shizuku
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method

object FlagManager {

    private const val PREFS = "manu_flags"
    private const val KEY_JSON = "last_json"

    private val ELEVATED_TARGETS = listOf(
        "/data/local/tmp/ClientAppSettings.json",
        "/data/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json",
        "/data/data/com.roblox.client.vnggames/files/ClientSettings/ClientAppSettings.json"
    )

    // Реальные пути, которые читает клиент (Delta = com.roblox.client)
    private val ROBLOX_CLIENT_TARGETS = listOf(
        "/sdcard/Android/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json",
        "/storage/emulated/0/Android/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json",
        "/sdcard/Android/data/com.roblox.client.vnggames/files/ClientSettings/ClientAppSettings.json",
        "/storage/emulated/0/Android/data/com.roblox.client.vnggames/files/ClientSettings/ClientAppSettings.json"
    )

    private val EXTRA_TARGETS = listOf(
        "/sdcard/Roblox/ClientSettings/ClientAppSettings.json",
        "/storage/emulated/0/Roblox/ClientSettings/ClientAppSettings.json"
    )

    private val NO_ROOT_TARGETS: List<String>
        get() = ROBLOX_CLIENT_TARGETS + EXTRA_TARGETS

    private val ALL_TARGETS: List<String>
        get() = ELEVATED_TARGETS + NO_ROOT_TARGETS

    private val DELTA_PACKAGES = listOf(
        "com.delta.executor", "com.delta.mobile", "com.deltaexecutor",
        "com.delta.executor.mobile", "org.delta.executor", "com.delta",
        "com.deltaexecutor.android", "com.delta.android", "xyz.delta.executor",
        "com.delta.exec"
    )

    private val STOCK_ROBLOX_PACKAGES = listOf(
        "com.roblox.client",
        "com.roblox.client.vnggames"
    )

    data class ApplyReport(
        val ok: Boolean,
        val filteredJson: String,
        val kept: List<String>,
        val removed: List<String>,
        val writtenPaths: List<String>,
        val failedPaths: List<String>,
        val verifiedPaths: List<String>,
        val mode: String
    ) {
        fun summary(): String = buildString {
            appendLine("mode=$mode ok=$ok")
            appendLine("kept=${kept.size} removed=${removed.size}")
            appendLine("written=${writtenPaths.size} verified=${verifiedPaths.size}")
            if (removed.isNotEmpty()) appendLine("removed: ${removed.take(12).joinToString()}")
            if (verifiedPaths.isNotEmpty()) appendLine("verified: ${verifiedPaths.joinToString()}")
            if (failedPaths.isNotEmpty()) appendLine("failed: ${failedPaths.take(6).joinToString()}")
        }
    }

    fun hasRoot(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).waitFor() == 0
    } catch (_: Exception) {
        false
    }

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun accessMode(ctx: Context? = null): String = when {
        hasRoot() -> "Root ✓"
        shizukuReady() -> "Shizuku ✓"
        else -> "NoRoot/Direct"
    }

    fun requestShizukuPermission(requestCode: Int = 0) {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
        }
    }

    fun saveLastJson(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, json).apply()
    }

    fun loadLastJson(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, "") ?: ""

    private fun newShizukuProcess(cmd: Array<String>): Process? = try {
        val method: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(null, cmd, null, null) as Process
    } catch (_: Exception) {
        null
    }

    fun runElevated(cmd: String, log: (String) -> Unit = {}): Boolean {
        return try {
            val proc = when {
                hasRoot() -> Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                shizukuReady() -> newShizukuProcess(arrayOf("sh", "-c", cmd))
                else -> {
                    log("нет Root/Shizuku — elevated skip")
                    return false
                }
            } ?: return false
            val out = proc.inputStream.bufferedReader().readText().trim()
            val err = proc.errorStream.bufferedReader().readText().trim()
            val code = proc.waitFor()
            log("elevated exit=$code out=${out.take(120)} err=${err.take(80)}")
            code == 0 && !out.contains("FAIL")
        } catch (e: Exception) {
            log("elevated: ${e.message}")
            false
        }
    }

    private fun normalizeJson(s: String): String = try {
        org.json.JSONObject(s).toString()
    } catch (_: Exception) {
        s.replace(Regex("\s+"), "")
    }

    /** Read-back: файл есть и JSON совпадает с ожидаемым (нормализованно). Без требования FPS-ключа. */
    private fun verifyFile(path: String, expected: String): Boolean {
        return try {
            val f = File(path)
            if (!f.exists() || f.length() == 0L) return false
            val body = f.readText()
            normalizeJson(body) == normalizeJson(expected)
        } catch (_: Exception) {
            false
        }
    }

    /** Verify через elevated (для /data/data/...) */
    private fun verifyFileElevated(path: String, expected: String, log: (String) -> Unit): Boolean {
        val local = File.createTempFile("manu_chk", ".json")
        return try {
            val cmd = "cp '$path' '${local.absolutePath}' 2>/dev/null; chmod 666 '${local.absolutePath}' 2>/dev/null; if [ -s '${local.absolutePath}' ]; then echo OK; else echo FAIL; fi"
            if (!runElevated(cmd, log)) return false
            verifyFile(local.absolutePath, expected)
        } catch (_: Exception) {
            false
        } finally {
            try { local.delete() } catch (_: Exception) {}
        }
    }

    private fun writeOne(path: String, json: String, log: (String) -> Unit): Boolean {
        return try {
            val f = File(path)
            f.parentFile?.mkdirs()
            f.writeText(json)
            try {
                f.setReadable(true, false)
                f.setWritable(true, false)
            } catch (_: Exception) {
            }
            val ok = verifyFile(path, json)
            log(if (ok) "WRITE+VERIFY OK: $path (${f.length()}b)" else "WRITE but VERIFY FAIL: $path")
            ok
        } catch (e: Exception) {
            log("WRITE FAIL: $path (${e.message})")
            false
        }
    }

    private fun elevatedCopyVerified(localPath: String, targets: List<String>, expected: String, log: (String) -> Unit): List<String> {
        val verified = mutableListOf<String>()
        for (t in targets) {
            val dir = t.substringBeforeLast('/')
            val cmd = "mkdir -p '$dir' 2>/dev/null ; cp '$localPath' '$t' 2>/dev/null ; chmod 666 '$t' 2>/dev/null ; if [ -f '$t' ] && [ -s '$t' ]; then echo OK; else echo FAIL; fi"
            val procOk = runElevated(cmd, log)
            val ok = if (procOk) {
                // /data/data не читается без elevated — проверяем через elevated copy-back
                if (t.startsWith("/data/")) verifyFileElevated(t, expected, log) else verifyFile(t, expected)
            } else false
            if (ok) {
                verified.add(t)
                log("ELEV VERIFY OK: $t")
            } else {
                log("ELEV VERIFY FAIL: $t")
            }
        }
        return verified
    }


    /**
     * Полный apply:
     * 1) whitelist filter
     * 2) write to Roblox client paths
     * 3) real verify read-back
     * 4) report
     */
    fun writeFlags(ctx: Context, rawJson: String, log: (String) -> Unit = {}): Boolean {
        val report = writeFlagsDetailed(ctx, rawJson, log)
        return report.ok
    }

    fun writeFlagsDetailed(ctx: Context, rawJson: String, log: (String) -> Unit = {}): ApplyReport {
        val filter = FlagWhitelist.filter(rawJson, strict = true)
        log("whitelist: kept=${filter.kept.size} removed=${filter.removed.size} blocked=${filter.blocked.size}")
        if (filter.removed.isNotEmpty()) log("unsupported: ${filter.removed.take(15).joinToString()}")
        if (filter.blocked.isNotEmpty()) log("blocked: ${filter.blocked.joinToString()}")
        if (filter.typeErrors.isNotEmpty()) log("type errors: ${filter.typeErrors.joinToString()}")

        val json = filter.json
        saveLastJson(ctx, json)

        val cache = ctx.externalCacheDir ?: ctx.cacheDir
        val local = File(cache, "ClientAppSettings.json")
        local.writeText(json)
        log("local buffer: ${local.absolutePath} (${local.length()}b)")

        val written = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val verified = mutableListOf<String>()

        val mode = when {
            hasRoot() -> "root"
            shizukuReady() -> "shizuku"
            else -> "noroot"
        }

        // 1) direct write в пути клиента Roblox/Delta
        for (path in NO_ROOT_TARGETS) {
            if (writeOne(path, json, log)) {
                written.add(path)
                verified.add(path)
            } else {
                failed.add(path)
            }
        }

        // 2) elevated для data/data и local/tmp
        if (hasRoot() || shizukuReady()) {
            val elevVerified = elevatedCopyVerified(local.absolutePath, ELEVATED_TARGETS, json, log)
            written.addAll(elevVerified)
            verified.addAll(elevVerified)
            for (t in ELEVATED_TARGETS) {
                if (t !in elevVerified) failed.add(t)
            }
        } else {
            log("elevated skipped (no root/shizuku)")
        }

        // Успех ТОЛЬКО если verified путь принадлежит клиенту Roblox/Delta
        val ok = verified.any { p ->
            p.contains("com.roblox.client") || p.contains("com.roblox.client.vnggames")
        }

        log(
            if (ok) "APPLY OK verified=${verified.size}"
            else "APPLY FAIL — ни один путь Roblox не подтверждён. Выдай «Все файлы» или Shizuku"
        )

        val report = ApplyReport(
            ok = ok,
            filteredJson = json,
            kept = filter.kept,
            removed = filter.removed,
            writtenPaths = written.distinct(),
            failedPaths = failed.distinct(),
            verifiedPaths = verified.distinct(),
            mode = mode
        )
        log(report.summary())
        return report
    }

    fun reapplyLast(ctx: Context, log: (String) -> Unit = {}): Boolean {
        val json = loadLastJson(ctx)
        if (json.isBlank()) {
            log("reapply: нет сохранённых флагов")
            return false
        }
        log("reapply last JSON…")
        return writeFlags(ctx, json, log)
    }

    fun clearFlags(ctx: Context, log: (String) -> Unit = {}): Boolean {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_JSON).apply()
        var remaining = 0
        for (p in NO_ROOT_TARGETS) {
            try {
                val f = File(p)
                if (f.exists()) {
                    if (!f.delete()) remaining++
                }
            } catch (_: Exception) {
                remaining++
            }
        }
        val targets = (NO_ROOT_TARGETS + ELEVATED_TARGETS)
        val script = targets.joinToString(" ; ") { p ->
            "rm -f '$p' 2>/dev/null"
        } + " ; " + targets.joinToString(" ; ") { p ->
            "if [ -f '$p' ]; then echo FAIL; fi"
        } + " ; echo DONE"
        val elevOut = StringBuilder()
        val elev = runElevated(script) { elevOut.appendLine(it); log(it) }
        val elevFail = elevOut.toString().contains("FAIL")
        // success: nothing left on direct paths AND elevated didn't report FAIL
        val leftDirect = NO_ROOT_TARGETS.count { File(it).exists() }
        val ok = leftDirect == 0 && (!elev || !elevFail)
        log("clearFlags remainingDirect=$leftDirect elev=$elev elevFail=$elevFail ok=$ok")
        return ok
    }



    fun clearRobloxCache(log: (String) -> Unit = {}): Boolean {
        val dirs = listOf(
            File("/sdcard/Android/data/com.roblox.client/cache"),
            File("/storage/emulated/0/Android/data/com.roblox.client/cache"),
            File("/sdcard/Android/data/com.roblox.client.vnggames/cache"),
            File("/storage/emulated/0/Android/data/com.roblox.client.vnggames/cache")
        )
        var deletedSomething = false
        for (d in dirs) {
            try {
                if (!d.exists()) continue
                d.listFiles()?.forEach { f ->
                    try {
                        if (f.deleteRecursively()) deletedSomething = true
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        val left = dirs.any { d ->
            try { d.exists() && d.listFiles()?.isNotEmpty() == true } catch (_: Exception) { false }
        }
        val cmd =
            "rm -rf /sdcard/Android/data/com.roblox.client/cache/* " +
            "/storage/emulated/0/Android/data/com.roblox.client/cache/* " +
            "/sdcard/Android/data/com.roblox.client.vnggames/cache/* " +
            "/storage/emulated/0/Android/data/com.roblox.client.vnggames/cache/* 2>/dev/null; " +
            "left=0; " +
            "for d in /sdcard/Android/data/com.roblox.client/cache " +
            "/storage/emulated/0/Android/data/com.roblox.client/cache " +
            "/sdcard/Android/data/com.roblox.client.vnggames/cache " +
            "/storage/emulated/0/Android/data/com.roblox.client.vnggames/cache; do " +
            "if [ -d \"$d\" ] && [ -n \"$(ls -A \"$d\" 2>/dev/null)\" ]; then left=1; fi; done; " +
            "if [ \"$left\" = 0 ]; then echo OK; else echo FAIL; fi"
        val elevOut = StringBuilder()
        val elev = runElevated(cmd) { elevOut.appendLine(it); log(it) }
        val out = elevOut.toString()
        val elevOk = elev && out.contains("OK") && !out.contains("FAIL")
        val ok = elevOk || (deletedSomething && !left)
        log("cache clear deleted=$deletedSomething leftDirect=$left elevOk=$elevOk ok=$ok")
        return ok
    }





    fun killBackground(log: (String) -> Unit = {}): Boolean {
        return runElevated("am kill-all 2>/dev/null; echo OK", log)
    }

    fun needsAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    fun findDeltaPackage(ctx: Context): String? {
        for (pkg in DELTA_PACKAGES) {
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
            }
        }
        try {
            val apps = ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val label = ctx.packageManager.getApplicationLabel(app).toString().lowercase()
                if (label.contains("delta") && (label.contains("exec") || label.contains("executor"))) {
                    return app.packageName
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun isDeltaInstalled(ctx: Context): Boolean =
        findDeltaPackage(ctx) != null || isRobloxInstalled(ctx)

    fun isRobloxInstalled(ctx: Context): Boolean {
        for (pkg in STOCK_ROBLOX_PACKAGES) {
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun findDeltaRobloxPackage(ctx: Context): String? = null // Delta = com.roblox.client

    fun launchDelta(ctx: Context, log: (String) -> Unit = {}): Boolean {
        val pkg = findDeltaPackage(ctx) ?: return false
        return try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            log("Delta Executor: $pkg")
            true
        } catch (e: Exception) {
            log("launchDelta: ${e.message}")
            false
        }
    }

    /** Запуск: com.roblox.client (Delta) как обычный Roblox */
    fun launchRobloxSmart(ctx: Context, placeId: String?, log: (String) -> Unit = {}): Boolean {
        val pm = ctx.packageManager
        for (pkg in STOCK_ROBLOX_PACKAGES) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!placeId.isNullOrBlank()) {
                    try {
                        intent.data = Uri.parse("roblox://placeId=$placeId")
                        intent.putExtra("placeId", placeId)
                    } catch (_: Exception) {
                    }
                }
                ctx.startActivity(intent)
                log("LAUNCH Roblox/Delta: $pkg" + (if (placeId != null) " place=$placeId" else ""))
                return true
            } catch (_: Exception) {
            }
        }
        if (!placeId.isNullOrBlank()) {
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("roblox://placeId=$placeId"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                log("LAUNCH deep-link place=$placeId")
                return true
            } catch (_: Exception) {
            }
        }
        log("com.roblox.client не найден")
        return false
    }
}
