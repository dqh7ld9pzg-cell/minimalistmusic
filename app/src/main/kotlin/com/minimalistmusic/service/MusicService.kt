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

package com.minimalistmusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.minimalistmusic.R
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.cache.OptimizationLevel
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.domain.model.PlayMode
import com.minimalistmusic.domain.model.PlaybackState
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.usecase.player.HandlePlayerErrorUseCase
import com.minimalistmusic.domain.usecase.player.PlayNextSongUseCase
import com.minimalistmusic.domain.usecase.player.PrepareSongUseCase
import com.minimalistmusic.service.state.PlaybackStateManager
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


/**
 * 音乐播放服务 - 应用核心播放引擎
 *
 * 架构说明:
 * - 继承 MediaSessionService，提供系统级媒体控制支持
 * - 使用 ExoPlayer 作为音频播放引擎
 * - 使用 MediaSession 提供通知栏和蓝牙控制
 *
 * 生命周期:
 * 1. onCreate() - 初始化播放器、创建MediaSession、启动前台服务
 * 2. onBind() - 绑定服务，返回Binder供Activity控制
 * 3. onStartCommand() - 处理通知栏按钮点击事件
 * 4. onDestroy() - 释放资源
 *
 * 状态管理:
 * - 封装 PlaybackStateManager对播放状态进行统一管理
 * - 通过 getPlaybackState() 向外暴露播放状态（PlaybackState）
 *
 * 播放模式:
 * - SEQUENCE: 顺序播放
 * - SHUFFLE: 随机播放
 * - REPEAT_ONE: 单曲循环
 *
 * @see MediaSessionService 系统媒体会话服务基类
 * @see ExoPlayer Google官方音频播放引擎
 * @see MediaSession 媒体会话，用于处理媒体按钮和通知栏控制
 */
@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    // region 依赖注入实例
    /**
     * 播放状态管理器 (重构 2025-12-01)
     *
     * 功能:
     * - 维护全局播放状态（Single Source of Truth）
     * - 提供线程安全的状态更新
     * - 暴露响应式状态流供 UI 层订阅
     */
    @Inject
    lateinit var playbackStateManager: PlaybackStateManager

    /**
     * 音频缓存管理器
     *
     * 功能:
     * - 提供ExoPlayer的缓存数据源
     * - 自动缓存播放过的音频（最大2GB）
     * - 完整缓存过的音频在下次播放时直接从缓存读取，无需网络请求
     */
    @Inject
    lateinit var audioCacheManager: AudioCacheManager

    /**
     * 本地音乐仓库 (新增 2025-11-19)
     *
     * 功能:
     * - 访问cached_songs表,删除失效的缓存记录
     * - 确保播放异常时同步删除数据库记录和缓存文件
     */
    @Inject
    lateinit var musicLocalRepository: MusicLocalRepository

    /**
     * 用户偏好设置 (新增 2025-11-19)
     *
     * 功能:
     * - 读取WiFi快速缓存开关设置
     * - 根据设置动态配置ExoPlayer的LoadControl
     */
    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    /**
     * 缓存状态管理器
     *
     * 功能:
     * - 管理缓存进度状态
     * - 切歌时清理旧进度
     */
    @Inject
    lateinit var cacheStateManager: com.minimalistmusic.domain.cache.CacheStateManager
    // endregion

    // region 相关的usecase
    /**
     * 播放下一首歌曲用例 (新增 2025-11-22)
     *
     * 功能:
     * - Service 通过 UseCase 获取下一首歌曲
     * - 不再依赖 ViewModel 的回调
     * - 解决 ViewModel 生命周期导致的自动切换失败问题
     */
    @Inject
    lateinit var playNextSongUseCase: PlayNextSongUseCase

    /**
     * 准备歌曲用例 (新增 2025-11-22)
     *
     * 功能:
     * - 插入播放记录 + 获取URL
     * - 用于 skipToPrevious/skipToNext（它们已经自己计算了索引）
     */
    @Inject
    lateinit var prepareSongUseCase: PrepareSongUseCase

    /**
     * 播放错误处理用例 (重构 2025-12-01)
     *
     * 功能:
     * - 解析播放错误类型
     * - 显示用户友好提示
     * - 清理失效的缓存碎片
     * - 清除失效的URL
     * - 通知需要重新获取URL
     */
    @Inject
    lateinit var handlePlayerErrorUseCase: HandlePlayerErrorUseCase
    // endregion

    // region 服务生命周期相关
    // 服务协程作用域: SupervisorJob: 子协程失败不影响其他协程，Dispatchers.Main: 在主线程执行，方便更新UI状态
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ExoPlayer实例 - 音频播放引擎
    private var player: ExoPlayer? = null

    // MediaSession实例 - 用于媒体控制和通知栏
    private var mediaSession: MediaSession? = null

    // 包装后的Player - 用于MediaSession (2025-11-18)
    private var wrappedPlayer: Player? = null

    /** Binder实例 - 用于Activity绑定服务 */
    private val binder = MusicBinder()

    /** 错误状态标记 - 用于记录播放器是否处于错误状态 (2025-11-18) */
    private var isInErrorState = false

    /** 上次播放失败的歌曲索引 - 避免重复尝试相同的失败歌曲 (2025-11-18) */
    private var lastFailedSongIndex = -1

    /** 防抖相关 (2025-11-18) */
    private var lastSkipTime = 0L
    private val skipDebounceDelay = 500L  // 500ms防抖延迟

    /** 耳机线控双击检测相关 (2025-11-19) */
    private var lastHeadsetHookTime = 0L
    private var headsetHookClickCount = 0
    private val headsetHookHandler = Handler(Looper.getMainLooper())

    /** 音频输出设备监听器 (2025-11-21)
     * 用于诊断和修复音频路由问题
     * - 监听耳机/蓝牙设备的连接/断开
     * - 检测AudioManager状态异常
     * - 自动恢复音频输出
     */
    private var audioDeviceReceiver: BroadcastReceiver? = null
    /** 歌曲播放结束监听器 (2025-11-21)
     *
     * 架构重构 (2025-11-22): 已废弃
     * - 之前：通过回调通知 ViewModel 处理歌曲结束
     * - 现在：Service 直接使用 UseCase 处理，不依赖 ViewModel
     * - 保留代码以兼容旧版本，但不再使用
     *
     * @deprecated 使用 PlayNextSongUseCase 替代
     */

    /**
     * URL失效监听器 (2025-11-22)
     *
     * 当检测到403错误（URL失效）时，通知ViewModel重新获取URL
     * 架构优势：
     * - Service层不直接调用Repository
     * - ViewModel负责业务逻辑（获取URL）
     * - Service只负责播放控制
     *
     * @param (songId: Long, currentIndex: Int)：songId 需要重新获取URL的歌曲ID，currentIndex 歌曲在播放列表中的索引
     */
    private var onUrlExpiredListener: ((songId: Long, currentIndex: Int) -> Unit)? = null
    private val headsetHookRunnable = Runnable {
        // 延迟后执行，根据点击次数决定操作
        when (headsetHookClickCount) {
            1 -> {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService 耳机线控: 单击 -> 播放/暂停"
                )
                togglePlayPause()
            }

            2 -> {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService 耳机线控: 双击 -> 下一首"
                )
                skipToNext()
            }

            3 -> {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService 耳机线控: 三击 -> 上一首"
                )
                skipToPrevious()
            }
        }
        headsetHookClickCount = 0
    }
    private val headsetHookDoubleClickDelay = 400L  // 双击检测间隔
    // endregionbi变量

    /**
     * 服务Binder - 提供服务实例给绑定的Activity
     *
     * 使用场景:
     * - Activity通过bindService()绑定服务后，可调用getService()获取MusicService实例
     * - 从而直接调用播放控制方法（play/pause/skip等）
     */
    inner class MusicBinder : Binder() {
        /** 获取MusicService实例 */
        fun getService(): MusicService = this@MusicService
    }

    companion object {
        /** 前台服务通知ID */
        const val NOTIFICATION_ID = 1001

        /** 通知渠道ID - 用于Android 8.0+的通知分类 */
        const val CHANNEL_ID = "music_playback_channel"
    }

    // ============ 生命周期方法 ============
    /**
     * 服务创建时调用 - 初始化播放器和MediaSession
     *
     * 执行流程:
     * 1. 创建通知渠道（Android 8.0+必需）
     * 2. 初始化 ExoPlayer 并设置监听器
     * 3. 创建 MediaSession
     * 4. 启动前台服务（显示通知栏）
     * 5. 开启播放进度更新循环
     *
     * 注意事项:
     * - 必须调用 startForeground() 避免服务被系统杀死
     * - ExoPlayer的监听器会自动处理播放状态变化
     */
    override fun onCreate() {
        super.onCreate()
        initExoPlayer()
        createNotificationChannel()
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        // 开启播放进度更新循环
        startPositionUpdateLoop()
        // 注册音频设备监听器
        registerAudioDeviceListener()
        // 监听缓存开关变化，动态重建ExoPlayer
        observeCacheSettingsChanges()
    }

    /**
     * 监听缓存设置变化
     *
     * 当用户切换缓存开关或快速缓存设置时，需要重建ExoPlayer以应用新的DataSource配置
     * 重建时会保留当前播放状态和位置
     */
    private fun observeCacheSettingsChanges() {
        serviceScope.launch {
            // 监听缓存总开关
            userPreferencesDataStore.cacheEnabled
                .drop(1) // 跳过初始值，避免onCreate时触发重建
                .collect { enabled ->
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "MusicService 检测到缓存开关变化: $enabled，准备重建ExoPlayer"
                    )
                    rebuildExoPlayerIfNeeded()
                }
        }

        serviceScope.launch {
            // 监听WiFi快速缓存开关
            userPreferencesDataStore.fastCacheOnWiFi
                .drop(1) // 跳过初始值
                .collect {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "MusicService 检测到WiFi快速缓存开关变化，准备重建ExoPlayer"
                    )
                    rebuildExoPlayerIfNeeded()
                }
        }

        serviceScope.launch {
            // 监听移动网络快速缓存开关
            userPreferencesDataStore.fastCacheOnMobile
                .drop(1) // 跳过初始值
                .collect {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "MusicService 检测到移动网络快速缓存开关变化，准备重建ExoPlayer"
                    )
                    rebuildExoPlayerIfNeeded()
                }
        }
    }

    /**
     * 重建ExoPlayer以应用新的缓存策略
     *
     * 保留当前播放状态：
     * - 播放列表
     * - 当前播放位置
     * - 播放暂停状态
     */
    private fun rebuildExoPlayerIfNeeded() {
        val currentPlayer = player ?: return
        val currentSession = mediaSession ?: return

        // 防御性检查：如果播放列表为空，跳过重建
        val itemCount = currentPlayer.mediaItemCount
        if (itemCount == 0) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService rebuildExoPlayerIfNeeded: 播放列表为空，跳过重建"
            )
            return
        }

        // 保存当前状态
        val wasPlaying = currentPlayer.isPlaying
        val currentPosition = currentPlayer.currentPosition
        val currentIndex = currentPlayer.currentMediaItemIndex
        val playbackState = currentPlayer.playbackState

        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "MusicService rebuildExoPlayerIfNeeded: " +
                "mediaItemCount=$itemCount, " +
                "currentIndex=$currentIndex, " +
                "position=$currentPosition, " +
                "playbackState=$playbackState, " +
                "isPlaying=$wasPlaying"
        )

        // 保存播放列表（添加异常处理）
        val mediaItems = mutableListOf<MediaItem>()
        try {
            for (i in 0 until itemCount) {
                mediaItems.add(currentPlayer.getMediaItemAt(i))
            }
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService rebuildExoPlayerIfNeeded: 保存播放列表失败: ${e.message}"
            )
            return
        }

        // 二次验证
        if (mediaItems.isEmpty()) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService rebuildExoPlayerIfNeeded: 保存的播放列表为空，跳过重建"
            )
            return
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "MusicService rebuildExoPlayerIfNeeded: 成功保存${mediaItems.size}首歌曲，开始重建"
        )

        // 释放旧的 MediaSession 和 ExoPlayer
        currentSession.release()
        mediaSession = null
        currentPlayer.stop()
        currentPlayer.release()
        player = null

        // 重新初始化ExoPlayer和MediaSession
        initExoPlayer()

        // 恢复播放列表和状态
        val newPlayer = player ?: run {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService rebuildExoPlayerIfNeeded: 重新初始化ExoPlayer失败"
            )
            return
        }

        try {
            newPlayer.setMediaItems(mediaItems, currentIndex, currentPosition)
            newPlayer.prepare()
            if (wasPlaying) {
                newPlayer.play()
            }
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService rebuildExoPlayerIfNeeded: 重建成功，已恢复播放列表和状态"
            )
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService rebuildExoPlayerIfNeeded: 恢复播放失败: ${e.message}"
            )
        }
    }

    /**
     * 处理通知栏操作
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            "play_pause" -> togglePlayPause()
            "previous" -> skipToPrevious()
            "next" -> skipToNext()
        }
        return START_STICKY
    }

    /**
     * 服务销毁时调用 - 释放所有资源
     *
     * 释放顺序:
     * 1. 释放ExoPlayer（停止播放并释放音频焦点）
     * 2. 释放MediaSession（注销媒体按钮监听）
     * 3. 音频缓存资源（SimpleCache），置空引用（防止内存泄漏）
     *
     */
    override fun onDestroy() {
        serviceScope.cancel()
        headsetHookHandler.removeCallbacks(headsetHookRunnable)

        // 注销音频设备监听器 (2025-11-21)
        audioDeviceReceiver?.let {
            try {
                unregisterReceiver(it)
                LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "MusicService 音频设备监听器已注销")
            } catch (e: Exception) {
                LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "注销音频设备监听器失败: ${e.message}")
            }
            audioDeviceReceiver = null
        }
        player?.release()
        mediaSession?.release()
        audioCacheManager.release() // 释放缓存资源
        player = null
        mediaSession = null
        super.onDestroy()
    }

    private fun initExoPlayer() {
        /**
         * 初始化ExoPlayer并设置监听器
         * 音频焦点管理 (2025-11-19):
         * - 设置音频属性为音乐类型
         * - 启用自动音频焦点管理
         * - 当其他应用获取焦点时自动暂停，恢复焦点后自动继续播放
         */
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 根据缓存总开关和网络类型选择策略
        // 策略逻辑：
        // 1. 总开关关闭时使用只读模式
        // 2. 总开关开启时根据网络类型选择策略
        //    - WiFi网络且开启快速缓存时使用高速模式
        //    - 移动网络且开启快速缓存时使用高速模式
        //    - 其他情况使用标准模式
        val cacheEnabled = userPreferencesDataStore.cacheEnabled.value

        // 关键修复：一次性读取所有开关状态，确保LoadControl和DataSourceFactory使用一致的值
        val networkType = getNetworkType()
        val useOptimized = if (cacheEnabled) {
            when (networkType) {
                NetworkType.WIFI -> userPreferencesDataStore.fastCacheOnWiFi.value
                NetworkType.MOBILE -> userPreferencesDataStore.fastCacheOnMobile.value
                NetworkType.NONE, NetworkType.OTHER -> false
            }
        } else {
            false
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "MusicService initExoPlayer: " +
                "缓存总开关=$cacheEnabled, " +
                "网络类型=$networkType, " +
                "fastCacheOnWiFi=${userPreferencesDataStore.fastCacheOnWiFi.value}, " +
                "fastCacheOnMobile=${userPreferencesDataStore.fastCacheOnMobile.value}, " +
                "useOptimized=$useOptimized"
        )

        val loadControl = if (!cacheEnabled) {
            DefaultLoadControl.Builder().build()
        } else {
            if (useOptimized) {
                // 优化策略：激进缓存
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        50_000,   // 最小缓冲10秒（减少启动延迟）
                        600_000,  // 最大缓冲10分钟（更积极缓存）
                        500,     // seek后缓冲0.5秒后开始播放（快速响应）
                        3_000     // 播放中卡住后恢复3秒（减少等待）
                    )
                    .setTargetBufferBytes(30 * 1024 * 1024)  // 目标缓冲30MB（更大缓冲）
                    .setPrioritizeTimeOverSizeThresholds(false)  // 优先按大小而非时长
                    .setAllocator(DefaultAllocator(true, 64 * 1024))  // 使用更大的内存块
                    .build()
            } else {
                // 默认策略：ExoPlayer标准配置
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        50_000,   // 最小缓冲50秒（减少启动延迟）
                        60_000,  // 最大缓冲1分钟（更积极缓存）
                        500,     // seek后缓冲0.5秒后开始播放（快速响应）
                        3_000     // 播放中卡住后恢复3秒（减少等待）
                    )
                    .setPrioritizeTimeOverSizeThresholds(false)  // 优先按大小而非时长
                    .build()
            }
        }

        val cacheDataSourceFactory = if (!cacheEnabled) {
            // 总开关关闭时使用只读模式缓存
            //
            // 关键设计：
            // - 允许读取已有缓存避免播放已缓存歌曲出错
            // - 不写入新缓存不持久化新歌曲
            // - 不显示已缓存音乐UI由业务层控制
            // - 不记录新的缓存到数据库由业务层控制
            //
            // 为什么不能用 DefaultDataSource：
            // - DefaultDataSource 无法读取 SimpleCache
            // - 播放已缓存歌曲时会从网络重新下载
            // - URL 可能已过期导致播放失败
            // - 误触发URL失效逻辑删除有效缓存
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService initExoPlayer: 缓存总开关已关闭，使用只读模式"
            )
            audioCacheManager.createReadOnlyCacheDataSourceFactory()
        } else {
            // 总开关开启时，使用前面统一读取的 useOptimized 值
            if (useOptimized) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService initExoPlayer: 使用高速缓存策略"
                )
                audioCacheManager.createOptimizeCacheDataSourceFactory()
            } else {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService initExoPlayer: 使用标准缓存策略"
                )
                audioCacheManager.createCacheDataSourceFactory()
            }
        }

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)  // 应用LoadControl配置
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    cacheDataSourceFactory  // 使用策略系统选择的DataSource.Factory
                )
            )
            .setAudioAttributes(audioAttributes, true)  // true = 自动处理音频焦点
            .setHandleAudioBecomingNoisy(true)  // 耳机拔出时自动暂停
            .build().apply {
                addListener(object : Player.Listener {
                    /**
                     * 播放状态改变回调
                     * @param playbackState Player.STATE_IDLE/BUFFERING/READY/ENDED
                     *
                     * 修复 (2025-11-20): 不在这里调用updatePlaybackState()
                     * 原因：会与onIsPlayingChanged重复更新，导致闪烁
                     * 播放状态的更新已经由onIsPlayingChanged处理
                     */
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateStr = when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "MusicService播放状态变化: $stateStr, 当前URL: ${player?.currentMediaItem?.localConfiguration?.uri}"
                        )
                        // 当前歌曲播放结束，自动播放下一曲
                        // 架构重构 (2025-11-22): 使用 UseCase 替代 ViewModel 回调
                        // 优势:
                        // - Service 不再依赖 ViewModel 生命周期
                        // - 后台播放时自动切换完全正常
                        // - 业务逻辑集中在 UseCase，易于测试
                        when (playbackState) {
                            Player.STATE_ENDED -> {
                                val currentPlayMode = playbackStateManager.currentState.playMode
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 歌曲播放结束，当前模式: $currentPlayMode"
                                )
                                when (currentPlayMode) {
                                    PlayMode.REPEAT_ONE -> {
                                        // 单曲循环: REPEAT_MODE_ONE会自动重播，不需要手动操作
                                        LogConfig.d(
                                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                                            "MusicService 单曲循环结束，ExoPlayer自动重播"
                                        )
                                    }

                                    else -> {
                                        // 顺序播放和随机播放: Service 直接通过 UseCase 处理
                                        LogConfig.d(
                                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                                            "MusicService 使用 UseCase 自动播放下一首"
                                        )
                                        handlePlayNext()
                                    }
                                }
                            }

                            Player.STATE_READY -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 歌曲准备完成"
                                )
                                // 清除错误状态 (2025-11-18)
                                if (isInErrorState) {
                                    LogConfig.d(
                                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                                        "MusicService 从错误状态恢复"
                                    )
                                    isInErrorState = false
                                    lastFailedSongIndex = -1
                                }
                            }

                            else -> {
                                // Do nothing
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 歌曲播放结束，当前playbackState: $playbackState"
                                )
                            }
                        }
                    }

                    /**
                     * 播放准备状态改变回调 (2025-11-21)
                     *
                     * 用于诊断音频焦点问题:
                     * - 监听耳机拔出事件 (AUDIO_BECOMING_NOISY)
                     * - 监听音频焦点丢失 (AUDIO_FOCUS_LOSS)
                     * - 帮助定位"拔出耳机后无声音"的问题
                     *
                     * @param playWhenReady 是否准备播放
                     * @param reason 状态改变原因
                     */
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        val reasonStr = when (reason) {
                            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "用户请求"
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "音频焦点丢失"
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "耳机拔出"
                            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "远程控制"
                            else -> "未知原因($reason)"
                        }
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "MusicService 播放准备状态变化: playWhenReady=$playWhenReady, 原因=$reasonStr"
                        )
                        // 重要: 当因为耳机拔出或焦点丢失而暂停时，在恢复焦点或插入耳机后可以自动继续播放
                        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                            LogConfig.d(
                                LogConfig.TAG_PLAYER_DATA_LOCAL,
                                "MusicService 耳机拔出导致暂停"
                            )
                        }
                    }

                    /**
                     * 播放/暂停状态改变回调
                     * @param isPlaying true=正在播放，false=已暂停
                     *
                     */
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlaybackState()
                        updateNotification() // 更新通知栏按钮状态
                    }

                    /**
                     * 歌曲切换回调
                     * @param mediaItem 新的媒体项
                     * @param reason 切换原因（用户点击/自动播放/等）
                     *
                     * 修复 (2025-11-20): 只更新歌曲信息，不更新播放状态
                     * 原因：播放状态会由onIsPlayingChanged自动更新
                     * 避免重复调用updatePlaybackState()导致闪烁
                     */
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateCurrentSong()
                        // 移除 updatePlaybackState()，避免与onIsPlayingChanged重复
                        updateNotification() // 更新通知栏歌曲信息
                    }

                    /**
                     * 播放错误回调 (2025-11-13, 重构 2025-12-01)
                     *
                     * 添加原因：
                     * - 用户反馈点击歌曲后只显示歌词但不播放
                     * - 日志显示播放状态从 BUFFERING 直接跳到 IDLE
                     * - 缺少错误监听导致无法定位播放失败原因
                     *
                     * 重构 (2025-12-01):
                     * - 提取错误处理逻辑到 HandlePlayerErrorUseCase
                     * - Service 只负责标记错误状态和委托处理
                     * - 从 223 行降至 ~20 行
                     *
                     * @param error PlaybackException 播放错误对象
                     */
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)

                        // 标记错误状态 (2025-11-18)
                        isInErrorState = true
                        lastFailedSongIndex = player?.currentMediaItemIndex ?: -1

                        // 重构 (2025-12-01): 委托给 HandlePlayerErrorUseCase 处理
                        // 架构优势：
                        // - 职责分离：Service 只负责标记错误状态
                        // - 业务逻辑：错误处理逻辑在 UseCase 层
                        // - 代码简洁：从 223 行降至 ~15 行
                        val currentUrl =
                            player?.currentMediaItem?.localConfiguration?.uri?.toString()
                        serviceScope.launch {
                            handlePlayerErrorUseCase(
                                error = error,
                                currentUrl = currentUrl,
                                onUrlExpired = { songId, index ->
                                    // 通知 ViewModel 重新获取 URL
                                    onUrlExpiredListener?.invoke(songId, index)
                                }
                            )
                        }
                    }
                })
            }

        // 包装ExoPlayer以拦截媒体按钮事件 (2025-11-18)
        // 用于应用防抖和自动播放逻辑
        wrappedPlayer = object : ForwardingPlayer(player!!), Player {
            override fun seekToNext() {
                // 调用MusicService的skipToNext()以应用防抖和自动播放逻辑
                this@MusicService.skipToNext()
            }

            override fun seekToPrevious() {
                // 调用MusicService的skipToPrevious()以应用防抖和自动播放逻辑
                this@MusicService.skipToPrevious()
            }

            override fun seekToNextMediaItem() {
                // 调用MusicService的skipToNext()以应用防抖和自动播放逻辑
                this@MusicService.skipToNext()
            }

            override fun seekToPreviousMediaItem() {
                // 调用MusicService的skipToPrevious()以应用防抖和自动播放逻辑
                this@MusicService.skipToPrevious()
            }
        }
        // 创建MediaSession（用于媒体按钮和通知栏控制）
        // 使用包装后的Player以拦截媒体控制事件
        // 修复 (2025-11-19): 添加MediaSession.Callback处理耳机按键事件
        mediaSession = MediaSession.Builder(this, wrappedPlayer!!)
            .setCallback(object : MediaSession.Callback {
                // 处理媒体按钮事件（耳机线控）
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent,
                ): Boolean {
                    val keyEvent =
                        intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT,
                            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                                -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 耳机线控: 下一首"
                                )
                                skipToNext()
                                return true
                            }

                            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                                -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 耳机线控: 上一首"
                                )
                                skipToPrevious()
                                return true
                            }

                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 耳机线控: 播放/暂停"
                                )
                                togglePlayPause()
                                return true
                            }

                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                // 耳机中间按钮：使用双击检测
                                // 单击: 播放/暂停，双击: 下一首，三击: 上一首
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastHeadsetHookTime < headsetHookDoubleClickDelay) {
                                    // 在双击间隔内，增加点击计数
                                    headsetHookClickCount++
                                    headsetHookHandler.removeCallbacks(headsetHookRunnable)
                                } else {
                                    // 超过间隔，重新计数
                                    headsetHookClickCount = 1
                                }
                                lastHeadsetHookTime = currentTime
                                // 延迟执行，等待可能的后续点击
                                headsetHookHandler.postDelayed(
                                    headsetHookRunnable,
                                    headsetHookDoubleClickDelay
                                )
                                return true
                            }

                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 耳机线控: 播放"
                                )
                                play()
                                return true
                            }

                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 耳机线控: 暂停"
                                )
                                pause()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()
    }
    /**
     * 注册音频设备监听器 (2025-11-21)
     *
     * 监听以下广播事件:
     * - ACTION_HEADSET_PLUG: 有线耳机插拔
     * - ACTION_AUDIO_BECOMING_NOISY: 音频输出变为"吵闹"（耳机拔出）
     * - ACTION_SCO_AUDIO_STATE_UPDATED: 蓝牙SCO音频状态变化
     *
     * 目的:
     * - 诊断音频路由问题的根本原因
     * - 记录详细的设备状态日志
     * - 在需要时自动恢复音频输出
     */
    /**
     * 获取当前网络类型
     *
     * 用于根据网络环境选择合适的缓存策略
     *
     * @return NetworkType 枚举值
     */
    private fun getNetworkType(): NetworkType {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.OTHER
            else -> NetworkType.OTHER
        }
    }

    private fun registerAudioDeviceListener() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        audioDeviceReceiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        val name = intent.getStringExtra("name") ?: "未知设备"
                        val microphone = intent.getIntExtra("microphone", -1)
                        when (state) {
                            0 -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 音频设备: 耳机已拔出 (设备=$name, 有麦克风=${microphone == 1})"
                                )
                                // ExoPlayer会自动暂停，这里只记录日志
                            }

                            1 -> {
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                                    "MusicService 音频设备: 耳机已插入 (设备=$name, 有麦克风=${microphone == 1})"
                                )
                                // 检查音频输出状态
                                checkAndLogAudioState()
                            }
                        }
                    }

                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "MusicService 音频设备: 收到AUDIO_BECOMING_NOISY广播"
                        )
                        // ExoPlayer已通过setHandleAudioBecomingNoisy(true)自动处理
                    }

                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        val state =
                            intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                        val stateStr = when (state) {
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "断开"
                            AudioManager.SCO_AUDIO_STATE_CONNECTING -> "连接中"
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> "已连接"
                            else -> "未知($state)"
                        }
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "MusicService 音频设备: 蓝牙SCO音频状态=$stateStr"
                        )
                    }
                }
            }
        }
        registerReceiver(audioDeviceReceiver, filter)
        LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "MusicService 音频设备监听器已注册")
    }

    /**
     * 检查并记录当前音频状态 (2025-11-21)
     *
     * 用于诊断音频路由问题:
     * - 获取AudioManager状态
     * - 检查音频输出设备
     * - 记录详细日志帮助定位问题
     */
    private fun checkAndLogAudioState() {
        try {
            val audioManager =
                getSystemService(AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val mode = audioManager.mode
                val modeStr = when (mode) {
                    AudioManager.MODE_NORMAL -> "NORMAL"
                    AudioManager.MODE_RINGTONE -> "RINGTONE"
                    AudioManager.MODE_IN_CALL -> "IN_CALL"
                    AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
                    else -> "UNKNOWN($mode)"
                }
                val isSpeakerOn = audioManager.isSpeakerphoneOn
                val isWiredHeadsetOn = audioManager.isWiredHeadsetOn
                val isMusicActive = audioManager.isMusicActive
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService 音频状态检查: mode=$modeStr, speaker=$isSpeakerOn, " +
                            "wiredHeadset=$isWiredHeadsetOn, musicActive=$isMusicActive"
                )
            }
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService 音频状态检查失败: ${e.message}"
            )
        }
    }

    /**
     * 服务绑定时调用
     *
     * @param intent 绑定Intent
     * @return MusicBinder实例，用于Activity获取服务对象
     */
    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    /**
     * 获取MediaSession实例 - 由MediaSessionService调用
     *
     * @param controllerInfo 控制器信息（系统媒体按钮/蓝牙设备等）
     * @return MediaSession实例
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示当前播放的音乐"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val state = playbackStateManager.currentState
        val song = state.currentSong
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Bug修复 (2025-11-15): 加载应用图标作为通知栏左侧大图标
        // - setLargeIcon(): 通知栏左侧的大图标（显示应用图标）
        // - setSmallIcon(): 状态栏和通知栏右上角的小图标（单色音符）
        //
        // 关键修复 (2025-11-15): 强制使用最高分辨率图标（xxxhdpi: 192x192）
        // 问题原因：BitmapFactory.decodeResource() 会根据设备密度自动选择图标
        //          在低密度设备上可能选择48x48的图标，拉伸后模糊
        // 解决方案：使用 Options.inDensity 强制从xxxhdpi加载192x192图标
        //          确保在所有设备上都清晰显示
        val options = BitmapFactory.Options().apply {
            inDensity = DisplayMetrics.DENSITY_XXXHIGH  // 强制使用xxxhdpi (192x192)
            inTargetDensity = resources.displayMetrics.densityDpi    // 目标设备密度
        }
        val appIcon = BitmapFactory.decodeResource(
            resources,
            R.mipmap.ic_minimalist_logo,
            options
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "MinimalistMusic")
            .setContentText(song?.artist ?: "暂无播放")
            // 左侧大图标：显示应用图标
            .setLargeIcon(appIcon)
            // 右上角小图标：单色音符图标
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentIntent(pendingIntent)
            .setOngoing(state.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            // Bug修复 (2025-11-15): 使用与播放器界面一致的Material Design图标
            // - 替换系统图标为自定义drawable，提升视觉一致性
            // - 图标与应用播放器界面的Icons.Filled图标完全一致
            .addAction(R.drawable.ic_skip_previous_white, "上一曲", createMediaAction("previous"))
            .addAction(
                if (state.isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white,
                if (state.isPlaying) "暂停" else "播放",
                createMediaAction("play_pause")
            )
            .addAction(R.drawable.ic_skip_next_white, "下一曲", createMediaAction("next"))
            .build()
    }

    private fun createMediaAction(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun updatePlaybackState() {
        player?.let { p ->
            // 修复 (2025-11-19): 使用playWhenReady而非isPlaying来判断播放状态
            // 原因: isPlaying在buffering期间为false，导致UI闪烁
            // playWhenReady表示用户意图（想要播放），在buffering期间也为true
            // 结合playbackState判断：READY或BUFFERING状态下，playWhenReady为true即视为播放中
            // 重构 (2025-12-01): 使用 PlaybackStateManager 更新状态
            playbackStateManager.updateFromPlayer(p)
        }
    }

    private fun startPositionUpdateLoop() {
        serviceScope.launch {
            while (true) {
                if (player?.isPlaying == true) {
                    updatePlaybackState()
                }
                delay(500)
            }
        }
    }

    // ============ 公开的播放控制方法 ============

    /**
     * 获取播放状态流 (重构 2025-12-01)
     *
     * 用于 ViewModel 和 UI 层订阅播放状态
     *
     * @return 播放状态 StateFlow
     */
    fun getPlaybackState(): StateFlow<PlaybackState> = playbackStateManager.playbackState

    /**
     * 播放歌曲列表
     *
     * 执行流程:
     * 1. 清空当前播放队列
     * 2. 将歌曲列表转换为ExoPlayer的MediaItem
     * 3. 设置播放列表并准备播放
     * 4. 从指定索引开始播放
     * 5. 更新全局播放状态
     *
     * @param songs 歌曲列表
     * @param startIndex 起始播放索引，默认从第一首开始
     *
     * 注意事项:
     * - 如果歌曲没有有效的播放路径（path为null），会被自动过滤
     * - 如果所有歌曲都无效，方法会直接返回不执行播放
     */
    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        player?.let { p ->
            // 清除错误状态（2025-11-18）
            if (isInErrorState) {
                LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "MusicService playSongs: 清除错误状态")
                isInErrorState = false
                lastFailedSongIndex = -1
            }
            // 修复 (2025-11-20): 遵循ExoPlayer最佳实践，避免duration闪烁
            // 关键原则：
            // 1. 不调用stop()，因为stop()会重置duration为0，导致UI闪烁
            // 2. 先设置媒体项，再prepare，最后才更新StateFlow
            // 3. 这样UI收到通知时，ExoPlayer已经有正确的duration
            // 4. 构建新的媒体项目列表
            // 修复 (2025-11-21): 只播放当前歌曲，不创建完整的MediaItem列表
            // 原因：其他歌曲URL未获取，过滤后会导致索引不匹配
            // 策略：只创建当前歌曲的MediaItem，通过seekToSong切换时重新调用playSongs
            val targetSong = songs.getOrNull(startIndex)
            if (targetSong == null || targetSong.path.isNullOrEmpty()) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService playSongs: 目标歌曲URL为空，无法播放"
                )
                // 重构 (2025-12-01): 使用 PlaybackStateManager 更新状态
                playbackStateManager.updatePlaylist(songs, startIndex, targetSong)
                return
            }
            // 只创建当前歌曲的MediaItem
            val mediaItems = listOf(
                MediaItem.Builder()
                    .setMediaId(targetSong.id.toString())
                    .setUri(targetSong.path)
                    .build()
            )
            // 实际索引始终是0（因为只有一首歌）
            val actualStartIndex = 0
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService playSongs: 原始索引=$startIndex, 实际索引=$actualStartIndex, 总歌曲=${songs.size}, 有效歌曲=${mediaItems.size}"
            )
            // 2. 设置播放模式
            // 修复 (2025-11-21): 顺序播放使用REPEAT_MODE_OFF + 手动循环
            // 问题: REPEAT_MODE_ALL会导致ExoPlayer自动循环,与手动切换逻辑冲突
            // 解决: 使用REPEAT_MODE_OFF,在STATE_ENDED时手动判断是否循环
            val currentPlayMode = playbackStateManager.currentState.playMode
            p.repeatMode = when (currentPlayMode) {
                PlayMode.SEQUENCE -> Player.REPEAT_MODE_OFF
                PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF
                PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            }
            // 3. 设置播放列表并准备播放（ExoPlayer最佳实践）
            // setMediaItems() 会自动调用 stop() 和 clearMediaItems()
            // 但我们不手动调用stop()，避免触发额外的状态变化
            // 修复 (2025-11-21): 使用已声明的targetSong变量，避免重复播放正在播放的歌曲
            if (playbackStateManager.currentState.isPlaying && (targetSong?.id == playbackStateManager.currentState.currentSong?.id)) return
            p.setMediaItems(mediaItems, actualStartIndex, 0)
            // 4. 立即更新播放列表、currentSong和currentIndex
            // 修复 (2025-11-21): 使用原始startIndex而不是actualStartIndex
            // 原因：只播放当前歌曲，ExoPlayer索引始终是0，但逻辑索引应该是完整列表中的位置
            // 重构 (2025-12-01): 使用 PlaybackStateManager 更新状态
            playbackStateManager.updatePlaylist(songs, startIndex, targetSong)

            // 清理非当前歌曲的缓存进度（切歌时清理旧进度）
            cacheStateManager.clearCacheProgressExcept(targetSong.id)

            // 5. prepare并播放
            // prepare()会触发onMediaItemTransition→updateCurrentSong()→再次确认currentSong
            // play()会触发onIsPlayingChanged→updatePlaybackState()→更新isPlaying
            p.prepare()
            p.play()
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService playSongs: 成功设置播放列表，歌曲总数=${songs.size}, 当前歌曲=${targetSong?.title ?: "未知"}"
            )
        }
    }

    /**
     * 切换播放/暂停状态
     *
     * 使用场景:
     * - 用户点击播放按钮
     * - 通知栏播放按钮点击
     * - 蓝牙耳机按键
     *
     * 错误恢复 (2025-11-18):
     * - 检测到错误状态时，自动重新prepare并播放
     * - 这样网络恢复后用户点击播放按钮就能继续播放
     *
     * 播放状态记录 (2025-11-18):
     * - 记录暂停前的播放状态，用于skipToNext/skipToPrevious恢复播放
     */
    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                // 错误恢复：如果处于错误状态，重新prepare
                if (isInErrorState) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "MusicService togglePlayPause: 检测到错误状态，尝试恢复播放"
                    )
                    p.prepare()
                }
                p.play()
            }
        }
    }

    /**
     * 播放 - 直接调用ExoPlayer的play()
     *
     * 注意: 如果没有加载任何歌曲，调用此方法无效
     */
    fun play() {
        player?.play()
    }

    /**
     * 暂停 - 直接调用ExoPlayer的pause()
     */
    fun pause() {
        player?.pause()
    }

    /**
     * 播放上一曲
     *
     * 架构重构 (2025-11-22): 使用 PlayPreviousSongUseCase
     * - 之前：直接操作 ExoPlayer 的 MediaItem（在单曲播放模式下失效）
     * - 现在：计算上一首索引，使用 UseCase 获取 URL 并播放
     * - 优势：逻辑统一，通知栏/界面/自动播放都使用相同机制
     *
     * 防抖与播放恢复优化 (2025-11-18):
     * - 添加500ms防抖，避免频繁点击
     * - 记录播放状态，切换后自动播放
     */
    fun skipToPrevious() {
        // 防抖检查
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < skipDebounceDelay) {
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "MusicService skipToPrevious: 防抖拦截")
            return
        }
        lastSkipTime = currentTime
        // 重构 (2025-12-01): 使用 PlaybackStateManager 获取状态
        val state = playbackStateManager.currentState
        val currentIndex = state.currentIndex
        val playlist = state.playlist
        val playMode = state.playMode
        // 单曲循环：重新播放当前歌曲
        if (playMode == PlayMode.REPEAT_ONE) {
            player?.seekTo(0)
            player?.play()
            return
        }
        // 计算上一首索引
        val prevIndex = when (playMode) {
            PlayMode.SEQUENCE -> {
                if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
            }

            PlayMode.SHUFFLE -> {
                val availableIndices = playlist.indices.filter { it != currentIndex }
                if (availableIndices.isEmpty()) return
                availableIndices.random()
            }

            else -> return
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "MusicService skipToPrevious: 当前=$currentIndex, 上一首=$prevIndex"
        )
        // 使用 UseCase 准备并播放上一首
        serviceScope.launch {
            try {
                val prevSong = playlist.getOrNull(prevIndex) ?: return@launch
                // 使用 PrepareSongUseCase 准备歌曲（插入记录 + 获取URL）
                val finalSong = prepareSongUseCase(prevSong)
                if (finalSong == null) {
                    LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "skipToPrevious: 歌曲准备失败")
                    return@launch
                }
                // 更新播放列表并播放
                val updatedPlaylist = playlist.toMutableList()
                updatedPlaylist[prevIndex] = finalSong
                withContext(Dispatchers.Main) {
                    playSongs(updatedPlaylist, prevIndex)
                }
            } catch (e: Exception) {
                LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "skipToPrevious: 异常 ${e.message}")
            }
        }
    }

    /**
     * 播放下一曲
     *
     * 架构重构 (2025-11-22): 使用 PlayNextSongUseCase
     * - 之前：直接操作 ExoPlayer 的 MediaItem（在单曲播放模式下失效）
     * - 现在：计算下一首索引，使用 UseCase 获取 URL 并播放
     * - 优势：逻辑统一，通知栏/界面/自动播放都使用相同机制
     *
     * 防抖与播放恢复优化 (2025-11-18):
     * - 添加500ms防抖，避免频繁点击
     * - 记录播放状态，切换后自动播放
     */
    fun skipToNext() {
        // 防抖检查
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSkipTime < skipDebounceDelay) {
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "MusicService skipToNext: 防抖拦截")
            return
        }
        lastSkipTime = currentTime
        // 重构 (2025-12-01): 使用 PlaybackStateManager 获取状态
        val state = playbackStateManager.currentState
        val currentIndex = state.currentIndex
        val playlist = state.playlist
        val playMode = state.playMode
        // 单曲循环：重新播放当前歌曲
        if (playMode == PlayMode.REPEAT_ONE) {
            player?.seekTo(0)
            player?.play()
            return
        }
        // 计算下一首索引
        val nextIndex = when (playMode) {
            PlayMode.SEQUENCE -> {
                if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
            }

            PlayMode.SHUFFLE -> {
                val availableIndices = playlist.indices.filter { it != currentIndex }
                if (availableIndices.isEmpty()) return
                availableIndices.random()
            }

            else -> return
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "MusicService skipToNext: 当前=$currentIndex, 下一首=$nextIndex"
        )
        // 使用 UseCase 准备并播放下一首
        serviceScope.launch {
            try {
                val nextSong = playlist.getOrNull(nextIndex) ?: return@launch
                // 使用 PrepareSongUseCase 准备歌曲（插入记录 + 获取URL）
                val finalSong = prepareSongUseCase(nextSong)
                if (finalSong == null) {
                    LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "skipToNext: 歌曲准备失败")
                    return@launch
                }
                // 更新播放列表并播放
                val updatedPlaylist = playlist.toMutableList()
                updatedPlaylist[nextIndex] = finalSong
                withContext(Dispatchers.Main) {
                    playSongs(updatedPlaylist, nextIndex)
                }
            } catch (e: Exception) {
                LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "skipToNext: 异常 ${e.message}")
            }
        }
    }

    /**
     * 更新指定索引歌曲的URL并跳转播放
     *
     * 修复 (2025-11-20): 避免因重新设置整个播放列表导致的状态闪烁
     * - 使用replaceMediaItem替换单个MediaItem，而不是clearMediaItems + setMediaItems
     * - 这样可以保持播放器状态稳定，避免闪烁
     *
     * 修复 (2025-11-22): 适配单曲播放模式
     * - playSongs()只加载当前歌曲（mediaItemCount=1），但逻辑播放列表仍保持完整
     * - ExoPlayer索引始终为0，逻辑索引为完整列表中的位置
     * - 使用playSongs()重新加载，而不是直接操作MediaItem
     *
     * @param index 歌曲索引（完整播放列表中的逻辑索引）
     * @param updatedSong 更新URL后的歌曲
     */
    fun updateSongAndSeek(index: Int, updatedSong: Song) {
        player?.let { p ->
            val path = updatedSong.path
            if (path.isNullOrEmpty()) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService updateSongAndSeek: path为空"
                )
                return
            }
            // 清除错误状态
            if (isInErrorState) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService updateSongAndSeek: 清除错误状态"
                )
                isInErrorState = false
                lastFailedSongIndex = -1
            }
            // 重构 (2025-12-01): 使用 PlaybackStateManager 更新播放列表
            val currentPlaylist = playbackStateManager.currentState.playlist.toMutableList()
            if (index < currentPlaylist.size) {
                currentPlaylist[index] = updatedSong
            }
            // 修复 (2025-11-22): 使用playSongs()重新加载
            // 原因：单曲播放模式下，ExoPlayer只有1个MediaItem（索引0）
            // 不能直接用replaceMediaItem(index)，因为index可能是34，而mediaItemCount=1
            // 策略：重新调用playSongs()加载更新后的歌曲
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService updateSongAndSeek: 重新加载歌曲 ${updatedSong.title}, 逻辑索引=$index"
            )
            playSongs(currentPlaylist, index)
        }
    }

    /**
     * 跳转到指定播放位置
     *
     * @param position 目标位置（毫秒）
     *
     * 使用场景:
     * - 用户拖动进度条
     * - 歌词点击跳转
     *
     * 错误恢复 (2025-11-18):
     * - 检测到错误状态时，先重新prepare再跳转
     * - 这样网络恢复后用户拖动进度条就能继续播放
     *
     * 修复 (2025-11-19):
     * - 只在播放器暂停时才调用play()，避免状态闪烁
     * - ExoPlayer的seekTo()会保持当前播放状态
     */
    fun seekTo(position: Long) {
        player?.let { p ->
            // 错误恢复：如果处于错误状态，重新prepare
            if (isInErrorState) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService seekTo: 检测到错误状态，尝试恢复播放"
                )
                p.prepare()
            }
            // 记录当前播放状态
            val wasPlaying = p.isPlaying || p.playbackState == Player.STATE_BUFFERING
            p.seekTo(position)
            // 修复 (2025-11-19): 只在之前暂停时才调用play()
            // 如果已经在播放，seekTo会保持播放状态，无需再调用play()
            if (!wasPlaying) {
                p.play()
            }
        }
        // 移除 (2025-11-20): 不需要手动调用updatePlaybackState()
        // ExoPlayer的回调会自动更新状态，手动调用会导致重复更新
    }

    /**
     * 切换播放模式 - 循环切换三种模式
     *
     * 切换顺序:
     * SEQUENCE（顺序） → SHUFFLE（随机） → REPEAT_ONE（单曲循环） → SEQUENCE...
     *
     * 副作用:
     * - 更新全局播放状态中的playMode
     * - 同步ExoPlayer的repeatMode（仅单曲循环时设置为REPEAT_MODE_ONE）
     */
    fun togglePlayMode() {
        // 重构 (2025-12-01): 使用 PlaybackStateManager 切换播放模式
        val nextMode = playbackStateManager.togglePlayMode()
        // 同步ExoPlayer的重复模式
        //
        // 修复 (2025-11-21): 顺序播放使用REPEAT_MODE_OFF + 手动循环
        // 问题原因：
        // - SEQUENCE模式下使用REPEAT_MODE_ALL，会导致ExoPlayer自动循环，与手动切换逻辑冲突
        // - 导致索引不同步，skipToNext/skipToPrevious失效
        //
        // 解决方案：
        // - SEQUENCE模式使用REPEAT_MODE_OFF，在STATE_ENDED时手动判断是否循环
        // - SHUFFLE模式使用REPEAT_MODE_OFF，手动处理随机播放逻辑
        // - REPEAT_ONE模式使用REPEAT_MODE_ONE，自动单曲循环
        player?.repeatMode = when (nextMode) {
            PlayMode.SEQUENCE -> Player.REPEAT_MODE_OFF  // 修复：顺序播放使用手动循环
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF   // 随机播放关闭自动循环
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE // 单曲循环
        }
    }

    /**
     * 设置URL失效监听器 (2025-11-22)
     *
     * 当播放出现403错误时，通知ViewModel重新获取URL
     *
     * @param listener 回调参数：
     *                 - songId: 需要重新获取URL的歌曲ID
     *                 - currentIndex: 歌曲在播放列表中的索引
     */
    fun setOnUrlExpiredListener(listener: ((songId: Long, currentIndex: Int) -> Unit)?) {
        onUrlExpiredListener = listener
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
        player?.let { p ->
            // 重构 (2025-12-01): 使用 PlaybackStateManager 移除歌曲
            // 从 ExoPlayer 中移除（需要先获取索引）
            val removeIndex =
                playbackStateManager.currentState.playlist.indexOfFirst { it.id == songId }
            if (removeIndex == -1) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService removeSongFromPlaylist: 歌曲不在播放列表中 (songId=$songId)"
                )
                return@let
            }

            try {
                p.removeMediaItem(removeIndex)
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService removeSongFromPlaylist: 移除 MediaItem 失败: ${e.message}"
                )
            }

            // 使用 PlaybackStateManager 更新播放列表和索引
            val newIndex = playbackStateManager.removeSongFromPlaylist(songId)

            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "MusicService removeSongFromPlaylist: 已移除歌曲 (songId=$songId), 新索引=$newIndex"
            )
        }
    }

    private fun updateCurrentSong() {
        player?.let { p ->
            // 修复 (2025-11-21): 不更新currentIndex
            // 原因：ExoPlayer只有一首歌，索引始终是0
            // currentIndex由playSongs()设置，这里只更新currentSong用于验证
            val mediaId = p.currentMediaItem?.mediaId
            val playlist = playbackStateManager.currentState.playlist
            val currentSong = if (mediaId != null) {
                playlist.find { it.id.toString() == mediaId }
            } else {
                null
            }
            // 重构 (2025-12-01): 使用 PlaybackStateManager 更新当前歌曲
            // 只在找到歌曲时更新（避免清空currentSong）
            if (currentSong != null) {
                playbackStateManager.updateCurrentSong(currentSong)
            }
        }
    }

    /**
     * 处理播放下一首歌曲 (新增 2025-11-22)
     *
     * 架构重构：
     * - Service 直接调用 UseCase 获取下一首歌曲
     * - 不再依赖 ViewModel 回调
     * - 解决后台播放时 ViewModel 可能被清理的问题
     *
     * 工作流程：
     * 1. 通过 UseCase 获取下一首歌曲（包含URL）
     * 2. 更新播放列表
     * 3. 调用 playSongs() 播放下一首
     */
    private fun handlePlayNext() {
        // 重构 (2025-12-01): 使用 PlaybackStateManager 获取状态
        val currentState = playbackStateManager.currentState
        val currentIndex = currentState.currentIndex
        val playlist = currentState.playlist
        val playMode = currentState.playMode
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "MusicService handlePlayNext: 当前索引=$currentIndex, 列表大小=${playlist.size}, 模式=$playMode"
        )
        // 使用协程获取下一首歌曲
        serviceScope.launch {
            try {
                // 调用 UseCase 获取下一首歌曲
                val result = playNextSongUseCase(currentIndex, playlist, playMode)
                if (result == null) {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "MusicService handlePlayNext: UseCase 返回 null，无法播放下一首"
                    )
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this@MusicService,
                            "无法播放下一首歌曲",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                val (nextIndex, nextSong) = result
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService handlePlayNext: UseCase 返回成功，下一首=${nextSong.title}, 索引=$nextIndex"
                )
                // 更新播放列表
                val updatedPlaylist = playlist.toMutableList()
                updatedPlaylist[nextIndex] = nextSong
                // 播放下一首（必须在主线程）
                withContext(Dispatchers.Main) {
                    playSongs(updatedPlaylist, nextIndex)
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "MusicService handlePlayNext: 已调用 playSongs，开始播放下一首"
                    )
                }
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "MusicService handlePlayNext: 异常 ${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }
}
/**
 * 网络类型枚举
 *
 * 用于区分不同网络环境，选择合适的缓存策略
 */
enum class NetworkType {
    WIFI,      // WiFi网络
    MOBILE,    // 移动网络（4G/5G）
    NONE,      // 无网络
    OTHER      // 其他网络类型（以太网等）
}
