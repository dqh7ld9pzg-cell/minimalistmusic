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
import com.minimalistmusic.domain.cache.CacheStateManager
import com.minimalistmusic.domain.model.RecommendPlaylist
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
/**
 * HomeViewModel - 首页
 *
 * 架构重构说明：
 * - 2025-11-12: 迁移依赖从 MusicRepository → LocalMusicRepository
 * - 移除了 GetLocalSongsUseCase, GetFavoriteSongsUseCase, GetPlayHistoryUseCase, GetPagedPlayHistoryUseCase
 * - 直接依赖 LocalMusicRepository，符合单一职责原则
 * - 保持 ScanAndSaveLocalMusicUseCase（复杂业务逻辑，适合保留UseCase）
 * - 保持原有功能不变
 *
 * 缓存状态支持 (2025-11-14 重构):
 * - 继承BaseViewModel，自动获得缓存状态管理能力
 * - 移除重复的AudioCacheManager和CacheManager依赖
 * - 移除重复的isSongCached()实现，使用BaseViewModel提供的方法
 */
@HiltViewModel
class HomeViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    application: Application, // 注入Application，传递给BaseViewModel
    private val musicLocalRepository: MusicLocalRepository, // 重构：使用LocalMusicRepository代替MusicRepository
    cacheStateManager: CacheStateManager // 新增 (2025-11-14): 通过BaseViewModel注入
) : BaseViewModel(
    application,
    cacheStateManager
) { // 重构 (2025-11-14): 继承BaseViewModel获得AndroidViewModel和缓存状态管理能力
    // 本地歌曲列表
    // 重构：直接从 LocalMusicRepository 获取
    val localSongs = musicLocalRepository.getAllLocalSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // 我喜欢的音乐
    // 重构：直接从 LocalMusicRepository 获取
    // 优化 (2025-11-23): 使用 Eagerly 避免页面进入时闪烁（空→有内容）
    val favoriteSongs = musicLocalRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // 我喜欢的歌单
    // 从 LocalMusicRepository 获取收藏的歌单列表
    val favoritePlaylists = musicLocalRepository.getAllFavoritePlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // 播放历史
    // 重构：直接从 LocalMusicRepository 获取
    val playHistory = musicLocalRepository.getPlayHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // 推荐歌单
    private val _recommendPlaylists = MutableStateFlow<List<RecommendPlaylist>>(emptyList())
    val recommendPlaylists = _recommendPlaylists.asStateFlow()
    // 错误消息统一使用BaseViewModel的errorMessage (SharedFlow)
    // 移除重复定义 (2025-11-15)
    // ========== 分页播放历史管理 (2025-11-19) ==========
    private val _pagedPlayHistory = MutableStateFlow<List<Song>>(emptyList())
    val pagedPlayHistory = _pagedPlayHistory.asStateFlow()
    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory = _isLoadingHistory.asStateFlow()
    private val _hasMoreHistory = MutableStateFlow(true)
    val hasMoreHistory = _hasMoreHistory.asStateFlow()
    private var currentPage = 0
    private val pageSize = 30  // 优化 (2025-11-23): 统一分页大小为30条，平衡加载速度和用户体验
    // 播放历史总数 (2025-11-20重构：使用Flow监听数据库变化，实现Single Source of Truth)
    val playHistoryCount = musicLocalRepository.observePlayHistoryCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    // 监听最新播放时间变化用于触发刷新
    private val latestPlayedAt = musicLocalRepository.observeLatestPlayedAt()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    // 上一次的最新播放时间（用于检测新增/更新播放记录）
    private var lastPlayedAt: Long? = null
    init {
        // 监听最新播放时间变化，自动刷新列表 (2025-11-20)
        // 无论是新增还是更新播放时间，都会触发刷新
        viewModelScope.launch {
            latestPlayedAt.collect { playedAt ->
                // 只在播放时间变化时刷新
                // 删除时不需要刷新，因为已经在deletePlayHistoryBySongId中处理了
                if (playedAt != null && playedAt != lastPlayedAt && lastPlayedAt != null) {
                    // 重新加载第一页，因为最新播放的在最前面
                    refreshFirstPagePlayHistory()
                }
                lastPlayedAt = playedAt
            }
        }
    }
    /**
     * 刷新播放历史第一页
     * 用于新增播放记录后更新列表
     * @since 2025-11-20
     */
    private fun refreshFirstPagePlayHistory() {
        viewModelScope.launch {
            try {
                // 重新加载当前已加载的所有数据
                val totalLoaded = _pagedPlayHistory.value.size.coerceAtLeast(pageSize)
                val refreshedSongs = musicLocalRepository.getPlayHistoryWithTimePaged(
                    totalLoaded,
                    0
                )
                _pagedPlayHistory.value = refreshedSongs
            } catch (e: Exception) {
                // 刷新失败不显示错误，保持当前数据
            }
        }
    }
    /**
     * 删除指定歌曲的播放历史
     * @since 2025-11-20
     */
    fun deletePlayHistoryBySongId(songId: Long) {
        viewModelScope.launch {
            try {
                musicLocalRepository.deletePlayHistoryBySongId(songId)
                // 从当前列表中移除
                _pagedPlayHistory.value = _pagedPlayHistory.value.filter { it.id != songId }
                // 播放历史总数会通过Flow自动更新，无需手动刷新
            } catch (e: Exception) {
                showError("删除失败: ${e.message}")
            }
        }
    }
    /**
     * 加载更多播放历史
     *
     * 使用ViewModel管理分页状态，确保页面切换时数据不丢失
     * @since 2025-11-19
     */
    fun loadMorePlayHistory() {
        if (_isLoadingHistory.value || !_hasMoreHistory.value) return
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val newSongs = musicLocalRepository.getPlayHistoryWithTimePaged(
                    pageSize,
                    currentPage * pageSize
                )
                if (newSongs.isEmpty()) {
                    _hasMoreHistory.value = false
                } else {
                    _pagedPlayHistory.value = _pagedPlayHistory.value + newSongs
                    currentPage++
                }
            } catch (e: Exception) {
                showError("加载播放历史失败: ${e.message}")
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }
    /**
     * 重置播放历史分页状态
     * 用于下拉刷新等场景
     */
    fun resetPlayHistory() {
        _pagedPlayHistory.value = emptyList()
        _hasMoreHistory.value = true
        currentPage = 0
        loadMorePlayHistory()
    }
    /**
     * 获取分页的播放历史
     *
     * 重构：直接调用 localMusicRepository.getPlayHistoryPaged()
     */
    suspend fun getPlayHistoryPaged(limit: Int, offset: Int): List<Song> {
        return try {
            // 重构：直接调用 Repository 方法（这个方法直接返回 List，不是 Result）
            musicLocalRepository.getPlayHistoryPaged(limit, offset)
        } catch (e: Exception) {
            // 统一使用BaseViewModel的showError方法 (2025-11-15)
            showError("加载失败: ${e.message}")
            emptyList()
        }
    }
    /**
     * 获取分页的播放历史（包含播放时间）
     *
     * 返回的Song对象的addedAt字段被设置为最近播放时间
     * 用于PlayHistoryScreen显示最近播放时间
     * @since 2025-11-19
     */
    suspend fun getPlayHistoryWithTimePaged(limit: Int, offset: Int): List<Song> {
        return try {
            musicLocalRepository.getPlayHistoryWithTimePaged(limit, offset)
        } catch (e: Exception) {
            showError("加载失败: ${e.message}")
            emptyList()
        }
    }
    // clearError() 方法已移除 (2025-11-15)
    // 原因：SharedFlow不需要手动清除错误消息，每次emit都是新事件
    // 缓存状态查询方法已移至BaseViewModel (2025-11-14)
    // 所有子类都可以直接调用 isSongCached(song) 方法
    /**
     * 切换歌单收藏状态
     *
     * 2025-11-16新增：支持歌单收藏功能
     * 架构说明：通过ViewModel暴露业务方法，而不是直接暴露Repository
     */
    fun toggleFavoritePlaylist(playlist: com.minimalistmusic.domain.model.RecommendPlaylist) {
        viewModelScope.launch {
            try {
                musicLocalRepository.toggleFavoritePlaylist(playlist)
            } catch (e: Exception) {
                showError("收藏失败: ${e.message}")
            }
        }
    }
}