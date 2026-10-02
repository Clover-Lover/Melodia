package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "UserArtistRepositoryImpl"

// 翻页上限，防止服务端异常时无限请求
private const val MAX_SUBLIST_PAGES = 50

class UserArtistRepositoryImpl(
    private val apiService: UserArtistApi
) : UserArtistRepository {

    override fun getFavoriteArtists(): Flow<Result<List<ArtistInfo>>> = flow {
        var artists = emptyList<ArtistInfo>()

        // 尝试获取已关注歌手（实际返回: {"data":[...], "hasMore":true, "code":200}），分页取全
        val collected = LinkedHashMap<Long, ArtistInfo>()
        try {
            var offset = 0
            for (page in 0 until MAX_SUBLIST_PAGES) {
                val response = apiService.getArtistSublist(
                    ArtistSublistRequest(limit = ARTIST_SUBLIST_PAGE_SIZE, offset = offset)
                )
                if (response.code != 200 || response.data.isEmpty()) break
                val before = collected.size
                response.data.forEach { dto ->
                    collected.getOrPut(dto.id) {
                        ArtistInfo(
                            id = dto.id,
                            name = dto.name,
                            avatarUrl = dto.img1v1Url.takeIf { it.isNotBlank() } ?: dto.picUrl
                        )
                    }
                }
                offset += response.data.size
                val hasMore = response.hasMore ?: (response.data.size >= ARTIST_SUBLIST_PAGE_SIZE)
                // 无新增条目说明服务端忽略了 offset，避免死循环
                if (!hasMore || collected.size == before) break
            }
        } catch (e: Exception) {
            // 翻页中途失败时保留已取到的部分，仅在一条都没有时才进入备用流程
            AppLogger.w(TAG, "已关注歌手接口失败，已取到 ${collected.size} 条", e)
        }
        artists = collected.values.toList()


        if (artists.isNotEmpty()) {
            emit(Result.success(artists))
            return@flow
        }

        // 备用：热门歌手榜单
        // 注意：emit 必须放在 try/catch 之外——.first() 等短路收集算子会在拿到首个值后
        // 向上抛内部取消信号，若 emit 处在 try 块内会被这里的 catch(Exception) 误捕获，
        // 导致再次 emit 时触发 "Flow exception transparency violated" 崩溃
        val fallbackResult = try {
            val response = apiService.getTopArtists()
            if (response.isSuccess && response.artists.isNotEmpty()) {
                artists = response.artists.map { dto ->
                    ArtistInfo(
                        id = dto.id,
                        name = dto.name,
                        avatarUrl = dto.img1v1Url.takeIf { it.isNotBlank() } ?: dto.picUrl
                    )
                }
                Result.success(artists)
            } else {
                Result.failure(AppError.BizError(response.code, null))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "热门歌手兜底也失败", e)
            Result.failure(e)
        }
        emit(fallbackResult)
    }
}
