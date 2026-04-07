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

package com.minimalistmusic.data.local.entity

import androidx.room.*

/**
 * 收藏歌单实体 (2025-11-16)
 *
 * 用于存储用户收藏的在线歌单
 * 与PlaylistEntity的区别：
 * - PlaylistEntity: 用户创建的本地歌单
 * - FavoritePlaylistEntity: 用户收藏的在线推荐歌单
 *
 * 性能优化:
 * - 添加索引: favoritedAt - 加速按收藏时间排序查询
 */
@Entity(
    tableName = "favorite_playlists",
    indices = [
        Index(value = ["favoritedAt"])  // 加速按收藏时间排序
    ]
)
data class FavoritePlaylistEntity(
    @PrimaryKey val playlistId: Long,    // 网易云音乐歌单ID
    val name: String,                     // 歌单名称
    val cover: String?,                   // 封面URL
    val description: String?,             // 歌单描述
    val songCount: Long = 0,              // 歌曲数量（从RecommendPlaylist的playCount字段获取）
    val source: String = "playlist",      // 数据源类型："playlist" 或 "artist"
    val favoritedAt: Long = System.currentTimeMillis()  // 收藏时间
)
