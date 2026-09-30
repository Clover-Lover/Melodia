package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.log.AppLogger
import java.util.concurrent.TimeUnit

private const val TAG = "AutoStart"
private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val VALUE_NAME = "Melodia"
private const val REG_TIMEOUT_SECONDS = 5L

// 开机自启以注册表 Run 项为准；阻塞调用，需在 IO 线程执行
object AutoStart {

    // jpackage 启动器注入的 exe 路径，开发环境运行时为空
    val exePath: String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    val isSupported: Boolean get() = exePath != null

    fun isEnabled(): Boolean {
        if (!isSupported) return false
        val result = runReg("query", RUN_KEY, "/v", VALUE_NAME) ?: return false
        return result.exitCode == 0 && result.output.contains(exePath!!, ignoreCase = true)
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val path = exePath ?: return false
        val result = if (enabled) {
            runReg("add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$path\"", "/f")
        } else {
            runReg("delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        return result?.exitCode == 0
    }

    private class RegResult(val exitCode: Int, val output: String)

    private fun runReg(vararg args: String): RegResult? = try {
        val process = ProcessBuilder(listOf("reg") + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(REG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            AppLogger.w(TAG, "reg ${args.first()} 超时")
            null
        } else {
            RegResult(process.exitValue(), output)
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "reg ${args.first()} 执行失败", e)
        null
    }
}
