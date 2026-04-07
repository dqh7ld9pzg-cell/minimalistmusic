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
 * 歌曲实体
 *
 * 性能优化说明 (2025-11-12):
 * - 添加索引: isLocal, isFavorite, addedAt - 加速常用查询
 * - 这些字段在 WHERE 和 ORDER BY 子句中频繁使用
 *
 * 字段说明 (2025-11-19):
 * - addedAt: 记录添加到数据库的时间（本地歌曲扫描时间、在线歌曲首次入库时间）
 * - favoritedAt: 收藏时间（仅当 isFavorite=true 时有效）
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["isLocal"]),      // 加速本地歌曲筛选查询
        Index(value = ["isFavorite"]),   // 加速收藏歌曲查询
        Index(value = ["addedAt"]),      // 加速时间排序查询
        Index(value = ["favoritedAt"])   // 加速收藏时间排序查询（2025-11-19）
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val path: String?,
    val albumArt: String?,
    val isLocal: Boolean = true,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val favoritedAt: Long? = null,  // 收藏时间（2025-11-19新增）
    val source: String? = null
)
