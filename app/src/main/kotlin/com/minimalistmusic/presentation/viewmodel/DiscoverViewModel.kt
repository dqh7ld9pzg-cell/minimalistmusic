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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimalistmusic.domain.model.RecommendPlaylist
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.PlaylistCache
import com.minimalistmusic.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
/**
 * DiscoverViewModel - 发现页面
 *
 * 架构重构说明：
 * - 移除了 GetCachedRecommendedPlaylistsUseCase 和 GetSongPlayUrlUseCase
 * - 直接依赖 OnlineMusicRepository，简化调用链
 * - 保持原有功能不变
 */
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val musicOnlineRepository: MusicOnlineRepository
) : ViewModel() {
    // 推荐歌单
    private val _recommendPlaylists = MutableStateFlow<List<RecommendPlaylist>>(emptyList())
    val recommendPlaylists = _recommendPlaylists.asStateFlow()
    // 歌单歌曲
    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs = _playlistSongs.asStateFlow()
    // 当前选中的歌单
    private val _selectedPlaylist = MutableStateFlow<RecommendPlaylist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    // 精选歌单加载状态
    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    // 是否已初始化加载过数据
    private var hasInitialized = false
    // 上次刷新时间戳，用于防抖
    private var lastRefreshTime = 0L
    private val REFRESH_DEBOUNCE_MS = 1000L // 1秒防抖
    init {
        // ViewModel初始化时自动加载一次
        loadRecommendPlaylists(isForceRefresh = false)
    }
    /**
     * 加载推荐歌单
     *
     * 策略：从预制歌单中随机选择5个
     *
     * @param isForceRefresh 是否强制刷新（用户主动点击刷新按钮）
     */
    fun loadRecommendPlaylists(isForceRefresh: Boolean = false) {
        // 如果不是强制刷新且已经初始化过，直接返回
        if (!isForceRefresh && hasInitialized && _recommendPlaylists.value.isNotEmpty()) {
            return
        }
        // 防抖：如果距离上次刷新不足1秒，忽略本次请求
        val currentTime = System.currentTimeMillis()
        if (isForceRefresh && currentTime - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
            return
        }
        lastRefreshTime = currentTime
        // 优化 (2025-11-28)：避免重复加载
        // 如果正在加载中，忽略新的请求，防止闪烁
        if (_isLoading.value) {
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            // 调用Repository获取推荐歌单（强制刷新时传入true以跳过缓存）
            when (val result = musicOnlineRepository.getRecommendPlaylists()) {
                is Result.Success -> {
                    _recommendPlaylists.value = result.data
                    hasInitialized = true
                }
                is Result.Error -> {
                    _errorMessage.value = "加载失败: ${result.exception.message}"
                }
                is Result.Loading -> {
                    // Loading状态已通过_isLoading处理
                }
            }
            _isLoading.value = false
        }
    }
    /**
     * 播放歌曲（获取播放URL）
     *
     * 重构：直接调用 onlineMusicRepository.getSongPlayUrl()
     */
    fun playSong(song: Song, onResult: (Song?) -> Unit) {
        viewModelScope.launch {
            // 重构：直接调用 Repository 方法
            when (val result = musicOnlineRepository.getSongPlayUrl(song)) {
                is Result.Success -> {
                    // 返回带有播放URL的歌曲
                    val songWithUrl = song.copy(path = result.data)
                    onResult(songWithUrl)
                }
                is Result.Error -> {
                    _errorMessage.value = "获取播放链接失败: ${result.exception.message}"
                    onResult(null)
                }
                else -> onResult(null)
            }
        }
    }
    /**
     * 清空歌单歌曲
     */
    fun clearPlaylistSongs() {
        _playlistSongs.value = emptyList()
        _selectedPlaylist.value = null
    }
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    fun onPlayListClicked(playlist: RecommendPlaylist) {
        PlaylistCache.put(playlist)
    }
}