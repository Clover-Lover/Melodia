package com.lin0721.linmusic.feature.localmusic.data

import android.net.Uri
import com.lin0721.linmusic.core.localmusic.LocalMusicApi

class LocalMusicApiImpl(private val coverArtCache: LocalCoverArtCache) : LocalMusicApi {

    override suspend fun coverUriFor(sourceUri: Uri): Uri? = coverArtCache.coverUriFor(sourceUri)
}
