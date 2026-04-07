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
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.domain.cache.CacheStateManager
import com.minimalistmusic.domain.model.PlayMode
import com.minimalistmusic.domain.model.PlaybackState
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.repository.PlaybackController
import com.minimalistmusic.domain.usecase.history.SyncHistoryToCloudUseCase
import com.minimalistmusic.domain.usecase.player.HandleUrlExpiredUseCase
import com.minimalistmusic.domain.usecase.player.LoadLyricsUseCase
import com.minimalistmusic.domain.usecase.player.PreparePlaylistUseCase
import com.minimalistmusic.domain.usecase.player.PrepareSongUseCase
import com.minimalistmusic.service.state.PlaybackStateManager
import com.minimalistmusic.util.LyricLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * PlayerViewModel - 播放器页面
 *
 * 架构重构说明：
 * - 2025-11-12: 迁移依赖从 MusicRepository → LocalMusicRepository
 * - 移除了 AddToHistoryUseCase
 * - 直接依赖 LocalMusicRepository，符合单一职责原则
 * - 保持 LoadLyricsUseCase 和 SyncHistoryToCloudUseCase（复杂业务逻辑，适合保留UseCase）
 * - 保持原有功能不变
 *
 * 离线播放修复 (2025-11-12):
 * - 添加 OnlineMusicRepository 依赖
 * - 播放在线歌曲前先获取真实的播放URL（优先从缓存，缓存未命中才请求网络）
 * - 确保断网时能播放已缓存URL的歌曲
 */
@UnstableApi
@OptIn(FlowPreview::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val loadLyricUseCase: LoadLyricsUseCase,
    private val preparePlaylistUseCase: PreparePlaylistUseCase,
    private val handleUrlExpiredUseCase: HandleUrlExpiredUseCase,
    private val prepareSongUseCase: PrepareSongUseCase,
    private val musicLocalRepository: MusicLocalRepository,
    private val syncHistoryUseCase: SyncHistoryToCloudUseCase,
    private val playbackController: PlaybackController,
    private val playbackStateManager: PlaybackStateManager,
    cacheStateManager: CacheStateManager,
) : BaseViewModel(
    application,
    cacheStateManager
) {
    /**
     * 播放历史记录跟踪 (2025-11-20)
     *
     * 新增：播放超过10秒才记录到播放历史
     * - recordedSongId: 当前已记录的歌曲ID，避免重复记录
     * - lastPlayPosition: 上次检查的播放位置，用于计算播放时长
     * - accumulatedPlayTime: 当前歌曲累计播放时长（毫秒）
     */
    private var recordedSongId: Long? = null
    private var lastPlayPosition: Long = 0
    private var accumulatedPlayTime: Long = 0
    private val minPlayTimeForHistory = 10_000L // 10秒
    /**
     * 当前播放请求的Job
     *
     * 用途：
     * - 跟踪当前正在进行的播放请求
     * - 当用户快速点击多首歌曲时，取消之前的请求，只处理最新的
     * - 避免多个请求同时进行导致的播放切换延迟和混乱
     */
    private var currentPlayJob: Job? = null

    /**
     * 播放状态
     *
     * 架构重构：直接使用PlaybackStateManager提供的状态Flow
     * PlaybackStateManager是播放状态的唯一数据源（SSOT - Single Source of Truth）
     * PlaybackRepository只负责播放控制，不管理状态
     */
    val playbackState: StateFlow<PlaybackState> = playbackStateManager.playbackState

    // 当前歌词
    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics = _currentLyrics.asStateFlow()

    init {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayViewModel init obj: ${this}"
        )

        // 架构重构: Service绑定由PlaybackRepository管理
        // 设置URL失效监听器
        playbackController.setOnUrlExpiredListener { songId, currentIndex ->
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "PlayerViewModel 收到URL失效事件: songId=$songId, index=$currentIndex"
            )
            handleUrlExpired(songId, currentIndex)
        }
        // 优化 (2025-11-29): 智能初始化迷你播放器状态
        // 策略:
        // 1. 如果playbackState已有歌曲(播放中或暂停) → 不做任何处理,保持当前状态
        // 2. 如果playbackState无歌曲 → 延迟1秒后加载最近播放的第一首歌曲到暂停状态
        // 修复: 避免退出应用重新进入时,正在播放的歌曲被替换成暂停状态
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1000)  // 延迟1秒,等待MusicService状态恢复
            // 检查playbackState是否已有歌曲
            val currentSong = playbackState.value.currentSong
            if (currentSong != null) {
                // 已有歌曲(可能是播放中或暂停),不做任何处理
                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "PlayerViewModel init: playbackState已有歌曲,保持当前状态: ${currentSong.title}, isPlaying=${playbackState.value.isPlaying}"
                )
                return@launch
            }
            // playbackState无歌曲,加载最近播放的歌曲
            try {
                val recentSongs = musicLocalRepository.getPlayHistoryPaged(limit = 1, offset = 0)
                if (recentSongs.isNotEmpty()) {
                    val lastSong = recentSongs.first()
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "PlayerViewModel init: 自动加载最后播放的歌曲（暂停状态）: ${lastSong.title}"
                    )
                    // 切换到主线程，只加载不播放
                    withContext(Dispatchers.Main) {
                        loadSongWithoutPlaying(lastSong)
                    }
                }
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "PlayerViewModel init: 加载最后播放的歌曲失败: ${e.message}"
                )
            }
        }
        // 监听歌曲变化，加载歌词和监听缓存
        // 修复 (2025-11-23): 解决歌曲切换时重复触发问题
        // 根本原因：
        // 1. MusicService 在 playSongs() 中多次更新 _playbackState:
        //    - playSongs() 第一次更新 (设置 playlist/currentIndex/currentSong)
        //    - prepare() 触发 onMediaItemTransition() → updateCurrentSong() 第二次更新
        //    - play() 可能再次触发回调
        // 2. 每次 copy() 创建新对象，虽然 songId 相同但对象引用不同
        // 3. distinctUntilChanged() 只对比 songId 值，但 StateFlow 每次更新都会触发 map
        //
        // 解决方案：
        // - 使用 debounce(300) 延迟处理，合并短时间内的多次更新
        // - 在 collect 中添加二次去重，避免处理相同 songId
        // - 记录上次处理的 songId，只在真正切换时执行逻辑
        var lastProcessedSongId: Long? = null
        viewModelScope.launch {
            playbackState
                .map { state -> state.currentSong?.id }
                .distinctUntilChanged()
                //.debounce()  // 延迟300ms，等待所有状态更新完成
                .collect { songId ->
                    val song = playbackState.value.currentSong
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "PlayerViewMode playbackState状态发生变化 currentSong: $song"
                    )
                    // 二次去重：避免因 StateFlow 特性导致的重复处理
                    if (songId != null && songId == lastProcessedSongId) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel 跳过重复处理: songId=$songId (已处理过)"
                        )
                        return@collect
                    }
                    if (song != null && songId != null) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel 监听到歌曲切换, songId=$songId, 标题=${song.title}"
                        )
                        lastProcessedSongId = songId
                        // 清空旧歌词，避免短暂显示上一首歌曲的歌词
                        _currentLyrics.value = emptyList()
                        // 加载新歌词（Repository层有持久化缓存）
                        _currentLyrics.value = loadLyricUseCase(song)
                        // 监听缓存进度
                        val durationMs = playbackState.value.duration.takeIf { it > 0 }
                            ?: (song.duration * 1000L)
                        // cacheStateManager.monitorCacheProgress(
                        //     song.id,
                        //     song.path,
                        //     song.isLocal,
                        //     durationMs
                        // )
                    }
                }
        }
        // 监听播放进度，标记聆听足迹（2025-11-20重构）
        // - 播放记录已在playSongs/seekToSong/skipToNext/skipToPrevious入口处添加
        // - 这里只负责播放超过10秒时标记为聆听足迹（isListeningRecord=true）
        viewModelScope.launch {
            var lastSongId: Long? = null
            playbackState.collect { state ->
                val song = state.currentSong ?: return@collect
                val currentPosition = state.currentPosition
                // 检测歌曲切换，重置状态
                if (song.id != lastSongId) {
                    lastSongId = song.id
                    accumulatedPlayTime = 0
                    lastPlayPosition = currentPosition
                    recordedSongId = null
                    return@collect
                }
                // 如果当前播放周期内已标记为聆听足迹，跳过（避免重复标记）
                if (recordedSongId == song.id) return@collect
                // 只在播放状态下累计时长
                if (state.isPlaying) {
                    // 计算本次增量：当前位置 - 上次位置
                    val delta = if (currentPosition > lastPlayPosition) {
                        currentPosition - lastPlayPosition
                    } else {
                        // 位置倒退（可能是seek操作），重置为0，只计算当前位置作为基准
                        0
                    }
                    accumulatedPlayTime += delta
                    lastPlayPosition = currentPosition
                    // 累计播放超过10秒，标记为聆听足迹
                    if (accumulatedPlayTime >= minPlayTimeForHistory) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel 播放超过10秒，标记为聆听足迹: ${song.title}, 累计时长: ${accumulatedPlayTime}ms"
                        )
                        recordedSongId = song.id
                        musicLocalRepository.markAsListeningRecord(song.id)
                        // 更新缓存歌曲的最近播放时间 (2025-11-21)
                        // 用于LRU缓存管理，确保经常听的歌曲不会被清理
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                musicLocalRepository.updateCachedSongLastPlayedAt(song.id)
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_VIEWMODEL,
                                    "PlayerViewModel 已更新缓存歌曲播放时间: ${song.title}"
                                )
                            } catch (e: Exception) {
                                LogConfig.w(
                                    LogConfig.TAG_PLAYER_VIEWMODEL,
                                    "PlayerViewModel 更新缓存歌曲播放时间失败: ${e.message}"
                                )
                            }
                        }
                        syncHistoryIfNeeded()
                    }
                } else {
                    // 暂停状态，更新lastPlayPosition以便恢复播放时正确计算
                    lastPlayPosition = currentPosition
                }
            }
        }
        // 移除持续监听播放进度的方式（性能优化 2025-11-19）
        // 原方案：每秒检查播放进度是否到达检查点，存在持续运行和不必要计算的问题
        // 新方案：由CacheStateManager在初始监听未完成时，根据歌曲时长计算并安排定时检查
        // 详见 CacheStateManager.monitorCacheProgress() 的实现
    }

    /**
     * 播放歌曲列表
     *
     * 离线播放修复 (2025-11-12):
     * - 对在线歌曲（isLocal=false），先获取真实的播放URL
     * - 优先从缓存读取URL，缓存未命中才请求网络
     * - 确保断网时能播放已缓存URL的歌曲
     *
     * 流程:
     * 1. 遍历歌曲列表
     * 2. 对于在线歌曲，调用 onlineMusicRepository.getSongPlayUrl() 获取真实URL
     * 3. 用真实URL替换song.path
     * 4. 传递给MusicService播放
     *
     * @param songs 歌曲列表
     * @param startIndex 起始播放索引
     */
    /**
     * 播放歌曲列表
     *
     * 修复说明 (2025-11-13):
     * - 修复判断条件：所有在线歌曲都需要获取真实URL，不再检查path是否为null
     * - 原因：歌单API返回的path可能是占位符URL（如http://music.163.com/song/media/outer/url?id=xxx）
     * - 必须调用getSongPlayUrl()获取真实的播放URL
     *
     * 离线播放支持 (2025-11-12):
     * - 对在线歌曲获取真实播放URL（优先从缓存）
     * - 优先从缓存读取URL，缓存未命中才请求网络
     * - 确保断网时能播放已缓存URL的歌曲
     *
     * @param songs 歌曲列表
     * @param startIndex 起始播放索引
     */
    /**
     * 只加载歌曲信息而不自动播放（暂停状态）
     *
     * 用途：应用启动时加载最后播放的歌曲到迷你播放器，但不自动播放
     *
     * @param song 要加载的歌曲
     * @since 2025-11-29
     */
    private fun loadSongWithoutPlaying(song: Song) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel loadSongWithoutPlaying: ${song.title}"
        )

        viewModelScope.launch {
            try {
                // 使用 PrepareSongUseCase 准备歌曲（插入记录 + 获取URL）
                val preparedSong = prepareSongUseCase(song)

                if (preparedSong != null) {
                    // 加载到播放器并暂停
                    playbackController.playSongs(listOf(preparedSong), 0)
                    playbackController.pause()  // 立即暂停
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "PlayerViewModel 歌曲已加载到暂停状态: ${song.title}"
                    )
                } else {
                    // 准备失败，但仍尝试加载
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "PlayerViewModel 歌曲准备失败，尝试直接加载: ${song.title}"
                    )
                    playbackController.playSongs(listOf(song), 0)
                    playbackController.pause()
                }
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "PlayerViewModel loadSongWithoutPlaying 失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 播放歌曲列表
     *
     * 优化版本 (2025-11-15):
     * - 异步获取URL，不阻塞UI
     * - 优先播放当前歌曲，后台获取其他歌曲URL
     * - 添加错误提示，提升用户体验
     * - 添加详细日志诊断问题
     *
     * 流程优化：
     * 1. 立即获取startIndex对应歌曲的URL
     * 2. 如果获取成功，立即开始播放
     * 3. 如果获取失败，显示错误提示
     * 4. 后台异步获取其他在线歌曲的URL（可选）
     *
     * @param songs 歌曲列表
     * @param startIndex 起始播放索引
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel playSongs start ========================================"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel playSongs 歌曲数量: ${songs.size}, 起始索引: $startIndex, 歌曲名称：${songs.getOrNull(startIndex)?.title}"
        )

        // 性能优化：取消之前的播放请求，避免多个请求同时进行
        currentPlayJob?.cancel()
        LogConfig.d(LogConfig.TAG_PLAYER_VIEWMODEL, "PlayerViewModel playSongs 已取消之前的播放请求")

        // 启动新的播放请求协程
        currentPlayJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                // 使用 PreparePlaylistUseCase 准备播放列表（封装了复杂的准备逻辑）
                val result = preparePlaylistUseCase(songs, startIndex)

                when (result) {
                    is PreparePlaylistUseCase.PrepareResult.Success -> {
                        val totalTime = System.currentTimeMillis() - startTime
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel playSongs 播放列表准备完成，耗时: ${totalTime}ms"
                        )

                        // 调用 PlaybackController 开始播放
                        playbackController.playSongs(result.songs, result.startIndex)
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel playSongs 播放已开始: ${result.targetSong.title}"
                        )
                    }

                    is PreparePlaylistUseCase.PrepareResult.Error -> {
                        LogConfig.e(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel playSongs 播放列表准备失败: ${result.message}"
                        )
                        showError(result.message)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消，正常流程控制，不显示错误提示
                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "PlayerViewModel playSongs 协程被取消"
                )
                throw e  // 重新抛出，让协程系统处理
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "PlayerViewModel playSongs [ERROR] 播放失败: ${e.javaClass.simpleName}: ${e.message}"
                )

                showError("播放失败：${e.message ?: "未知错误"}")
            }
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel playSongs end ========================================"
        )
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    /**
     * 跳到上一首歌曲
     *
     * 根据播放模式分别处理：
     * - 顺序播放：计算上一首索引并使用 seekToSong 跳转
     * - 随机播放：计算随机索引并使用 seekToSong 跳转
     * - 单曲循环：调用 PlaybackController.skipToPrevious 重播当前歌曲
     */
    fun skipToPrevious() {
        val currentState: PlaybackState = playbackState.value
        val currentIndex = currentState.currentIndex
        val playlist = currentState.playlist
        when (currentState.playMode) {
            PlayMode.SEQUENCE -> {
                // 顺序播放：计算上一首索引并使用 seekToSong 跳转
                val prevIndex = if (currentIndex > 0) {
                    currentIndex - 1
                } else {
                    // 循环到最后一首
                    playlist.size - 1
                }
                seekToSong(prevIndex)
            }

            PlayMode.SHUFFLE -> {
                // 随机播放：计算随机索引
                val availableIndices = playlist.indices.filter { it != currentIndex }
                if (availableIndices.isEmpty()) return
                val randomIndex = availableIndices.random()
                seekToSong(randomIndex)
            }

            PlayMode.REPEAT_ONE -> {
                // 单曲循环：重播当前歌曲
                playbackController.skipToPrevious()
            }
        }
    }

    /**
     * 跳到下一首歌曲
     *
     * 根据播放模式分别处理：
     * - 顺序播放：计算下一首索引并使用 seekToSong 跳转
     * - 随机播放：计算随机索引并使用 seekToSong 跳转
     * - 单曲循环：调用 PlaybackController.skipToNext 重播当前歌曲
     */
    fun skipToNext() {
        val currentState: PlaybackState = playbackState.value
        val currentIndex = currentState.currentIndex
        val playlist = currentState.playlist
        when (currentState.playMode) {
            PlayMode.SEQUENCE -> {
                // 顺序播放：计算下一首索引并使用 seekToSong 跳转
                val nextIndex = if (currentIndex < playlist.size - 1) {
                    currentIndex + 1
                } else {
                    // 循环到第一首
                    0
                }
                seekToSong(nextIndex)
            }

            PlayMode.SHUFFLE -> {
                // 随机播放：计算随机索引
                val availableIndices = playlist.indices.filter { it != currentIndex }
                if (availableIndices.isEmpty()) return
                val randomIndex = availableIndices.random()
                seekToSong(randomIndex)
            }

            PlayMode.REPEAT_ONE -> {
                // 单曲循环：重播当前歌曲
                playbackController.skipToNext()
            }
        }
    }

    fun seekTo(position: Long) {
        playbackController.seekTo(position)
    }

    fun togglePlayMode() {
        playbackController.togglePlayMode()
    }

    /**
     * 跳转到指定索引的歌曲
     *
     * 在跳转前先获取目标歌曲的播放URL
     * - 问题：搜索结果中的歌曲path为null，直接跳转会导致播放失败
     * - 方案：先获取目标歌曲的URL，更新播放列表，然后再播放
     */
    fun seekToSong(index: Int) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel seekToSong start ========================================"
        )
        val currentState: PlaybackState = playbackState.value
        val playlist = currentState.playlist  // 捕获当前播放列表
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel seekToSong 当前索引: ${currentState.currentIndex}，目标索引: $index 播放列表大小: ${playlist.size}"
        )
        val targetSong: Song? = playlist.getOrNull(index)
        if (targetSong == null) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "PlayerViewModel seekToSong [ERROR] 目标歌曲不存在，索引越界: $index"
            )
            return
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel seekToSong 目标歌曲: ${targetSong.title}，歌曲ID: ${targetSong.id}，path: ${targetSong.path ?: "null"}")
        // 如果是在线歌曲且path为空，先获取URL
        if (!targetSong.isLocal && targetSong.path.isNullOrEmpty()) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "PlayerViewModel seekToSong 在线歌曲且path为空，需要准备歌曲"
            )

            // 取消之前的播放请求，避免竞态条件
            currentPlayJob?.cancel()
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "PlayerViewModel seekToSong 已取消之前的播放请求"
            )

            currentPlayJob = viewModelScope.launch {
                try {
                    // 使用 PrepareSongUseCase 准备歌曲
                    val preparedSong = prepareSongUseCase(targetSong)

                    if (preparedSong != null) {
                        // 更新播放列表并播放
                        val updatedPlaylist = playlist.toMutableList()
                        updatedPlaylist[index] = preparedSong
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel seekToSong 歌曲准备完成，调用 playSongs"
                        )
                        playbackController.playSongs(updatedPlaylist, index)
                    } else {
                        LogConfig.e(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel seekToSong 歌曲准备失败"
                        )
                        showError("无法播放：获取播放地址失败")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 协程被取消，正常流程控制
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "PlayerViewModel seekToSong 协程被取消"
                    )
                    throw e
                } catch (e: Exception) {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "PlayerViewModel seekToSong 异常: ${e.message}"
                    )
                    showError("播放失败：${e.message}")
                }
            }
        } else {
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "PlayerViewModel seekToSong 本地歌曲或已有URL，直接播放"
            )

            viewModelScope.launch {
                // 使用 PrepareSongUseCase 确保歌曲在数据库中
                prepareSongUseCase(targetSong)
                // 直接播放
                playbackController.playSongs(playlist, index)
            }
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel seekToSong end ========================================"
        )
    }

    /**
     * 判断是否可以播放指定歌曲（2025-11-21）
     *
     * 用途：避免重复点击正在播放的歌曲导致重新播放
     *
     * 规则：
     * - 如果点击的是正在播放的歌曲 且 正在播放中 → 返回 false（不允许播放）
     * - 其他情况 → 返回 true（允许播放）
     *
     * 为什么在这里判断而不是在 MusicService 中：
     * - MusicService 需要处理播放模式切换逻辑（顺序、单曲循环、随机）
     * - 在 Service 层拦截会导致播放模式失效
     * - UI 层决定用户交互逻辑更合理
     *
     * @param songId 要播放的歌曲 ID
     * @return true 表示可以播放，false 表示应忽略
     */
    fun canPlaySong(songId: Long): Boolean {
        val currentState = playbackState.value
        // 如果点击的是正在播放的歌曲且正在播放中，则不允许
        return !(currentState.currentSong?.id == songId && currentState.isPlaying)
    }

    /**
     * 从播放列表中移除指定歌曲
     *
     * 使用场景:
     * - 用户在聆听足迹中删除歌曲后，同步更新播放列表
     *
     * @param songId 要移除的歌曲ID
     */
    fun removeSongFromPlaylist(songId: Long) {
        playbackController.removeSongFromPlaylist(songId)
    }

    /**
     * 如果已登录且开启了同步，则同步"播放历史"到云端
     *
     * 职责：调用 UseCase 执行同步操作
     * 业务逻辑（登录检查、开关检查、数据获取）由 UseCase 处理
     */
    private suspend fun syncHistoryIfNeeded() {
        // 同步失败不影响播放
        syncHistoryUseCase()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCleared() {
        LogConfig.d(LogConfig.TAG_PLAYER_VIEWMODEL, "PlayerViewModel onCleared() 被调用")
        super.onCleared()
        // 架构优化 (2025-12-04): 移除轮询监听任务取消
        // TransferListener 自动管理生命周期，无需手动取消
        // cacheStateManager.cancelCurrentMonitor()  // 已移除
        // 清除URL失效监听器
        playbackController.setOnUrlExpiredListener(null)
        // 注: Service的解绑由PlaybackRepository管理，不需要在这里处理
    }

    /**
     * 处理URL失效事件
     *
     * 当播放器检测到403错误时调用
     * 使用 HandleUrlExpiredUseCase 处理业务逻辑
     *
     * @param songId 失效URL对应的歌曲ID
     * @param currentIndex 歌曲在播放列表中的索引
     */
    private fun handleUrlExpired(songId: Long, currentIndex: Int) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "PlayerViewModel handleUrlExpired: songId=$songId, index=$currentIndex"
        )

        viewModelScope.launch(Dispatchers.Main) {
            try {
                // 使用 HandleUrlExpiredUseCase 处理 URL 失效
                val playlist = playbackState.value.playlist
                val result = handleUrlExpiredUseCase(playlist, songId, currentIndex)

                when (result) {
                    is HandleUrlExpiredUseCase.HandleResult.Success -> {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel URL失效已修复: ${result.updatedSong.title}"
                        )
                        // 调用 PlaybackController 更新并继续播放
                        playbackController.updateSongAndSeek(result.index, result.updatedSong)
                    }

                    is HandleUrlExpiredUseCase.HandleResult.Error -> {
                        LogConfig.e(
                            LogConfig.TAG_PLAYER_VIEWMODEL,
                            "PlayerViewModel URL失效处理失败: ${result.message}"
                        )
                        showError(result.message)
                        // URL获取失败，跳到下一首
                        skipToNext()
                    }
                }
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "PlayerViewModel URL失效处理异常: ${e.javaClass.simpleName}: ${e.message}"
                )
                showError("播放错误：${e.message}")
                skipToNext()
            }
        }
    }
}