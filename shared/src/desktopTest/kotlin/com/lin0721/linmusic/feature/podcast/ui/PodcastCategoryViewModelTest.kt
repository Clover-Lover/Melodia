package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.domain.PodcastPage
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastCategoryViewModelTest {

    private val repository = FakePodcastRepository()
    private val controller = FakePlaybackController()
    private val progress = PodcastProgressPreferences(InMemoryPreferencesStore())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PodcastCategoryViewModel(repository, controller, progress, testResourceProvider)

    private fun page(hasMore: Boolean, vararg radios: PodcastRadio) =
        Result.success(PodcastPage(radios.toList(), hasMore))

    private fun PodcastCategoryViewModel.success() = uiState.value as PodcastCategoryUiState.Success

    @Test
    fun `加载分类热门电台与推荐节目`() {
        repository.categoryHotRadios = { _, _ -> page(true, testRadio(1), testRadio(2)) }
        repository.recommendPrograms = { Result.success(listOf(testProgram(9))) }
        val vm = viewModel()

        vm.load(3)

        val state = vm.success()
        assertEquals(3L, state.categoryId)
        assertEquals(listOf(1L, 2L), state.radios.map { it.id })
        assertTrue(state.hasMore)
        assertEquals(listOf(9L), state.programs.itemsOrEmpty().map { it.id })
        assertEquals(1, repository.count("recommendPrograms:3"))
    }

    @Test
    fun `同一分类重复进入不重拉`() {
        repository.categoryHotRadios = { _, _ -> page(false, testRadio(1)) }
        val vm = viewModel()

        vm.load(3)
        vm.load(3)

        assertEquals(1, repository.count("categoryHot:3:0"))
    }

    @Test
    fun `电台列表失败即整页失败`() {
        repository.categoryHotRadios = { _, _ -> failure() }
        val vm = viewModel()

        vm.load(3)

        assertTrue(vm.uiState.value is PodcastCategoryUiState.Error)
    }

    @Test
    fun `推荐节目失败只影响自己的区块`() {
        repository.categoryHotRadios = { _, _ -> page(false, testRadio(1)) }
        repository.recommendPrograms = { failure() }
        val vm = viewModel()

        vm.load(3)

        val state = vm.success()
        assertEquals(1, state.radios.size)
        assertTrue(state.programs is PodcastSection.Error)

        repository.recommendPrograms = { Result.success(listOf(testProgram(9))) }
        vm.retryPrograms()
        assertEquals(listOf(9L), vm.success().programs.itemsOrEmpty().map { it.id })
    }

    @Test
    fun `翻页按已加载数量作偏移，失败时停止翻页`() {
        repository.categoryHotRadios = { _, offset ->
            when (offset) {
                0 -> page(true, testRadio(1), testRadio(2))
                2 -> page(true, testRadio(3))
                else -> failure()
            }
        }
        val vm = viewModel()
        vm.load(3)

        vm.loadMore()
        assertEquals(listOf(1L, 2L, 3L), vm.success().radios.map { it.id })

        vm.loadMore()
        assertEquals(3, vm.success().radios.size)
        assertEquals(false, vm.success().hasMore)
    }

    @Test
    fun `快速切换分类时丢弃晚到的响应`() {
        val slow = CompletableDeferred<Result<PodcastPage<PodcastRadio>>>()
        repository.categoryHotRadios = { cate, _ -> if (cate == 1L) slow.await() else page(false, testRadio(2)) }
        val vm = viewModel()

        vm.load(1)
        vm.load(2)
        slow.complete(page(false, testRadio(1)))

        assertEquals(2L, vm.success().categoryId)
        assertEquals(listOf(2L), vm.success().radios.map { it.id })
    }

    @Test
    fun `播放分类节目时带上本地进度`() {
        repository.categoryHotRadios = { _, _ -> page(false, testRadio(1)) }
        repository.recommendPrograms = { Result.success(listOf(testProgram(1), testProgram(2))) }
        runBlocking { progress.upsert(testProgress(20, positionMs = 77_000)) }
        val vm = viewModel()
        vm.load(3)

        vm.playProgram(1)

        val call = controller.queueCalls.single()
        assertEquals(1, call.startIndex)
        assertEquals(77_000L, call.startPositionMs)
    }
}
