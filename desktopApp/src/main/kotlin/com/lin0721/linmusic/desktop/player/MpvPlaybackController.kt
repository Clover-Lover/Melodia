package com.lin0721.linmusic.desktop.player

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.NowPlaying
import com.lin0721.linmusic.core.player.PlayMode
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.desktop.player.mpv.MpvEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val TAG = "MpvPlayback"

// 连续取地址失败达到该数即停止，避免整队无版权时空转
private const val MAX_CONSECUTIVE_FAILURES = 3

// 听不满该时长的曲目不上报播放时长，与 Android 端一致
private const val MIN_REPORT_PLAYED_MS = 5_000L

// 上一首按钮：已播放超过该时长时回到本曲开头
private const val RESTART_THRESHOLD_MS = 3_000L

class MpvPlaybackController(
    private val repository: PlaybackRepository,
    private val scope: CoroutineScope
) : PlaybackController, MpvEngine.Listener {

    private val engine = MpvEngine(this)

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    override val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    override val sleepTimerRemaining: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    private val _playContext = MutableStateFlow<String?>(null)
    override val playContext: StateFlow<String?> = _playContext.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LIST_LOOP)
    override val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentQueueItem = MutableStateFlow<QueueItem?>(null)
    override val currentQueueItem: StateFlow<QueueItem?> = _currentQueueItem.asStateFlow()

    private val _previousQueueItem = MutableStateFlow<QueueItem?>(null)
    override val previousQueueItem: StateFlow<QueueItem?> = _previousQueueItem.asStateFlow()

    private val _nextQueueItem = MutableStateFlow<QueueItem?>(null)
    override val nextQueueItem: StateFlow<QueueItem?> = _nextQueueItem.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    // 桌面端专用提示（取地址失败等），由界面层弹出
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var playJob: Job? = null
    private var consecutiveFailures = 0

    // 播放时长上报：累计实际发声的挂钟时长，暂停段不计
    private var reportingSongId: Long? = null
    private var playedMs = 0L
    private var playingSince: Long? = null

    override suspend fun initController() = Unit

    override suspend fun shouldBlockPlaybackOnMobile(): Boolean = false

    override fun playQueue(items: List<QueueItem>, startIndex: Int, playContext: String?) {
        if (items.isEmpty()) return
        _queue.value = items
        _playContext.value = playContext
        consecutiveFailures = 0
        playIndex(startIndex.coerceIn(0, items.lastIndex))
    }

    override fun playAudio(
        songId: Long,
        url: String,
        title: String,
        artist: String,
        coverUrl: String,
        startPosition: Long,
        playContext: String?
    ) {
        _queue.value = listOf(QueueItem(songId, title, artist, coverUrl))
        _playContext.value = playContext
        consecutiveFailures = 0
        playIndex(0, startPosition, knownUrl = url)
    }

    override fun addToPlayNext(items: List<QueueItem>) {
        if (items.isEmpty()) return
        val current = _queue.value
        if (current.isEmpty()) {
            playQueue(items, 0, null)
            return
        }
        val insertAt = (_currentIndex.value + 1).coerceIn(0, current.size)
        _queue.value = current.take(insertAt) + items + current.drop(insertAt)
        refreshNeighbours()
    }

    override fun playNext() {
        nextIndex(fromUser = true)?.let { playIndex(it) }
    }

    override fun playPrevious() {
        val size = _queue.value.size
        if (size == 0) return
        val index = if (_playMode.value == PlayMode.SHUFFLE) {
            randomIndexExcept(_currentIndex.value, size)
        } else {
            (_currentIndex.value - 1 + size) % size
        }
        playIndex(index)
    }

    override fun skipToPrevious() {
        if (_currentPosition.value > RESTART_THRESHOLD_MS) seekTo(0) else playPrevious()
    }

    override fun playAtIndex(index: Int) {
        if (index in _queue.value.indices) playIndex(index)
    }

    override fun removeFromQueue(index: Int) {
        val items = _queue.value
        if (index !in items.indices) return
        val remaining = items.filterIndexed { i, _ -> i != index }
        _queue.value = remaining
        when {
            remaining.isEmpty() -> clearQueue()
            index < _currentIndex.value -> {
                _currentIndex.value -= 1
                refreshNeighbours()
            }
            index == _currentIndex.value -> playIndex(index.coerceAtMost(remaining.lastIndex))
            else -> refreshNeighbours()
        }
    }

    override fun moveInQueue(from: Int, to: Int) {
        val items = _queue.value.toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return
        val currentId = _currentQueueItem.value?.songId
        items.add(to, items.removeAt(from))
        _queue.value = items
        _currentIndex.value = items.indexOfFirst { it.songId == currentId }
        refreshNeighbours()
    }

    override fun clearQueue() {
        playJob?.cancel()
        finishReporting()
        engine.stop()
        _queue.value = emptyList()
        _currentIndex.value = -1
        _nowPlaying.value = null
        _playWhenReady.value = false
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        refreshNeighbours()
    }

    override fun toggleShuffle() {
        _playMode.value = if (_playMode.value == PlayMode.SHUFFLE) PlayMode.LIST_LOOP else PlayMode.SHUFFLE
    }

    override fun toggleRepeat() {
        _playMode.value = if (_playMode.value == PlayMode.SINGLE_LOOP) PlayMode.LIST_LOOP else PlayMode.SINGLE_LOOP
    }

    override fun rotatePlayMode() {
        _playMode.value = when (_playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
        }
    }

    override fun pause() {
        _playWhenReady.value = false
        engine.setPaused(true)
    }

    override fun resume() {
        if (_nowPlaying.value == null) return
        _playWhenReady.value = true
        engine.setPaused(false)
    }

    override fun togglePlayPause() {
        if (_playWhenReady.value) pause() else resume()
    }

    override fun seekTo(positionMs: Long) {
        _currentPosition.value = positionMs.coerceAtLeast(0L)
        engine.seekTo(positionMs)
    }

    override fun reloadCurrentTrack() {
        val index = _currentIndex.value
        if (index in _queue.value.indices) playIndex(index, _currentPosition.value)
    }

    override fun setSleepTimer(minutes: Int) = Unit

    override fun setPositionUpdateInterval(intervalMs: Long) = Unit

    override fun cancelPendingSkip(): Boolean = false

    override fun disableRoaming() = Unit

    override fun disableIntelligence() = Unit

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _volume.value = clamped
        engine.setVolume(clamped)
    }

    fun release() {
        playJob?.cancel()
        finishReporting()
        engine.release()
    }

    override fun onPositionChanged(positionMs: Long) {
        _currentPosition.value = positionMs
    }

    override fun onDurationChanged(durationMs: Long) {
        _duration.value = durationMs
    }

    // 以下回调来自 mpv 事件线程，涉及计时与计数的状态统一切回主线程处理
    override fun onPauseChanged(paused: Boolean) {
        scope.launch {
            _isPlaying.value = !paused && _nowPlaying.value != null
            if (paused) pauseClock() else startClock()
        }
    }

    override fun onFileLoaded() {
        scope.launch {
            consecutiveFailures = 0
            _isPlaying.value = _playWhenReady.value
            startClock()
        }
    }

    override fun onEnded(isError: Boolean) {
        scope.launch {
            if (isError) {
                AppLogger.w(TAG, "音频解码失败，跳到下一首")
                handleFailure("这首歌暂时无法播放")
            } else {
                nextIndex(fromUser = false)?.let { playIndex(it) } ?: run { _isPlaying.value = false }
            }
        }
    }

    private fun playIndex(index: Int, startMs: Long = 0L, knownUrl: String? = null) {
        val item = _queue.value.getOrNull(index) ?: return
        playJob?.cancel()
        finishReporting()
        _currentIndex.value = index
        _currentPosition.value = startMs
        _duration.value = 0L
        _playWhenReady.value = true
        _isPlaying.value = false
        _nowPlaying.value = NowPlaying(
            mediaId = item.songId.toString(),
            title = item.title,
            artist = item.artist,
            artworkUri = item.coverUrl.takeIf { it.isNotBlank() }
        )
        refreshNeighbours()
        playJob = scope.launch {
            val url = knownUrl ?: repository.getSongUrl(item.songId).first().getOrElse { error ->
                AppLogger.w(TAG, "取播放地址失败 songId=${item.songId}", error)
                handleFailure("《${item.title}》暂无版权或需要会员")
                return@launch
            }
            engine.load(url, startMs)
            beginReporting(item.songId)
        }
    }

    private fun handleFailure(message: String) {
        consecutiveFailures++
        _messages.tryEmit(message)
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES || _queue.value.size <= 1) {
            _messages.tryEmit("连续多首无法播放，已停止")
            consecutiveFailures = 0
            _playWhenReady.value = false
            _isPlaying.value = false
            engine.stop()
            return
        }
        nextIndex(fromUser = true)?.let { playIndex(it) }
    }

    // 单曲循环仅在自然播完时重播本曲，手动切歌照常前进
    private fun nextIndex(fromUser: Boolean): Int? {
        val size = _queue.value.size
        if (size == 0) return null
        val current = _currentIndex.value
        return when {
            _playMode.value == PlayMode.SINGLE_LOOP && !fromUser -> current.coerceAtLeast(0)
            _playMode.value == PlayMode.SHUFFLE -> randomIndexExcept(current, size)
            else -> (current + 1) % size
        }
    }

    private fun randomIndexExcept(current: Int, size: Int): Int {
        if (size <= 1) return 0
        var candidate = Random.nextInt(size - 1)
        if (candidate >= current) candidate++
        return candidate
    }

    private fun refreshNeighbours() {
        val items = _queue.value
        val index = _currentIndex.value
        val valid = index in items.indices
        _currentQueueItem.value = if (valid) items[index] else null
        _previousQueueItem.value = if (valid && items.size > 1) items[(index - 1 + items.size) % items.size] else null
        _nextQueueItem.value = if (valid && items.size > 1) items[(index + 1) % items.size] else null
    }

    private fun beginReporting(songId: Long) {
        reportingSongId = songId
        playedMs = 0L
        playingSince = null
        scope.launch {
            repository.reportStartPlay(songId).collect { result ->
                result.onFailure { AppLogger.w(TAG, "打卡上报 startplay 失败 songId=$songId", it) }
            }
        }
    }

    private fun startClock() {
        if (reportingSongId != null && playingSince == null) playingSince = System.currentTimeMillis()
    }

    private fun pauseClock() {
        val since = playingSince ?: return
        playedMs += System.currentTimeMillis() - since
        playingSince = null
    }

    private fun finishReporting() {
        val songId = reportingSongId ?: return
        pauseClock()
        val played = playedMs
        reportingSongId = null
        playedMs = 0L
        if (played < MIN_REPORT_PLAYED_MS) return
        val playedSeconds = played / 1000
        scope.launch {
            repository.reportPlayEnd(songId, playedSeconds).collect { result ->
                result.onFailure { AppLogger.w(TAG, "打卡上报 play 失败 songId=$songId", it) }
            }
        }
    }
}
