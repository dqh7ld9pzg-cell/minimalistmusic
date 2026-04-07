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

package com.minimalistmusic.data.local

import androidx.room.*
import com.minimalistmusic.data.local.dao.*
import com.minimalistmusic.data.local.entity.*

/**
 * 音乐数据库
 *
 * 架构重构 (2025-12-02):
 * - Entity 定义移至 entity/ 包
 * - DAO 接口移至 dao/ 包
 * - Model 数据类移至 model/ 包
 * - 提升代码可维护性和模块化
 *
 * 版本历史:
 * - v1: 初始版本
 * - v2: 添加 songs.source 字段
 * - v3: 添加 cache 表
 * - v4: 添加其他优化
 * - v5 (2025-11-12): 性能优化 - 添加索引
 *   · songs: 添加索引 (isLocal, isFavorite, addedAt)
 *   · search_history: 添加索引 (searchedAt)
 *   · play_history: 优化索引为复合索引 (songId, playedAt)
 * - v6 (2025-11-16): 添加歌单收藏功能
 *   · 新增 favorite_playlists 表
 *   · 支持收藏在线推荐歌单
 * - v7 (2025-11-16): 架构重构 - 添加独立的缓存音乐表
 *   · 新增 cached_songs 表
 *   · 解决依赖播放历史和收藏表的架构问题
 *   · 性能优化：直接查询，无需多表过滤
 *   · 功能增强：记录缓存元数据（URL、大小、时间）
 * - v8 (2025-11-16): 给favorite_playlists表添加source字段
 * - v9 (2025-11-18): URL缓存优化 - 扩展cached_songs表
 *   · 添加 isFullyCached 字段：区分URL缓存和音频文件缓存
 *   · 添加 urlExpireTime 字段：支持URL有效期管理
 *   · 添加 urlExpireTime 索引：加速过期清理
 *   · 优化策略：所有播放URL都缓存，减少API调用
 * - v10 (2025-11-19): 架构优化 - 添加独立的收藏时间字段
 *   · songs 表添加 favoritedAt 字段：专门记录收藏时间
 *   · 职责分离：addedAt 记录入库时间，favoritedAt 记录收藏时间
 *   · 添加 favoritedAt 索引：加速收藏时间排序
 *   · 查询优化：收藏列表按 favoritedAt 排序
 * - v11 (2025-11-20): 播放历史优化 - 区分播放记录和聆听足迹
 *   · play_history 表添加 isListeningRecord 字段：标识是否计入聆听足迹
 *   · 播放记录：所有播放过的歌曲（立即入库，用于外键约束和缓存管理）
 *   · 聆听足迹：播放>=10秒的歌曲（显示在"聆听足迹"界面）
 *   · 添加 isListeningRecord 索引：加速聆听足迹查询
 *   · 限制200首：仅限制聆听足迹数量，播放记录由总表限制控制
 * - v12 (2025-11-21): 缓存管理优化 - 支持按歌曲数量管理缓存
 *   · cached_songs 表添加 lastPlayedAt 字段：记录最近播放时间
 *   · 添加 lastPlayedAt 索引：加速按播放时间排序
 *   · 支持LRU缓存策略：按最近播放时间删除旧缓存
 *   · 用户可设置最大缓存歌曲数（5-100首），超过上限自动清理
 * - v13 (2025-11-21): 缓存白名单功能 - 保护用户喜爱的歌曲不被清理
 *   · cached_songs 表添加 isProtected 字段：标识是否加入白名单
 *   · 添加 isProtected 索引：加速白名单查询
 *   · LRU清理和清空缓存时自动跳过白名单歌曲
 *   · 用户可批量管理白名单（添加/移除）
 *
 * 性能优化说明:
 * - 所有索引在运行时自动创建，无需迁移脚本
 * - Room会在MIGRATION中自动处理索引的创建和删除
 */
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        UserEntity::class,
        SearchHistoryEntity::class,
        PlayHistoryEntity::class,
        CacheEntity::class,
        FavoritePlaylistEntity::class,  // v6: 收藏歌单表
        CachedSongEntity::class          // v7: 已缓存歌曲表
    ],
    version = 13,  // 升级版本号：添加 isProtected 字段支持白名单 (2025-11-21)
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun userDao(): UserDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun cacheDao(): CacheDao
    abstract fun favoritePlaylistDao(): FavoritePlaylistDao  // v6: 收藏歌单DAO
    abstract fun cachedSongDao(): CachedSongDao              // v7: 已缓存歌曲DAO
}
