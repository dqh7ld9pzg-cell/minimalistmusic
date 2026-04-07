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

package com.minimalistmusic.domain.repository

import com.minimalistmusic.domain.model.Playlist
import com.minimalistmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow
/**
 * 本地音乐仓库接口
 *
 * 职责：
 * - 本地歌曲的增删改查
 * - 收藏歌曲管理
 * - 播放列表管理
 * - 播放历史记录管理
 *
 * 重构说明（2025-11-11）:
 * - 从 MusicRepository 中拆分出本地数据相关操作
 * - 遵循SRP（单一职责原则）：只处理本地数据，不涉及网络请求
 */
interface MusicLocalRepository {
    // ============ 本地歌曲操作 ============
    /**
     * 获取所有本地歌曲
     */
    fun getAllLocalSongs(): Flow<List<Song>>
    /**
     * 获取收藏的歌曲（我喜欢）
     */
    fun getFavoriteSongs(): Flow<List<Song>>
    /**
     * 获取收藏的歌曲（一次性，非Flow）- 2025-11-14
     * 用于CachedMusicViewModel加载已缓存歌曲列表
     */
    suspend fun getFavoriteSongsOnce(): List<Song>
    /**
     * 批量插入歌曲
     */
    suspend fun insertSongs(songs: List<Song>)
    /**
     * 更新歌曲信息
     */
    suspend fun updateSong(song: Song)
    /**
     * 切换歌曲的喜欢状态
     * @param song 要切换状态的歌曲
     */
    suspend fun toggleFavorite(song: Song)
    /**
     * 检查歌曲是否已收藏
     * @param songId 歌曲ID
     * @return true-已收藏, false-未收藏
     */
    suspend fun isFavorite(songId: Long): Boolean
    /**
     * 删除歌曲
     */
    suspend fun deleteSong(song: Song)

    // ============ 播放历史操作 ============
    /**
     * 获取播放历史
     */
    fun getPlayHistory(): Flow<List<Song>>
    /**
     * 分页获取播放历史
     * @param limit 每页数量
     * @param offset 偏移量
     */
    suspend fun getPlayHistoryPaged(limit: Int, offset: Int): List<Song>
    /**
     * 分页获取播放历史（包含播放时间）
     * 返回的Song对象的addedAt字段被设置为最近播放时间
     * @param limit 每页数量
     * @param offset 偏移量
     * @since 2025-11-19
     */
    suspend fun getPlayHistoryWithTimePaged(limit: Int, offset: Int): List<Song>
    /**
     * 添加到播放历史
     * @param song 播放的歌曲
     */
    suspend fun addToPlayHistory(song: Song)
    /**
     * 标记播放记录为聆听足迹
     * 当歌曲播放>=10秒时调用
     * @param songId 歌曲ID
     * @since 2025-11-20
     */
    suspend fun markAsListeningRecord(songId: Long)
    /**
     * 清空播放历史
     */
    suspend fun clearPlayHistory()
    /**
     * 获取播放历史总数（去重后的歌曲数量）
     * 用于显示"播放记录 xx首"
     * @since 2025-11-20
     */
    suspend fun getPlayHistoryCount(): Int
    /**
     * 持续监听播放历史总数变化（去重后的歌曲数量）
     * 用于UI显示"聆听足迹 (xx)"
     * @since 2025-11-20
     */
    fun observePlayHistoryCount(): Flow<Int>
    /**
     * 持续监听最新播放时间变化,
     * 用于实现Single Source of Truth
     * 用于触发列表刷新
     * @since 2025-11-20
     */
    fun observeLatestPlayedAt(): Flow<Long?>
    /**
     * 删除指定歌曲的播放历史记录
     * 用于长按删除播放记录功能
     * @param songId 歌曲ID
     * @since 2025-11-20
     */
    suspend fun deletePlayHistoryBySongId(songId: Long)
    // ============ 歌单收藏操作 (2025-11-16) ============
    /**
     * 获取所有收藏的歌单
     * 按收藏时间倒序排列
     */
    fun getAllFavoritePlaylists(): Flow<List<com.minimalistmusic.domain.model.RecommendPlaylist>>
    /**
     * 切换歌单的收藏状态
     * @param playlist 要切换状态的歌单
     */
    suspend fun toggleFavoritePlaylist(playlist: com.minimalistmusic.domain.model.RecommendPlaylist)
    /**
     * 检查歌单是否已收藏
     * @param playlistId 歌单ID
     * @return true-已收藏, false-未收藏
     */
    suspend fun isFavoritePlaylist(playlistId: Long): Boolean
    /**
     * 移除收藏的歌单
     * @param playlistId 歌单ID
     */
    suspend fun removeFavoritePlaylist(playlistId: Long)
    // ============ 已缓存音乐操作 (2025-11-16架构重构) ============
    /**
     * 获取所有已缓存的歌曲
     *
     * 架构说明：
     * - 从cached_songs表读取（独立的缓存音乐表）
     * - 不再依赖播放历史和收藏表
     * - 按缓存时间倒序排列
     *
     * @return 已缓存歌曲列表Flow
     */
    fun getAllCachedSongs(): Flow<List<Song>>
    /**
     * 获取所有已缓存的歌曲列表（按最近播放时间排序）
     *
     * 用于缓存音乐页面显示
     * 更新 (2025-11-21): 按lastPlayedAt排序，最近播放的在前面
     *
     * @since 2025-11-19
     * @return 已缓存歌曲列表Flow，Song.addedAt字段存储最近播放时间
     */
    fun getAllCachedSongsWithTime(): Flow<List<Song>>
    /**
     * 删除缓存记录
     *
     * 说明：
     * - 仅删除数据库中的缓存记录
     * - 音频文件的删除由AudioCacheManager负责
     *
     * @param songId 歌曲ID
     */
    suspend fun deleteCachedSong(songId: Long)
    /**
     * 更新缓存歌曲的最近播放时间
     *
     * 新增 (2025-11-21): LRU缓存管理
     * 在播放完整缓存的歌曲时调用
     *
     * @param songId 歌曲ID
     */
    suspend fun updateCachedSongLastPlayedAt(songId: Long)
    /**
     * 清理超过上限的缓存歌曲
     *
     * 新增 (2025-11-21): LRU缓存管理
     * 按最近播放时间排序，删除最久未播放的歌曲
     *
     * @param maxCount 最大缓存歌曲数
     * @return 清理的歌曲数量
     */
    suspend fun trimCachedSongs(maxCount: Int): Int
    /**
     * 清理超过上限的缓存歌曲（考虑白名单保护）
     *
     * 策略 (2025-11-25):
     * - 与CacheStateManager的清理策略保持一致
     * - 白名单歌曲不会被清理
     * - 只清理非白名单歌曲，按最近播放时间排序
     * - 计算方式：maxNonProtectedCount = maxCount - protectedCount
     *
     * @param maxCount 最大缓存数量
     * @return 清理的歌曲数量
     */
    suspend fun trimCachedSongsWithProtection(maxCount: Int): Int
    /**
     * 清空缓存歌曲记录
     */
    suspend fun clearCachedRecords()
    /**
     * 清除歌曲的播放URL（path字段）
     *
     * 用于删除缓存时清理失效的URL
     * 避免下次播放时使用已失效的403 URL
     *
     * @param songId 歌曲ID
     * @since 2025-11-22
     */
    suspend fun clearSongPath(songId: Long)

    // ============ 歌单操作 ============
    /**
     * 获取所有歌单列表
     */
    fun getAllPlaylists(): Flow<List<Playlist>>
    /**
     * 创建歌单列表
     * @param name 歌单名称
     * @param cover 封面URL（可选）
     * @return 新创建歌单的ID
     */
    suspend fun createPlaylist(name: String, cover: String? = null): Long
    /**
     * 删除歌单列表
     */
    suspend fun deletePlaylist(playlist: Playlist)
    /**
     * 添加歌曲到歌单列表
     * @param playlistId 歌单ID
     * @param songId 歌曲ID
     */
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)
    /**
     * 从歌单中移除歌曲
     * @param playlistId 歌单ID
     * @param songId 歌曲ID
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    /**
     * 获取歌单中的歌曲列表
     * @param playlistId 歌单ID
     */
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>
}
