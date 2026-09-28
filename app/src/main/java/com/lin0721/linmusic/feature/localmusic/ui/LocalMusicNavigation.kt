package com.lin0721.linmusic.feature.localmusic.ui

// 本地音乐各页面的跳转出口，由 NavHost 统一构造，避免每个页面各自声明一长串回调
data class LocalMusicNavigation(
    val onBack: () -> Unit,
    val openSongs: () -> Unit,
    val openArtists: () -> Unit,
    val openAlbums: () -> Unit,
    val openFolders: () -> Unit,
    val openArtist: (name: String) -> Unit,
    val openAlbum: (key: String) -> Unit,
    val openFolder: (path: String) -> Unit,
    val openPlaylists: () -> Unit,
    val openPlaylist: (id: Long) -> Unit,
    val openSettings: () -> Unit,
    // 已匹配网易歌曲的菜单里跳转线上歌手/专辑
    val openOnlineArtist: (id: Long) -> Unit,
    val openOnlineAlbum: (id: Long) -> Unit,
    val openTagEditor: (uri: String) -> Unit,
    val onLoginScreenVisibilityChanged: (Boolean) -> Unit
)
