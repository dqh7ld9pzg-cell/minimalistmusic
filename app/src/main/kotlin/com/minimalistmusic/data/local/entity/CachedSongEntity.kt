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
 * 已缓存歌曲实体 (2025-11-16)
 *
 * 架构重构说明：
 * - 旧方案：依赖播放历史（最多200首）+ 收藏歌曲，通过过滤获取缓存列表
 * - 新方案：独立的缓存音乐表，直接记录所有已缓存的歌曲
 *
 * 优点：
 * - 解决200首限制：不再依赖播放历史上限
 * - 性能优化：直接查询，无需多表JOIN和过滤
 * - 记录元数据：URL、缓存时间、文件大小等
 * - 灵活管理：支持手动添加、批量清理等功能
 *
 * 使用场景：
 * - CachedMusicViewModel: 获取已缓存歌曲列表
 * - AudioCacheManager: 缓存完成后插入记录，删除缓存时移除记录
 * - CacheStateManager: 快速检查歌曲是否已缓存
 *
 * URL缓存优化 (2025-11-18):
 * - 所有获取到的播放URL都存入数据库，不仅限于完整缓存的
 * - is_fully_cached: 区分是否完整缓存音频文件
 * - url_expire_time: URL有效期（默认Long.MAX_VALUE表示永久有效）
 * - 优化接口请求：相同歌曲ID优先从数据库获取URL，减少API调用
 *
 * 性能优化:
 * - 添加索引: cachedAt - 加速按缓存时间排序查询
 * - 添加索引: urlExpireTime - 加速过期URL清理
 */
@Entity(
    tableName = "cached_songs",
    indices = [
        Index(value = ["cachedAt"]),        // 加速按缓存时间排序
        Index(value = ["urlExpireTime"]),   // 加速过期URL查询 (2025-11-18)
        Index(value = ["lastPlayedAt"]),    // 加速按最近播放时间排序 (2025-11-21)
        Index(value = ["isProtected"])      // 加速白名单查询 (2025-11-21)
    ],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE  // 歌曲删除时自动删除缓存记录
        )
    ]
)
data class CachedSongEntity(
    @PrimaryKey val songId: Long,                                    // 歌曲ID
    val url: String,                                                  // 播放URL（用于离线播放）
    val isFullyCached: Boolean = false,                              // 是否完整缓存音频文件 (2025-11-18)
    val cacheSize: Long = 0,                                         // 缓存文件大小（字节，仅isFullyCached=true时有效）
    val cachedAt: Long = System.currentTimeMillis(),                 // 缓存时间（URL获取时间）
    val urlExpireTime: Long = Long.MAX_VALUE,                        // URL过期时间（默认永久有效）(2025-11-18)
    val lastPlayedAt: Long = System.currentTimeMillis(),             // 最近播放时间（用于LRU清理）(2025-11-21)
    val isProtected: Boolean = false                                 // 是否加入白名单保护（不被自动清理）(2025-11-21)
)
