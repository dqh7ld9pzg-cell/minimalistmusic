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

package com.minimalistmusic.data.local.dao

import androidx.room.*
import com.minimalistmusic.data.local.entity.CachedSongEntity
import com.minimalistmusic.data.local.entity.SongEntity
import com.minimalistmusic.data.local.model.SongWithCacheAndPlayTime
import com.minimalistmusic.data.local.model.SongWithCacheTime
import kotlinx.coroutines.flow.Flow

/**
 * 已缓存歌曲数据访问对象
 *
 * 架构重构：
 * - 独立管理音频缓存，不再依赖播放历史和收藏表
 * - URL过期逻辑已废弃，但保留urlExpireTime字段以便将来使用
 *
 * 性能优化：
 * - getAllFullyCachedSongs: 利用索引 (isFullyCached, cachedAt) 加速排序
 * - isFullyCached: 主键查询，O(1)复杂度
 * - JOIN查询: 获取完整的歌曲信息（包括标题、艺术家等）
 */
@Dao
interface CachedSongDao {
    /**
     * 获取所有完整缓存的歌曲（带完整信息）
     * 按缓存时间倒序排列（最近缓存的在最前面）
     *
     * 修改 (2025-11-18): 只返回isFullyCached=true的歌曲
     * 性能: O(n log n) - 有索引支持的JOIN和排序
     */
    @Query("SELECT songs.* FROM songs INNER JOIN cached_songs ON songs.id = cached_songs.songId WHERE cached_songs.isFullyCached = 1 ORDER BY cached_songs.cachedAt DESC")
    fun getAllCachedSongs(): Flow<List<SongEntity>>

    /**
     * 获取所有完整缓存的歌曲（包含缓存时间）
     * 按缓存时间倒序排列（最近缓存的在最前面）
     *
     * 用于缓存音乐页面显示缓存时间
     * @since 2025-11-19
     */
    @Query("""
        SELECT songs.id, songs.title, songs.artist, songs.album, songs.duration,
               songs.path, songs.albumArt, songs.isLocal, songs.isFavorite, songs.addedAt, songs.source,
               cached_songs.cachedAt
        FROM songs
        INNER JOIN cached_songs ON songs.id = cached_songs.songId
        WHERE cached_songs.isFullyCached = 1
        ORDER BY cached_songs.cachedAt DESC
    """)
    fun getAllCachedSongsWithTime(): Flow<List<SongWithCacheTime>>

    /**
     * 获取所有完整缓存歌曲的ID列表
     * 用于快速检查缓存状态
     *
     * 修改 (2025-11-18): 只返回isFullyCached=true的歌曲ID
     * 性能: O(n) - 快速列表查询
     */
    @Query("SELECT songId FROM cached_songs WHERE isFullyCached = 1")
    suspend fun getAllCachedSongIds(): List<Long>

    /**
     * 持续监听所有完整缓存歌曲的ID列表
     *
     * 新增 (2025-11-19): 缓存状态管理优化
     * 用于CacheStateManager实现Single Source of Truth
     * 数据库任何变化都会自动触发Flow更新
     *
     * 性能: O(n) - 快速列表查询，Room自动优化
     */
    @Query("SELECT songId FROM cached_songs WHERE isFullyCached = 1")
    fun observeAllCachedSongIds(): Flow<List<Long>>

    /**
     * 检查歌曲是否完整缓存
     *
     * 修改 (2025-11-18): 检查isFullyCached标志
     * 性能: O(1) - 主键查询
     */
    @Query("SELECT COUNT(*) > 0 FROM cached_songs WHERE songId = :songId AND isFullyCached = 1")
    suspend fun isCached(songId: Long): Boolean

    /**
     * 获取指定歌曲的缓存信息
     */
    @Query("SELECT * FROM cached_songs WHERE songId = :songId")
    suspend fun getCachedSong(songId: Long): CachedSongEntity?

    /**
     * 获取所有缓存记录（同步版本，用于启动时全量同步）(2025-11-24)
     *
     * 说明：
     * - 返回所有 cached_songs 记录（包括未完整缓存的）
     * - 用于启动时同步数据库与文件系统
     * - suspend 函数，但返回 List 而不是 Flow（适合一次性操作）
     */
    @Query("SELECT * FROM cached_songs")
    suspend fun getAllCachedSongsSync(): List<CachedSongEntity>

    /**
     * 重置完整缓存标志（2025-11-24）
     *
     * 使用场景：
     * - 启动时同步发现文件已被删除
     * - 将 isFullyCached 重置为 0，cacheSize 重置为 0
     * - 但保留 URL 缓存记录，下次播放时可继续使用
     */
    @Query("UPDATE cached_songs SET isFullyCached = 0, cacheSize = 0 WHERE songId = :songId")
    suspend fun resetFullyCachedFlag(songId: Long)


    /**
     * 插入或更新缓存记录
     * 在歌曲缓存完成时调用
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSong(cachedSong: CachedSongEntity)

    /**
     * 批量插入或更新缓存记录
     *
     * 新增 (2025-11-18): 批量URL缓存插入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedSongs(cachedSongs: List<CachedSongEntity>)


    /**
     * 更新完整缓存状态
     *
     * 新增 (2025-11-18): 音频文件缓存完成时调用
     */
    @Query("UPDATE cached_songs SET isFullyCached = 1, cacheSize = :cacheSize WHERE songId = :songId")
    suspend fun markAsFullyCached(songId: Long, cacheSize: Long)

    /**
     * 更新最近播放时间
     *
     * 新增 (2025-11-21): 播放完整缓存的歌曲时调用
     * 用于LRU缓存策略，记录歌曲最后播放时间
     *
     * @param songId 歌曲ID
     * @param lastPlayedAt 最近播放时间，默认为当前时间
     */
    @Query("UPDATE cached_songs SET lastPlayedAt = :lastPlayedAt WHERE songId = :songId AND isFullyCached = 1")
    suspend fun updateLastPlayedAt(songId: Long, lastPlayedAt: Long = System.currentTimeMillis())

    /**
     * 获取所有完整缓存的歌曲（按最近播放时间排序）
     *
     * 新增 (2025-11-21): 用于缓存列表页面显示
     * 按最近播放时间倒序排列（最近播放的在最前面）
     *
     * @return 包含歌曲信息、缓存时间和最近播放时间的列表
     */
    @Query("""
        SELECT songs.id, songs.title, songs.artist, songs.album, songs.duration,
               songs.path, songs.albumArt, songs.isLocal, songs.isFavorite, songs.addedAt, songs.source,
               cached_songs.cachedAt, cached_songs.lastPlayedAt
        FROM songs
        INNER JOIN cached_songs ON songs.id = cached_songs.songId
        WHERE cached_songs.isFullyCached = 1
        ORDER BY cached_songs.lastPlayedAt DESC
    """)
    fun getAllCachedSongsOrderedByPlayTime(): Flow<List<SongWithCacheAndPlayTime>>

    /**
     * 删除超过上限的旧缓存（按lastPlayedAt排序）
     *
     * 新增 (2025-11-21): LRU清理策略
     * 保留最近播放的maxCount首歌曲，删除其余的旧缓存
     *
     * @param maxCount 最大保留数量
     */
    @Query("""
        DELETE FROM cached_songs
        WHERE isFullyCached = 1
        AND songId NOT IN (
            SELECT songId FROM cached_songs
            WHERE isFullyCached = 1
            ORDER BY lastPlayedAt DESC
            LIMIT :maxCount
        )
    """)
    suspend fun trimCachedSongsByPlayTime(maxCount: Int)

    /**
     * 获取需要删除的缓存歌曲URL列表（用于删除文件）
     *
     * 新增 (2025-11-21): 在trimCachedSongsByPlayTime之前调用
     * 用于删除ExoPlayer缓存文件
     *
     * @param maxCount 最大保留数量
     * @return 需要删除的歌曲URL列表
     */
    @Query("""
        SELECT url FROM cached_songs
        WHERE isFullyCached = 1
        AND songId NOT IN (
            SELECT songId FROM cached_songs
            WHERE isFullyCached = 1
            ORDER BY lastPlayedAt DESC
            LIMIT :maxCount
        )
    """)
    suspend fun getUrlsToDelete(maxCount: Int): List<String>

    /**
     * 删除缓存记录
     * 在用户删除缓存时调用
     */
    @Query("DELETE FROM cached_songs WHERE songId = :songId")
    suspend fun deleteCachedSong(songId: Long)

    /**
     * 根据cacheKey删除缓存记录
     * 在用户删除缓存时调用
     */
    @Query("DELETE FROM cached_songs WHERE url = :cacheKey")
    suspend fun deleteCachedSongByCacheKey(cacheKey: String)

    /**
     * 根据URL查询缓存记录
     *
     * 新增 (2025-11-22): 用于白名单检查
     * ExoPlayer使用URL作为缓存key，需要通过URL反查songId来判断是否在白名单中
     *
     * @param url 缓存URL
     * @return 缓存记录，如果不存在返回null
     */
    @Query("SELECT * FROM cached_songs WHERE url = :url LIMIT 1")
    suspend fun getCachedSongByUrl(url: String): CachedSongEntity?

    /**
     * 清空所有缓存记录
     * 在清空缓存时调用
     */
    @Query("DELETE FROM cached_songs")
    suspend fun clearAllCachedSongs()


    /**
     * 获取完整缓存歌曲总数
     *
     * 修改 (2025-11-18): 只统计isFullyCached=true的歌曲
     */
    @Query("SELECT COUNT(*) FROM cached_songs WHERE isFullyCached = 1")
    suspend fun getCachedSongCount(): Int

    /**
     * 获取完整缓存歌曲总大小（字节）
     *
     * 修改 (2025-11-18): 只统计isFullyCached=true的歌曲大小
     */
    @Query("SELECT SUM(cacheSize) FROM cached_songs WHERE isFullyCached = 1")
    suspend fun getTotalCacheSize(): Long?

    /**
     * 持续监听完整缓存歌曲总大小（字节）
     *
     * 新增 (2025-11-20): 实现缓存大小的 Single Source of Truth
     * - 数据库变化时自动触发更新
     * - 用于 ProfileScreen 和 CachedMusicScreen 共享缓存大小状态
     */
    @Query("SELECT SUM(cacheSize) FROM cached_songs WHERE isFullyCached = 1")
    fun observeTotalCacheSize(): Flow<Long?>

    // ============ 白名单功能 (2025-11-22) ============

    /**
     * 获取所有白名单（受保护）歌曲列表
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 用于CachedMusicViewModel展示白名单状态
     *
     * @return 白名单歌曲实体列表
     */
    @Query("SELECT * FROM cached_songs WHERE isProtected = 1 AND isFullyCached = 1")
    suspend fun getProtectedSongs(): List<CachedSongEntity>

    /**
     * 获取所有白名单（受保护）歌曲的ID列表
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 用于快速检查歌曲是否在白名单中
     *
     * @return 白名单歌曲ID列表
     */
    @Query("SELECT songId FROM cached_songs WHERE isProtected = 1 AND isFullyCached = 1")
    suspend fun getProtectedSongIds(): List<Long>

    /**
     * 持续监听白名单歌曲ID列表的变化
     *
     * 修复 (2025-11-22): 白名单图标刷新问题
     * - 用于CachedMusicViewModel实时监听isProtected字段变化
     * - 当用户点击爱心收藏/取消收藏时，立即刷新UI白名单图标
     *
     * @return Flow<List<Long>> 白名单歌曲ID列表Flow
     */
    @Query("SELECT songId FROM cached_songs WHERE isProtected = 1 AND isFullyCached = 1")
    fun observeProtectedSongIds(): Flow<List<Long>>

    /**
     * 统计白名单歌曲数量
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 用于清空缓存对话框显示"也一起清除 X 首白名单歌曲？"
     *
     * @return 白名单歌曲数量
     */
    @Query("SELECT COUNT(*) FROM cached_songs WHERE isProtected = 1 AND isFullyCached = 1")
    suspend fun getProtectedCount(): Int

    /**
     * 更新单首歌曲的白名单状态
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 用于用户手动添加/移除白名单
     *
     * @param songId 歌曲ID
     * @param isProtected true=加入白名单，false=移出白名单
     */
    @Query("UPDATE cached_songs SET isProtected = :isProtected WHERE songId = :songId AND isFullyCached = 1")
    suspend fun updateProtectedStatus(songId: Long, isProtected: Boolean)

    /**
     * 批量更新白名单状态
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 用于多选模式批量添加到白名单
     *
     * @param songIds 歌曲ID列表
     * @param isProtected true=加入白名单，false=移出白名单
     */
    @Query("UPDATE cached_songs SET isProtected = :isProtected WHERE songId IN (:songIds) AND isFullyCached = 1")
    suspend fun batchUpdateProtectedStatus(songIds: List<Long>, isProtected: Boolean)

    /**
     * 自动将收藏的歌曲加入白名单
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 功能：如果歌曲在"我喜欢"中，自动加入缓存白名单
     *
     * SQL逻辑：
     * - 更新所有 isFavorite=1 且已完整缓存的歌曲
     * - 将它们的 isProtected 设置为 1
     */
    @Query("UPDATE cached_songs SET isProtected = 1 WHERE songId IN (SELECT id FROM songs WHERE isFavorite = 1) AND isFullyCached = 1")
    suspend fun autoProtectFavoriteSongs()

    /**
     * 清空所有非白名单缓存歌曲
     *
     * 新增 (2025-11-22): 缓存白名单功能
     * 用于"清空缓存"对话框中用户取消勾选"也清除白名单歌曲"时调用
     *
     * @return 被删除的歌曲数量
     */
    @Query("DELETE FROM cached_songs WHERE isFullyCached = 1 AND isProtected = 0")
    suspend fun clearNonProtectedCachedSongs()

    /**
     * 获取前 N 首歌曲的 ID 列表（按最近播放时间排序）
     *
     * 新增 (2025-11-23): 上限内缓存保护功能
     *
     * 用途：
     * - 为 ObservableLruCacheEvictor 的 isProtected 函数提供数据
     * - 保护所有在上限内的歌曲（不仅是白名单）
     *
     * 工作机制：
     * 1. 只统计完整缓存的歌曲（isFullyCached = 1）
     * 2. 按 lastPlayedAt 降序排序（最近播放的在前）
     * 3. 限制返回前 maxCount 首歌曲的 ID
     *
     * 使用示例：
     * - maxCachedSongs = 100
     * - 返回最近播放的 100 首歌曲的 ID（包括白名单和非白名单）
     * - ExoPlayer 清理缓存时，这 100 首都受保护
     *
     * @param maxCount 最大保留数量
     * @return 前 N 首歌曲的 ID 列表
     */
    @Query("""
        SELECT songId FROM cached_songs
        WHERE isFullyCached = 1
        ORDER BY lastPlayedAt DESC
        LIMIT :maxCount
    """)
    suspend fun getTopNSongIdsByPlayTime(maxCount: Int): List<Long>

    /**
     * 获取前 N 首非白名单歌曲的 ID（按最近播放时间排序）
     *
     * 新增 (优化 2025-11-23): LRU 缓存保护逻辑优化
     * - 只统计非白名单歌曲（isProtected = 0），排除白名单
     * - querySize 应该是缓存总数减去白名单数量
     * - 这样实现真正的"白名单 + TOP N 非白名单"组合保护策略
     *
     * @param querySize 最大非白名单歌曲数量
     */
    @Query("""
        SELECT songId FROM cached_songs
        WHERE isFullyCached = 1 AND isProtected = 0
        ORDER BY lastPlayedAt DESC
        LIMIT :querySize
    """)
    suspend fun getTopNSongIdsExcludingProtected(querySize: Int): List<Long>

    /**
     * 获取需要删除的非白名单缓存歌曲URL列表（排除白名单）
     *
     * 新增 (2025-11-22): LRU清理策略优化
     * 在自动清理缓存时，跳过白名单歌曲，只清理非白名单的旧歌曲
     *
     * @param maxCount 最大保留数量（不包括白名单）
     * @return 需要删除的歌曲URL列表
     */
    @Query("""
        SELECT url FROM cached_songs
        WHERE isFullyCached = 1
        AND isProtected = 0
        AND songId NOT IN (
            SELECT songId FROM cached_songs
            WHERE isFullyCached = 1
            AND isProtected = 0
            ORDER BY lastPlayedAt DESC
            LIMIT :maxCount
        )
    """)
    suspend fun getUrlsToDeleteExcludingProtected(maxCount: Int): List<String>

    /**
     * 删除超过上限的旧缓存（排除白名单）
     *
     * 修改 (2025-11-22): LRU清理策略优化
     * 原逻辑：删除超过maxCount的所有旧缓存
     * 新逻辑：跳过白名单歌曲，只删除非白名单的旧缓存
     *
     * 使用场景：
     * - 用户设置最大缓存100首，白名单有20首
     * - 实际保留：白名单20首 + 最近播放80首 = 100首
     * - 自动清理时只删除非白名单且不在最近80首的歌曲
     *
     * @param maxCount 最大保留数量（不包括白名单）
     */
    @Query("""
        DELETE FROM cached_songs
        WHERE isFullyCached = 1
        AND isProtected = 0
        AND songId NOT IN (
            SELECT songId FROM cached_songs
            WHERE isFullyCached = 1
            AND isProtected = 0
            ORDER BY lastPlayedAt DESC
            LIMIT :maxCount
        )
    """)
    suspend fun trimCachedSongsByPlayTimeExcludingProtected(maxCount: Int)

    /**
     * 获取所有非白名单歌曲的URL（按最久未播放排序）
     *
     * 用途 (2025-11-25):
     * - 清理策略优化：优先清理非白名单歌曲
     * - 按lastPlayedAt ASC排序，最久未播放的在前面
     *
     * @return 非白名单歌曲URL列表（按最久未播放排序）
     */
    @Query("""
        SELECT url FROM cached_songs
        WHERE isFullyCached = 1
        AND isProtected = 0
        ORDER BY lastPlayedAt ASC
    """)
    suspend fun getAllNonProtectedUrlsSortedByLRU(): List<String>

    /**
     * 获取所有白名单歌曲的URL（按最久未播放排序）
     *
     * 用途 (2025-11-25):
     * - 清理策略优化：非白名单不够时，才清理白名单
     * - 按lastPlayedAt ASC排序，最久未播放的在前面
     *
     * @return 白名单歌曲URL列表（按最久未播放排序）
     */
    @Query("""
        SELECT url FROM cached_songs
        WHERE isFullyCached = 1
        AND isProtected = 1
        ORDER BY lastPlayedAt ASC
    """)
    suspend fun getAllProtectedUrlsSortedByLRU(): List<String>
}
