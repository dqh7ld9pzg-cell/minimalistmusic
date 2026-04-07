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

package com.minimalistmusic.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.PlaybackController
import com.minimalistmusic.service.MusicService
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放器Repository实现
 *
 * 架构说明:
 * - 单例模式：全局唯一实例，避免多次绑定Service
 * - 封装MusicService：所有Service交互都通过此Repository
 * - 生命周期管理：内部管理Service绑定和解绑
 * - 职责单一：只负责播放控制，不管理状态
 *
 * 架构分离:
 * - 播放状态：由 PlaybackStateManager (@Singleton) 统一管理
 * - 播放控制：由 PlaybackController 封装 MusicService 方法
 * - ViewModel：同时注入两者，分别访问状态和控制
 *
 * 依赖注入：
 * - @Singleton确保全局唯一
 * - @ApplicationContext注入Application Context
 */
@Singleton
@UnstableApi
class PlaybackControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackController {

    // MusicService实例
    private var musicService: MusicService? = null

    // ServiceConnection回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "onServiceConnected: MusicService已连接")
            val binder = service as? MusicService.MusicBinder
            musicService = binder?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "onServiceDisconnected: MusicService断开连接"
            )
            musicService = null
        }
    }

    /**
     * 绑定MusicService
     *
     * 注意：此方法应在Application.onCreate()中调用，确保Service在应用生命周期内保持绑定
     *
     * 执行流程：
     * 1. 启动前台服务（startForegroundService）
     * 2. 绑定服务（bindService）
     */
    override fun bindService() {
        LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "bindService: 开始启动并绑定MusicService")
        val intent = Intent(context, MusicService::class.java)

        // 启动前台服务（Android 8.0+必须使用前台服务）
        try {
            context.startForegroundService(intent)
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "bindService: MusicService前台服务已启动")
        } catch (e: Exception) {
            LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "bindService: 启动前台服务失败 - ${e.message}"
            )
        }

        // 绑定服务
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (bound) {
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "bindService: MusicService绑定请求成功")
        } else {
            LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "bindService: MusicService绑定请求失败")
        }
    }

    /**
     * 解绑MusicService
     *
     * 注意：此方法应在Application.onTerminate()中调用（如果需要）
     */
    override fun unbindService() {
        LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "unbindService: 解绑MusicService")
        try {
            context.unbindService(serviceConnection)
            musicService = null
        } catch (e: Exception) {
            LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "unbindService: 解绑失败 - ${e.message}")
        }
    }

    /**
     * 播放歌曲列表
     *
     * 注意：URL获取逻辑应由ViewModel或UseCase处理，此方法只负责调用Service
     *
     * @param songs 歌曲列表（应包含有效的path）
     * @param startIndex 起始播放索引
     */
    override suspend fun playSongs(songs: List<Song>, startIndex: Int) {
        val service = musicService
        if (service == null) {
            LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "playSongs: MusicService未连接，无法播放")
            return
        }
        LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "playSongs: 播放歌曲列表，size=${songs.size}, startIndex=$startIndex"
        )
        service.playSongs(songs, startIndex)
    }

    override fun pause() {
        musicService?.pause()
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "pauseSongs: MusicService未连接")
    }

    override fun togglePlayPause() {
        musicService?.togglePlayPause()
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "togglePlayPause: MusicService未连接")
    }

    override fun skipToNext() {
        musicService?.skipToNext()
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "skipToNext: MusicService未连接")
    }

    override fun skipToPrevious() {
        musicService?.skipToPrevious()
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "skipToPrevious: MusicService未连接")
    }

    override fun seekTo(position: Long) {
        musicService?.seekTo(position)
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "seekTo: MusicService未连接")
    }

    override fun togglePlayMode() {
        musicService?.togglePlayMode()
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "togglePlayMode: MusicService未连接")
    }

    override fun removeSongFromPlaylist(songId: Long) {
        musicService?.removeSongFromPlaylist(songId)
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "removeSongFromPlaylist: MusicService未连接"
            )
    }

    override fun updateSongAndSeek(index: Int, song: Song) {
        musicService?.updateSongAndSeek(index, song)
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "updateSongAndSeek: MusicService未连接"
            )
    }

    override fun setOnUrlExpiredListener(listener: ((Long, Int) -> Unit)?) {
        musicService?.setOnUrlExpiredListener(listener)
            ?: LogConfig.w(LogConfig.TAG_PLAYER_DATA_LOCAL, "setOnUrlExpiredListener: MusicService未连接"
            )
    }

}
