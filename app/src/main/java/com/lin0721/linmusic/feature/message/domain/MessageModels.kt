package com.lin0721.linmusic.feature.message.domain

data class MessageUser(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String
)

// 一页消息；nextCursor 由各接口自己的翻页语义决定，hasMore 已排除"空页仍称有更多"的情况
data class MessagePage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextCursor: Long
)

// 回复我的评论 Tab 的条目
data class CommentMessage(
    val commentId: Long,
    val user: MessageUser,
    val content: String,
    val time: Long,
    val repliedContent: String?,
    val resourceName: String?,
    val resourceCreator: String?
)

// @我 Tab 的条目；key 为翻页偏移生成的稳定标识
data class ForwardMessage(
    val key: Long,
    val user: MessageUser?,
    val content: String,
    val time: Long
)

// 通知 Tab 的条目，按通知内部 type 区分
sealed interface NoticeItem {
    val id: Long
    val time: Long

    // songId 来自 threadId，仅单曲评论可解析；歌名由 ViewModel 补查
    data class Comment(
        override val id: Long,
        override val time: Long,
        val user: MessageUser,
        val content: String,
        val isReply: Boolean,
        val songId: Long?
    ) : NoticeItem

    // 关注对象发布的动态，只保留文案摘要
    data class TrackPost(
        override val id: Long,
        override val time: Long,
        val user: MessageUser,
        val text: String
    ) : NoticeItem

    data class PlaylistUpdate(
        override val id: Long,
        override val time: Long,
        val user: MessageUser,
        val playlistName: String,
        val trackCount: Int
    ) : NoticeItem

    // 解析失败或未知类型
    data class Unsupported(
        override val id: Long,
        override val time: Long
    ) : NoticeItem
}
