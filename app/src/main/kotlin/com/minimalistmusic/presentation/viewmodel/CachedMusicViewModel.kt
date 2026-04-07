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

package com.minimalistmusic.presentation.viewmodel

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.cache.KeyValueCacheManager
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.domain.cache.CacheStateManager
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 已缓存音乐ViewModel
 *
 * 职责：
 * - 管理已缓存的在线歌曲列表
 * - 提供删除单个歌曲缓存的功能
 * - 更新缓存状态和缓存大小
 *
 * 架构设计（2025-11-14）：
 * - 继承BaseViewModel，获得缓存状态管理能力
 * - 使用AudioCacheManager查询和删除缓存
 * - 使用LocalMusicRepository获取歌曲信息（从播放历史或收藏中匹配）
 *
 * 数据流：
 * 1. 从AudioCacheManager获取所有已缓存的URL
 * 2. 通过URL从数据库匹配对应的Song对象
 * 3. 显示在列表中
 * 4. 删除时调用AudioCacheManager.removeCachedSong()
 * 5. 刷新列表和缓存状态
 */
@HiltViewModel
class CachedMusicViewModel @OptIn(UnstableApi::class) @Inject constructor(
    application: Application, // 修复 (2025-11-14): 注入Application传递给BaseViewModel
    private val audioCacheManager: AudioCacheManager,
    private val cacheManager: KeyValueCacheManager,
    private val cachedSongDao: CachedSongDao, // 新增 (2025-11-19): 直接从数据库获取缓存URL
    private val musicLocalRepository: MusicLocalRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    cacheStateManager: CacheStateManager,
) : BaseViewModel(application, cacheStateManager) { // 修复 (2025-11-14): 传递application和cacheStateManager给BaseViewModel
    // ========== 状态管理 ==========
    /**
     * 已缓存的歌曲列表
     *
     * 包含所有已缓存音频的在线歌曲（不包括本地歌曲）
     */
    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs.asStateFlow()
    /**
     * 白名单保护状态映射 (2025-11-22)
     *
     * Key: 歌曲ID
     * Value: 是否受保护
     */
    private val _protectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val protectedSongIds: StateFlow<Set<Long>> = _protectedSongIds.asStateFlow()
    /**
     * 已缓存歌曲数量（架构优化 2025-11-15）
     *
     * 重构说明：
     * - 不再维护独立的 _cachedSongCount 状态（避免状态不同步）
     * - 直接使用 CacheStateManager.cachedSongCount（单一数据源）
     * - 当缓存完成时，CacheStateManager 自动更新此值
     *
     * 优化 (2025-11-23): 使用 Eagerly 确保数据立即可用，避免UI闪烁
     */
    val cachedSongCount: StateFlow<Int> = cachedSongs.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * 最大缓存歌曲数量（架构优化 2025-12-02）
     *
     * 单一数据源原则：
     * - 直接暴露 DataStore 的 StateFlow
     * - 移除了冗余的 _maxCachedSongs 状态
     * - DataStore 会自动响应用户在 Profile 页面的修改
     * - 无需手动调用 refreshMaxCachedSongs()
     */
    val maxCachedSongs: StateFlow<Int> = userPreferencesDataStore.maxCachedSongs
    /**
     * 加载状态
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    // 错误消息统一使用BaseViewModel的errorMessage (SharedFlow)
    // 移除重复定义 (2025-11-15)
    /**
     * 当前缓存总大小（格式化字符串）
     *
     * 重构 (2025-11-20): 使用 CacheStateManager.cacheSizeFormatted 作为 Single Source of Truth
     * - 移除独立的 _cacheSize 状态
     * - 数据库变化时自动更新
     * - 与 ProfileViewModel.cacheSize 共享同一数据源
     */
    val cacheSize: StateFlow<String> = cacheStateManager.cacheSizeFormatted
    // ========== 初始化 ==========
    init {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "CachedMusicViewModel init obj: $this"
        )
        // 架构优化 (2025-12-02): 移除手动同步 maxCachedSongs
        // DataStore 的 StateFlow 会自动提供最新值，无需手动同步
        // updateCacheSize() 已移除 (2025-11-20): 缓存大小由 CacheStateManager 自动管理
        // 架构优化 (2025-11-19): 持续监听cached_songs表的变化
        // 数据库是Single Source of Truth,表变化时自动更新UI
        // 更新 (2025-11-19): 使用getAllCachedSongsWithTime()获取缓存时间
        // 修复 (2025-11-24): 简化逻辑，直接使用数据库返回的列表
        viewModelScope.launch {
            musicLocalRepository.getAllCachedSongsWithTime().collect { newSongsList ->
                _cachedSongs.value = newSongsList
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachedMusicViewModel 缓存列表自动更新: ${newSongsList.size} 首歌曲"
                )
            }
        }
        // 白名单状态监听 (2025-11-22)
        // 修复 (2025-11-22): 直接监听 cached_songs.isProtected 字段变化
        // 原问题：
        // - 之前监听 getFavoriteSongs()，只能监听 songs.isFavorite 变化
        // - 但白名单状态存储在 cached_songs.isProtected 字段
        // - 导致点击爱心收藏时白名单图标不刷新
        // 解决方案：
        // - 使用 observeProtectedSongIds() 直接监听 cached_songs 表
        // - 当 isProtected 字段变化时，立即触发 Flow 更新
        viewModelScope.launch {
            cachedSongDao.observeProtectedSongIds().collect { protectedIds ->
                // 白名单变化时，直接更新 StateFlow
                _protectedSongIds.value = protectedIds.toSet()
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachedMusicViewModel 白名单状态变化，刷新UI: ${protectedIds.size} 首受保护歌曲"
                )
            }
        }
    }
    // ========== 公开方法 ==========

    /**
     * refreshMaxCachedSongs() 方法已移除 (2025-12-02架构优化)
     *
     * 原因：
     * - maxCachedSongs 现在直接使用 DataStore 的 StateFlow
     * - DataStore 会自动响应用户在 Profile 页面的修改
     * - 所有订阅者会自动收到更新，无需手动刷新
     * - 实现了真正的响应式架构
     */

    // loadCachedSongs() 方法已移除 (2025-11-16架构重构)
    // 原因：init中已启动持续监听，数据库变化时自动更新UI，无需手动调用
    /**
     * 删除指定歌曲的缓存
     *
     * 执行流程（2025-11-19优化）：
     * 1. 从数据库获取歌曲的缓存记录（包含播放URL）
     * 2. 调用AudioCacheManager删除对应的缓存文件
     * 3. 删除数据库中的缓存记录
     * 4. 删除CacheManager中的URL缓存（如果存在）
     * 5. 从UI列表中移除该歌曲
     * 6. 更新缓存状态Map
     * 7. 更新缓存大小显示
     *
     * @param song 要删除缓存的歌曲
     */
    @OptIn(UnstableApi::class)
    fun deleteSongCache(song: Song) {
        viewModelScope.launch {
            try {
                // 1. 优先从数据库获取歌曲的缓存记录（包含URL）
                val cachedSongEntity = cachedSongDao.getCachedSong(song.id)
                if (cachedSongEntity != null) {
                    val url = cachedSongEntity.url
                    // 2. 删除AudioCache中的音频文件
                    val isDeleted = audioCacheManager.removeCachedSong(url)
                    // 3. 删除数据库中的缓存记录（无论音频文件是否成功删除）
                    musicLocalRepository.deleteCachedSong(song.id)
                    // 4. 清除Song表中的path字段，避免下次播放时使用失效的URL (2025-11-22)
                    musicLocalRepository.clearSongPath(song.id)
                    // 5. 删除KeyValueCacheManager中的URL缓存（如果存在）
                    val cacheKey = KeyValueCacheManager.getSongUrlKey(song.id.toString())
                    cacheManager.deleteCache(cacheKey)
                    // 6. 从UI列表中移除该歌曲
                    _cachedSongs.value = _cachedSongs.value.filter { it.id != song.id }
                    // 7. 缓存状态由数据库 Flow 自动更新（重构 2025-12-03）
                    // musicLocalRepository.deleteCachedSong() 已删除数据库记录
                    // → Room Flow 自动触发 → cacheStateMap 更新 → UI 响应
                    // 8. 缓存大小由 CacheStateManager 自动更新 (2025-11-20)
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "缓存删除成功: ${song.title}, 音频文件删除=${isDeleted}, 数据库记录已删除, Song.path已清除"
                    )
                } else {
                    // 数据库中没有缓存记录，可能是数据不一致，尝试清理
                    LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "数据库中未找到缓存记录: ${song.title}, songId=${song.id}")
                    // 尝试从KeyValueCacheManager获取URL并清理
                    val cacheKey = KeyValueCacheManager.getSongUrlKey(song.id.toString())
                    val cachedUrl = cacheManager.getCache(cacheKey, String::class.java)
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager updateSongCacheState songId: $song.id cacheLyric: $cachedUrl")
                    if (cachedUrl != null) {
                        audioCacheManager.removeCachedSong(cachedUrl)
                        cacheManager.deleteCache(cacheKey)
                        LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "通过KeyValueCacheManager清理了音频文件缓存")
                    }
                    // 从UI列表中移除
                    _cachedSongs.value = _cachedSongs.value.filter { it.id != song.id }
                    // 缓存状态自动更新（重构 2025-12-03）
                    // 虽然数据库中没有记录，但从 UI 列表移除后，状态已同步
                    // 缓存大小由 CacheStateManager 自动更新 (2025-11-20)
                }
            } catch (e: Exception) {
                // 统一使用BaseViewModel的showError方法 (2025-11-15)
                showError("删除失败: ${e.message}")
                LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "删除缓存失败: ${e.message}")
            }
        }
    }
    // clearError() 方法已移除 (2025-11-15)
    // 原因：SharedFlow不需要手动清除错误消息，每次emit都是新事件
    // loadMusicCounts() 方法已移除 (2025-11-15)
    // 原因：cachedSongCount 现在直接来自 CacheStateManager，自动同步，无需手动更新
    // checkSongCached() 方法已移除 (2025-11-16架构重构)
    // 原因：不再需要逐个检查歌曲缓存状态，直接从cached_songs表读取
    /**
     * 批量加入白名单
     *
     * 执行流程：
     * 1. 更新数据库中歌曲的isProtected字段为true
     * 2. 更新内存中的缓存状态
     * 3. 立即刷新UI状态
     *
     * @param songIds 要加入白名单的歌曲ID列表
     * @param onSuccess 成功回调（用于显示Toast）
     */
    fun addToWhitelist(songIds: List<Long>, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                // 批量更新数据库
                cachedSongDao.batchUpdateProtectedStatus(songIds, true)
                // 架构优化 (2025-11-22): 移除手动刷新
                // 原因：observeProtectedSongIds() Flow 会自动监听数据库变化
                // 数据库更新后会自动触发 Flow，无需手动调用 updateProtectedSongIds()
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachedMusicViewModel 批量加入白名单成功: ${songIds.size} 首歌曲"
                )
                // 成功回调
                onSuccess?.invoke()
            } catch (e: Exception) {
                showError("加入白名单失败: ${e.message}")
                LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "批量加入白名单失败: ${e.message}")
            }
        }
    }
    /**
     * 批量移出白名单
     *
     * 执行流程：
     * 1. 更新数据库中歌曲的isProtected字段为false
     * 2. 更新内存中的缓存状态
     * 3. 立即刷新UI状态
     *
     * @param songIds 要移出白名单的歌曲ID列表
     * @param onSuccess 成功回调（用于显示Toast）
     */
    fun removeFromWhitelist(songIds: List<Long>, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                // 批量更新数据库
                cachedSongDao.batchUpdateProtectedStatus(songIds, false)
                // 架构优化 (2025-11-22): 移除手动刷新
                // 原因：observeProtectedSongIds() Flow 会自动监听数据库变化
                // 数据库更新后会自动触发 Flow，无需手动调用 updateProtectedSongIds()
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachedMusicViewModel 批量移出白名单成功: ${songIds.size} 首歌曲"
                )
                // 成功回调
                onSuccess?.invoke()
            } catch (e: Exception) {
                showError("移出白名单失败: ${e.message}")
                LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "批量移出白名单失败: ${e.message}")
            }
        }
    }
    // updateCacheSize() 方法已移除 (2025-11-20)
    // 原因：缓存大小现在由 CacheStateManager.cacheSizeFormatted 自动管理
    // updateProtectedSongIds() 方法已移除 (2025-11-22)
    // 原因：白名单状态现在通过 observeProtectedSongIds() Flow 自动监听
    // 数据库变化时会自动触发更新，无需手动调用
}
