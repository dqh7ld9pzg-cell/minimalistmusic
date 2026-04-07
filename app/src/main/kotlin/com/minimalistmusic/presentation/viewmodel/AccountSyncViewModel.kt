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
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
/**
 * 账号同步专用 ViewModel
 *
 * 负责管理用户喜欢歌曲的同步逻辑，包括：
 * - 显示/隐藏同步提醒对话框（UI 逻辑）
 * - 处理歌曲喜欢状态切换
 * - 自动同步到云端
 * - 处理登录后的待处理歌曲
 *
 * 架构重构说明：
 * - 2025-11-12: 迁移依赖从 MusicRepository → LocalMusicRepository
 * - 移除了 GetFavoriteSongsUseCase, ToggleFavoriteWithSyncUseCase, SyncFavoritesToCloudUseCase
 * - 直接依赖 LocalMusicRepository，符合单一职责原则
 * - 保持原有功能不变
 */
@HiltViewModel
class AccountSyncViewModel @Inject constructor(
    private val musicLocalRepository: MusicLocalRepository, // 重构：使用LocalMusicRepository代替MusicRepository
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {
    // 我喜欢的音乐
    // 重构：直接从 LocalMusicRepository 获取
    val favoriteSongs = musicLocalRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // 喜欢状态映射 - 用于实时更新UI
    private val _favoriteSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSongIds = _favoriteSongIds.asStateFlow()
    // 同步提醒对话框状态
    private val _showSyncReminder = MutableStateFlow(false)
    val showSyncReminder = _showSyncReminder.asStateFlow()
    // 待处理的歌曲（点击爱心后等待登录的歌曲）
    private val _pendingSong = MutableStateFlow<Song?>(null)
    val pendingSong = _pendingSong.asStateFlow()
    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    init {
        // 监听favorite列表变化，更新映射
        viewModelScope.launch {
            favoriteSongs.collect { songs ->
                _favoriteSongIds.value = songs.map { it.id }.toSet()
            }
        }
    }
    /**
     * 切换歌曲喜欢状态
     *
     * 重构：直接调用 localMusicRepository.toggleFavorite()
     *
     * 优化 (2025-11-20): 简化同步提醒逻辑
     * - 爱心操作在弹出对话框之前就执行
     * - 对话框只弹出一次，之后不再弹出
     * - 移除复选框和"保存到本地"按钮
     */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            try {
                // 检查是否已登录和是否显示过提醒
                val isLoggedIn = userPreferencesDataStore.isLoggedIn.value
                val hasShownReminder = userPreferencesDataStore.dontShowSyncReminder.value
                val isFavorite = _favoriteSongIds.value.contains(song.id)
                // 优化 (2025-11-20): 先执行爱心操作，再显示提醒
                musicLocalRepository.toggleFavorite(song)
                // 如果未登录、未显示过提醒、且是添加到喜欢（非取消），则显示同步提醒
                // 显示一次后就标记为已显示，之后不再弹出
                if (!isLoggedIn && !hasShownReminder && !isFavorite) {
                    _pendingSong.value = song
                    _showSyncReminder.value = true
                    // 标记为已显示，下次不再弹出
                    userPreferencesDataStore.setDontShowSyncReminder(true)
                }
                // UI 会自动通过 favoriteSongs Flow 更新
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }
    /**
     * 关闭同步提醒对话框
     *
     * 优化 (2025-11-20): 简化逻辑
     * - 爱心操作已在toggleFavorite中执行，这里只需要清理状态
     * - 不再需要dontShowAgain参数，因为对话框只会显示一次
     */
    fun dismissSyncReminder() {
        _showSyncReminder.value = false
        _pendingSong.value = null
    }
    /**
     * 导航到登录页面（关闭对话框但保留 pending 歌曲）
     *
     * 优化 (2025-11-20): 简化逻辑
     * - 移除dontShowAgain参数，对话框只显示一次，标记已在toggleFavorite中设置
     * - 爱心操作已在toggleFavorite中执行
     */
    fun navigateToLogin() {
        _showSyncReminder.value = false
        // 保留 pendingSong，用于登录成功后的同步
    }
    /**
     * 处理登录后的 pending 歌曲
     * 在用户从同步提醒对话框点击"去登录"并登录成功后调用
     *
     * 优化 (2025-11-20): 简化逻辑
     * - 爱心操作已在toggleFavorite中执行，这里只需要同步到云端
     * - TODO: 实现同步到云端的逻辑
     */
    fun processPendingSongAfterLogin() {
        viewModelScope.launch {
            _pendingSong.value?.let { song ->
                // 爱心操作已执行，这里可以执行同步到云端的逻辑
                // TODO: 调用云端同步API
            }
            _pendingSong.value = null
        }
    }
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    /**
     * 更新收藏歌曲的排序 (2025-11-19)
     *
     * 通过更新favoritedAt时间戳来实现排序持久化
     * 原理：列表中越靠前的歌曲，favoritedAt值越大（越晚收藏）
     *
     * @param sortedSongs 用户拖拽排序后的歌曲列表（从前到后）
     */
    fun updateFavoriteSongsOrder(sortedSongs: List<Song>) {
        viewModelScope.launch {
            try {
                // 使用当前时间作为基准，按列表顺序分配递减的时间戳
                val baseTime = System.currentTimeMillis()
                val totalCount = sortedSongs.size
                sortedSongs.forEachIndexed { index, song ->
                    // 越靠前的歌曲，favoritedAt越大（时间戳倒序）
                    // 这样查询时ORDER BY favoritedAt DESC就能保持用户的排序
                    val newFavoritedAt = baseTime - (index * 1000L)  // 每首歌间隔1秒
                    // 只更新favoritedAt字段
                    val updatedSong = song.copy(favoritedAt = newFavoritedAt)
                    musicLocalRepository.updateSong(updatedSong)
                }
            } catch (e: Exception) {
                _errorMessage.value = "保存排序失败: ${e.message}"
            }
        }
    }
}
