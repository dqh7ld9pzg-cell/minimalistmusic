/*
 * Copyright (C) 2025 JG.Y
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.minimalistmusic.util

import com.minimalistmusic.domain.model.RecommendPlaylist
object PlaylistCache {
    private val cache = mutableMapOf<Long, RecommendPlaylist>()
    fun put(playlist: RecommendPlaylist) {
        cache[playlist.id] = playlist
    }
    fun get(playlistId: Long): RecommendPlaylist? {
        return cache[playlistId]
    }
    // 可以选择不remove，这样同一个歌单再次进入时还可以使用缓存
    // 但是，如果歌单信息可能会变，可以在一定时间后移除，或者在使用后移除？
    // 由于推荐歌单列表每次启动应用都可能变化，所以可以长期缓存，直到应用进程结束。
}