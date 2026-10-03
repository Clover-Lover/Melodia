package com.lin0721.linmusic.feature.source.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.LxPluginInfo
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourceHttpClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LxPluginEngine"

// LX Music JS 插件沙盒引擎（基于 Android 原生隐藏 WebView）
class LxPluginEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var webView: WebView? = null
    private var isEngineReady = false

    // 插件初始化完成状态
    private var initDeferred = CompletableDeferred<List<String>>()

    // 等待中的直链解析任务
    private val pendingResolves = ConcurrentHashMap<Int, CompletableDeferred<String?>>()
    private val nextCallId = AtomicInteger(0)

    // 当前插件支持的平台列表（例如 "kw", "kg", "tx", "mg", "wy"）
    @Volatile
    var supportedSources: List<String> = emptyList()
        private set

    init {
        scope.launch {
            initWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        if (webView != null) return
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    isEngineReady = true
                    AppLogger.d(TAG, "LX 插件沙盒环境载入完成")
                }
            }
            addJavascriptInterface(HostBridge(), "MelodiaHostBridge")
        }
        val htmlContent = buildShimHtml()
        wv.loadDataWithBaseURL("https://melodia.local", htmlContent, "text/html", "UTF-8", null)
        webView = wv
    }

    // 载入并初始化 JS 脚本
    suspend fun loadScript(scriptText: String): Result<LxPluginInfo> = withContext(Dispatchers.Main) {
        try {
            initWebView()
            initDeferred = CompletableDeferred()

            // 提取头注释元信息
            val headerInfo = parseScriptHeader(scriptText)

            // 执行脚本
            webView?.evaluateJavascript(scriptText) {
                AppLogger.d(TAG, "脚本注入执行完毕")
            }

            // 等待脚本派发 inited 事件（超时 8 秒）
            val sources = withTimeoutOrNull(8000L) {
                initDeferred.await()
            } ?: emptyList()

            supportedSources = sources

            val pluginInfo = headerInfo.copy(
                sources = sources,
                rawScript = scriptText
            )

            AppLogger.i(TAG, "LX 插件加载成功: ${pluginInfo.name} v${pluginInfo.version}, 支持平台: $sources")
            Result.success(pluginInfo)
        } catch (e: Exception) {
            AppLogger.e(TAG, "LX 插件加载失败", e)
            Result.failure(e)
        }
    }

    // 卸载重置当前插件
    fun reset() {
        supportedSources = emptyList()
        scope.launch {
            val htmlContent = buildShimHtml()
            webView?.loadDataWithBaseURL("https://melodia.local", htmlContent, "text/html", "UTF-8", null)
        }
    }

    // 调用插件获取音乐直链
    suspend fun resolveMusicUrl(
        source: String,
        songId: String,
        songName: String,
        singer: String,
        albumName: String,
        durationMs: Long,
        quality: String
    ): String? = withContext(Dispatchers.IO) {
        val wv = webView ?: return@withContext null
        val callId = nextCallId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        pendingResolves[callId] = deferred

        val lxQuality = mapQualityToLx(quality)

        // 构造符合 LX 规范的 musicInfo
        val musicInfoJson = org.json.JSONObject().apply {
            put("id", songId)
            put("songmid", songId)
            put("name", songName)
            put("singer", singer)
            put("albumName", albumName)
            put("interval", formatDuration(durationMs))
        }.toString()

        withContext(Dispatchers.Main) {
            val safeMusicInfo = escapeJsString(musicInfoJson)
            val jsCode = "window.__melodia_resolve_music_url($callId, '$source', '$safeMusicInfo', '$lxQuality')"
            wv.evaluateJavascript(jsCode, null)
        }

        try {
            withTimeoutOrNull(12000L) { deferred.await() }
        } catch (e: Exception) {
            AppLogger.w(TAG, "解析直链超时或失败: ${e.message}")
            null
        } finally {
            pendingResolves.remove(callId)
        }
    }

    // 桥接供 JS 调用的接口
    inner class HostBridge {

        @JavascriptInterface
        fun httpRequest(id: Int, url: String, method: String, headersJson: String, body: String) {
            scope.launch(Dispatchers.IO) {
                try {
                    val headersMap = mutableMapOf<String, String>()
                    if (headersJson.isNotBlank()) {
                        runCatching {
                            val jsonObj = Json.parseToJsonElement(headersJson).jsonObject
                            jsonObj.forEach { (k, v) ->
                                headersMap[k] = v.jsonPrimitive.content
                            }
                        }
                    }

                    val respBody = if (method.equals("POST", ignoreCase = true)) {
                        SourceHttpClient.post(url, body, headersMap)
                    } else {
                        SourceHttpClient.get(url, headersMap)
                    }

                    val respJson = org.json.JSONObject().apply {
                        put("statusCode", 200)
                    }.toString()

                    withContext(Dispatchers.Main) {
                        val safeBody = escapeJsString(respBody)
                        val safeResp = escapeJsString(respJson)
                        webView?.evaluateJavascript(
                            "window.__melodia_handle_http_response($id, null, '$safeResp', '$safeBody')",
                            null
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val safeErr = escapeJsString(e.message ?: "Network error")
                        webView?.evaluateJavascript(
                            "window.__melodia_handle_http_response($id, '$safeErr', null, null)",
                            null
                        )
                    }
                }
            }
        }

        @JavascriptInterface
        fun onEvent(event: String, dataJson: String) {
            if (event == "inited") {
                val sourcesList = mutableListOf<String>()
                runCatching {
                    val root = Json.parseToJsonElement(dataJson).jsonObject
                    val sourcesObj = root["sources"]?.jsonObject
                    sourcesObj?.keys?.forEach { sourcesList.add(it) }
                }
                initDeferred.complete(sourcesList)
            }
        }

        @JavascriptInterface
        fun onResolveResult(callId: Int, url: String?, error: String?) {
            val deferred = pendingResolves[callId]
            if (deferred != null) {
                if (url.isNullOrBlank()) {
                    deferred.complete(null)
                } else {
                    deferred.complete(url)
                }
            }
        }

        @JavascriptInterface
        fun md5(input: String): String {
            return try {
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(input.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun parseScriptHeader(script: String): LxPluginInfo {
        var name = "未命名插件"
        var version = "1.0.0"
        var author = "未知"
        var desc = ""

        val lines = script.lines().take(50)
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.contains("@name") -> name = trimmed.substringAfter("@name").trim()
                trimmed.contains("@version") -> version = trimmed.substringAfter("@version").trim()
                trimmed.contains("@author") -> author = trimmed.substringAfter("@author").trim()
                trimmed.contains("@description") -> desc = trimmed.substringAfter("@description").trim()
            }
        }
        return LxPluginInfo(name = name, version = version, author = author, description = desc)
    }

    private fun mapQualityToLx(quality: String): String = when (quality) {
        "lossless" -> "flac"
        "hires" -> "flac24bit"
        "exhigh" -> "320k"
        else -> "128k"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    private fun escapeJsString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun buildShimHtml(): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"></head>
        <body>
        <script>
        (function() {
            var pendingCallbacks = {};
            var reqIdCounter = 0;
            var requestHandler = null;

            window.lx = {
                version: "2.0.0",
                env: "mobile",
                EVENT_NAMES: {
                    request: "request",
                    inited: "inited",
                    updateAlert: "updateAlert"
                },
                request: function(url, opts, callback) {
                    var id = ++reqIdCounter;
                    pendingCallbacks[id] = callback;
                    var options = opts || {};
                    var method = options.method || 'GET';
                    var headers = JSON.stringify(options.headers || {});
                    var body = typeof options.body === 'string' ? options.body : (options.body ? JSON.stringify(options.body) : '');
                    MelodiaHostBridge.httpRequest(id, url, method, headers, body);
                    return function() { delete pendingCallbacks[id]; };
                },
                on: function(event, handler) {
                    if (event === 'request') {
                        requestHandler = handler;
                    }
                    return Promise.resolve();
                },
                send: function(event, data) {
                    MelodiaHostBridge.onEvent(event, JSON.stringify(data || {}));
                    return Promise.resolve();
                },
                utils: {
                    crypto: {
                        md5: function(str) { return MelodiaHostBridge.md5(str); }
                    },
                    buffer: {
                        from: function(data) { return data; },
                        bufToString: function(buf) { return String(buf); }
                    }
                }
            };

            window.__melodia_handle_http_response = function(id, err, respJson, body) {
                var cb = pendingCallbacks[id];
                if (!cb) return;
                delete pendingCallbacks[id];
                var resp = null;
                try { resp = respJson ? JSON.parse(respJson) : null; } catch(e) {}
                cb(err ? new Error(err) : null, resp, body);
            };

            window.__melodia_resolve_music_url = function(callId, source, musicInfoJson, quality) {
                if (!requestHandler) {
                    MelodiaHostBridge.onResolveResult(callId, null, "No handler");
                    return;
                }
                var musicInfo = JSON.parse(musicInfoJson);
                Promise.resolve().then(function() {
                    return requestHandler({
                        source: source,
                        action: 'musicUrl',
                        info: {
                            type: quality,
                            musicInfo: musicInfo
                        }
                    });
                }).then(function(result) {
                    var url = typeof result === 'string' ? result : (result && result.url ? result.url : null);
                    MelodiaHostBridge.onResolveResult(callId, url, null);
                }).catch(function(err) {
                    MelodiaHostBridge.onResolveResult(callId, null, err ? err.message : "Error");
                });
            };
        })();
        </script>
        </body>
        </html>
    """.trimIndent()
}
