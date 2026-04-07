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

package com.minimalistmusic.data.mapper

import com.minimalistmusic.data.local.entity.PlaylistEntity
import com.minimalistmusic.data.local.entity.SongEntity
import com.minimalistmusic.domain.model.Playlist
import com.minimalistmusic.domain.model.Song

/**
 * 实体映射器
 *
 * 负责在不同层之间转换数据模型：
 * - Entity (数据库实体) ↔ Model (业务模型)
 */
// ============ Song 映射 ============
fun SongEntity.toModel() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    path = path,
    albumArt = albumArt,
    isLocal = isLocal,
    isFavorite = isFavorite,
    addedAt = addedAt,
    favoritedAt = favoritedAt  // 2025-11-19: 添加收藏时间映射
)
fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    path = path,
    albumArt = albumArt,
    isLocal = isLocal,
    isFavorite = isFavorite,
    addedAt = addedAt,
    favoritedAt = favoritedAt  // 2025-11-19: 添加收藏时间映射
)

// ============ Playlist 映射 ============
fun PlaylistEntity.toModel() = Playlist(
    id = id,
    name = name,
    cover = cover,
    songCount = songCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDefault = isDefault
)
fun Playlist.toEntity() = PlaylistEntity(
    id = id,
    name = name,
    cover = cover,
    songCount = songCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDefault = isDefault
)
