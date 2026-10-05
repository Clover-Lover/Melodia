package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PodcastCategoryUiState {
    data object Loading : PodcastCategoryUiState

    data class Error(val message: String) : PodcastCategoryUiState

    data class Success(
        val categoryId: Long,
        val radios: List<PodcastRadio>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false,
        // 推荐节目独立加载，失败不影响电台列表
        val programs: PodcastSection<List<PodcastProgram>> = PodcastSection.Loading,
        val progress: Map<Long, PodcastProgressEntry> = emptyMap()
    ) : PodcastCategoryUiState
}

// 分类二级页 ViewModel：该分类的热门电台（可翻页）与推荐节目
class PodcastCategoryViewModel(
    private val podcastRepository: PodcastRepository,
    private val playbackController: PlaybackController,
    private val progressPreferences: PodcastProgressPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastCategoryUiState>(PodcastCategoryUiState.Loading)
    val uiState: StateFlow<PodcastCategoryUiState> = _uiState.asStateFlow()

    private var categoryId: Long? = null
    private var progress: Map<Long, PodcastProgressEntry> = emptyMap()

    init {
        viewModelScope.launch {
            progressPreferences.entries.collect { entries ->
                progress = entries.associateBy { it.songId }
                _uiState.update { state ->
                    if (state is PodcastCategoryUiState.Success) state.copy(progress = progress) else state
                }
            }
        }
    }

    // 同一分类重复进入不必重拉
    fun load(categoryId: Long) {
        if (this.categoryId == categoryId && _uiState.value is PodcastCategoryUiState.Success) return
        this.categoryId = categoryId
        reload(categoryId)
    }

    fun retry() {
        categoryId?.let { reload(it) }
    }

    fun retryPrograms() {
        val id = categoryId ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                if (state is PodcastCategoryUiState.Success) state.copy(programs = PodcastSection.Loading) else state
            }
            val programs = podcastRepository.getRecommendPrograms(id).awaitSection(resourceProvider)
            _uiState.update { state ->
                if (state is PodcastCategoryUiState.Success && state.categoryId == id) state.copy(programs = programs) else state
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value as? PodcastCategoryUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return

        _uiState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            val section = podcastRepository.getCategoryHotRadios(current.categoryId, offset = current.radios.size)
                .awaitSection(resourceProvider)
            _uiState.update { state ->
                if (state !is PodcastCategoryUiState.Success || state.categoryId != current.categoryId) return@update state
                when (section) {
                    is PodcastSection.Success -> state.copy(
                        radios = state.radios + section.data.items,
                        hasMore = section.data.hasMore && section.data.items.isNotEmpty(),
                        isLoadingMore = false
                    )
                    // 追加失败时停止翻页但保留已有内容
                    else -> state.copy(isLoadingMore = false, hasMore = false)
                }
            }
        }
    }

    fun playProgram(index: Int) {
        val success = _uiState.value as? PodcastCategoryUiState.Success ?: return
        playbackController.playPodcastPrograms(success.programs.itemsOrEmpty(), index, progress)
    }

    private fun reload(categoryId: Long) {
        _uiState.value = PodcastCategoryUiState.Loading
        viewModelScope.launch {
            // 电台列表是主数据，失败才算整页失败；节目失败只影响自己的区块
            val radiosDeferred = async { podcastRepository.getCategoryHotRadios(categoryId).awaitSection(resourceProvider) }
            val programsDeferred = async { podcastRepository.getRecommendPrograms(categoryId).awaitSection(resourceProvider) }

            val radios = radiosDeferred.await()
            val programs = programsDeferred.await()
            // 期间又切到别的分类，晚到的结果丢弃
            if (this@PodcastCategoryViewModel.categoryId != categoryId) return@launch

            _uiState.value = when (radios) {
                is PodcastSection.Success -> PodcastCategoryUiState.Success(
                    categoryId = categoryId,
                    radios = radios.data.items,
                    hasMore = radios.data.hasMore && radios.data.items.isNotEmpty(),
                    programs = programs,
                    progress = progress
                )
                is PodcastSection.Error -> PodcastCategoryUiState.Error(radios.message)
                PodcastSection.Loading -> PodcastCategoryUiState.Loading
            }
        }
    }
}
