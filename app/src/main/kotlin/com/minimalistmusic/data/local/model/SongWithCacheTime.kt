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

package com.minimalistmusic.data.local.model

/**
 * 缓存歌曲数据（包含歌曲和缓存时间）
 *
 * 用于缓存音乐页面显示缓存时间
 * @since 2025-11-19
 */
data class SongWithCacheTime(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val path: String?,
    val albumArt: String?,
    val isLocal: Boolean,
    val isFavorite: Boolean,
    val addedAt: Long,
    val source: String?,
    val cachedAt: Long  // 缓存完成时间
)
