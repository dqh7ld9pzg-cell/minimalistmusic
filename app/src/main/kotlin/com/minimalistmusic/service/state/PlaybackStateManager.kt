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

package com.minimalistmusic.service.state

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.minimalistmusic.domain.model.PlayMode
import com.minimalistmusic.domain.model.PlaybackState
import com.minimalistmusic.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放状态管理器 (重构 2025-12-01)
 *
 * 职责：
 * - 维护全局播放状态（Single Source of Truth）
 * - 提供线程安全的状态更新（使用 StateFlow.update）
 * - 暴露响应式状态流供 UI 层订阅
 *
 * 架构优势：
 * - 单一数据源（SSOT）：避免状态不同步
 * - 原子更新：使用 update{} 保证并发安全
 * - 易于测试：可以独立测试状态转换逻辑
 * - 解耦合：从 MusicService Companion Object 中提取，降低耦合
 *
 * 使用方式：
 * ```kotlin
 * // Service 层
 * @Inject lateinit var stateManager: PlaybackStateManager
 * stateManager.updateFromPlayer(player)
 *
 * // ViewModel 层
 * viewModelScope.launch {
 *     stateManager.playbackState.collect { state ->
 *         // UI 自动更新
 *     }
 * }
 * ```
 */
@Singleton
class PlaybackStateManager @Inject constructor() {

    // ========== 状态存储 ==========

    /**
     * 内部可变状态流
     * 使用 MutableStateFlow 保证线程安全和响应式更新
     */
    private val _playbackState = MutableStateFlow(createInitialState())

    /**
     * 外部只读状态流
     * 对外暴露 StateFlow，防止外部直接修改状态
     */
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /**
     * 当前状态快照
     * 提供同步访问当前状态的方式，避免 collect 的异步复杂度
     */
    val currentState: PlaybackState
        get() = _playbackState.value

    // ========== 状态更新方法 ==========

    /**
     * 从 ExoPlayer 更新播放状态
     *
     * 更新内容：
     * - isPlaying: 播放/暂停状态
     * - currentPosition: 当前播放位置
     * - duration: 歌曲总时长
     *
     * 注意事项：
     * - 使用 playWhenReady 而非 isPlaying 判断播放状态
     * - 原因：isPlaying 在 buffering 期间为 false，导致 UI 闪烁
     * - playWhenReady 表示用户意图，buffering 期间也为 true
     *
     * @param player ExoPlayer 实例
     */
    fun updateFromPlayer(player: ExoPlayer) {
        _playbackState.update { currentState ->
            currentState.copy(
                isPlaying = player.playWhenReady &&
                        (player.playbackState == Player.STATE_READY ||
                                player.playbackState == Player.STATE_BUFFERING),
                currentPosition = player.currentPosition.coerceAtLeast(0),
                duration = player.duration.takeIf { it > 0 } ?: 0L
            )
        }
    }

    /**
     * 更新播放列表和当前歌曲
     *
     * 使用场景：
     * - playSongs() 开始播放新列表
     * - skipToNext() / skipToPrevious() 切换歌曲
     * - updatePlaylist() 后台更新 URL
     *
     * @param songs 完整播放列表
     * @param index 当前播放索引（逻辑索引，非 ExoPlayer 物理索引）
     * @param currentSong 当前播放歌曲（可为 null）
     */
    fun updatePlaylist(songs: List<Song>, index: Int, currentSong: Song?) {
        _playbackState.update { currentState ->
            currentState.copy(
                playlist = songs,
                currentIndex = index,
                currentSong = currentSong,
                duration = currentSong?.duration ?: 0L // fix issue：应用重启后进入首页只展示最近听的一首歌但不播放时歌曲时长不显示的问题
            )
        }
    }

    /**
     * 更新当前歌曲
     *
     * 使用场景：
     * - ExoPlayer 触发 onMediaItemTransition 时
     * - 需要同步当前歌曲信息
     *
     * @param currentSong 当前播放歌曲
     */
    fun updateCurrentSong(currentSong: Song?) {
        _playbackState.update { currentState ->
            currentState.copy(currentSong = currentSong)
        }
    }

    /**
     * 更新播放列表中的歌曲 URL
     *
     * 使用场景：
     * - 后台异步获取 URL 完成后更新
     * - URL 失效后重新获取并更新
     *
     * @param updatedSongs 更新后的歌曲列表
     */
    fun updateSongsInPlaylist(updatedSongs: List<Song>) {
        _playbackState.update { currentState ->
            val currentPlaylist = currentState.playlist.toMutableList()
            var hasUpdate = false

            updatedSongs.forEachIndexed { index, newSong ->
                if (index < currentPlaylist.size) {
                    val oldSong = currentPlaylist[index]
                    // 只有当 path 从空变为非空时才更新
                    if (oldSong.id == newSong.id &&
                        oldSong.path.isNullOrEmpty() &&
                        !newSong.path.isNullOrEmpty()
                    ) {
                        currentPlaylist[index] = newSong
                        hasUpdate = true
                    }
                }
            }

            if (hasUpdate) {
                currentState.copy(playlist = currentPlaylist)
            } else {
                currentState
            }
        }
    }

    /**
     * 从播放列表中移除歌曲
     *
     * 使用场景：
     * - 用户在聆听足迹中删除歌曲
     * - 需要同步更新播放列表和当前索引
     *
     * @param songId 要移除的歌曲 ID
     * @return 移除后的新索引，如果列表为空返回 -1
     */
    fun removeSongFromPlaylist(songId: Long): Int {
        var newIndex = -1

        _playbackState.update { currentState ->
            val currentPlaylist = currentState.playlist.toMutableList()
            val currentIndex = currentState.currentIndex

            // 找到要删除的歌曲索引
            val removeIndex = currentPlaylist.indexOfFirst { it.id == songId }
            if (removeIndex == -1) {
                return@update currentState
            }

            // 从列表中移除
            currentPlaylist.removeAt(removeIndex)

            // 计算新的当前索引
            newIndex = when {
                currentPlaylist.isEmpty() -> -1
                removeIndex < currentIndex -> currentIndex - 1
                removeIndex == currentIndex -> {
                    // 如果删除的是当前播放的歌曲，保持当前索引（会播放下一首）
                    if (currentIndex >= currentPlaylist.size) currentPlaylist.size - 1 else currentIndex
                }
                else -> currentIndex
            }

            currentState.copy(
                playlist = currentPlaylist,
                currentIndex = newIndex,
                currentSong = if (newIndex >= 0 && newIndex < currentPlaylist.size)
                    currentPlaylist[newIndex] else null
            )
        }

        return newIndex
    }

    /**
     * 切换播放模式
     *
     * 切换顺序：
     * SEQUENCE（顺序） → SHUFFLE（随机） → REPEAT_ONE（单曲循环） → SEQUENCE...
     *
     * @return 切换后的播放模式
     */
    fun togglePlayMode(): PlayMode {
        var newMode: PlayMode = PlayMode.SEQUENCE

        _playbackState.update { currentState ->
            newMode = when (currentState.playMode) {
                PlayMode.SEQUENCE -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
                PlayMode.REPEAT_ONE -> PlayMode.SEQUENCE
            }
            currentState.copy(playMode = newMode)
        }

        return newMode
    }

    /**
     * 设置播放模式
     *
     * 使用场景：
     * - 用户手动选择播放模式
     * - 恢复用户上次的播放模式
     *
     * @param playMode 新的播放模式
     */
    fun setPlayMode(playMode: PlayMode) {
        _playbackState.update { currentState ->
            currentState.copy(playMode = playMode)
        }
    }

    /**
     * 清空播放列表
     *
     * 使用场景：
     * - 用户清空播放队列
     * - 退出播放页面
     */
    fun clearPlaylist() {
        _playbackState.update { currentState ->
            currentState.copy(
                playlist = emptyList(),
                currentIndex = -1,
                currentSong = null
            )
        }
    }

    // ========== 私有方法 ==========

    /**
     * 创建初始状态
     *
     * 默认值：
     * - isPlaying: false（未播放）
     * - currentSong: null（无歌曲）
     * - currentPosition: 0（开始位置）
     * - duration: 0（无时长）
     * - playMode: SEQUENCE（顺序播放）
     * - playlist: emptyList（空列表）
     * - currentIndex: -1（无索引）
     */
    private fun createInitialState() = PlaybackState(
        isPlaying = false,
        currentSong = null,
        currentPosition = 0L,
        duration = 0L,
        playMode = PlayMode.SEQUENCE,
        playlist = emptyList(),
        currentIndex = -1
    )
}
