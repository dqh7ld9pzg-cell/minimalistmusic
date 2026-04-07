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

package com.minimalistmusic.domain.usecase.player

import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.domain.model.PlayMode
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.Result
import javax.inject.Inject
/**
 * 播放下一首歌曲用例 (2025-11-22)
 *
 * 职责：
 * - 根据播放模式计算下一首歌曲
 * - 获取歌曲的播放URL（如果需要）
 * - 插入播放历史记录
 *
 * 架构优势：
 * - Service 和 ViewModel 都可以调用此 UseCase
 * - 业务逻辑集中管理，易于测试和维护
 * - 解耦 Service 和 ViewModel，避免生命周期问题
 *
 * @param currentIndex 当前播放索引
 * @param playlist 播放列表
 * @param playMode 播放模式
 * @return 下一首歌曲及其索引，如果失败返回 null
 */
class PlayNextSongUseCase @Inject constructor(
    private val musicOnlineRepository: MusicOnlineRepository,
    private val musicLocalRepository: MusicLocalRepository
) {
    /**
     * 执行播放下一首逻辑
     *
     * @param currentIndex 当前播放索引
     * @param playlist 播放列表
     * @param playMode 播放模式
     * @return Pair<索引, 歌曲>，失败返回 null
     */
    suspend operator fun invoke(
        currentIndex: Int,
        playlist: List<Song>,
        playMode: PlayMode
    ): Pair<Int, Song>? {
        if (playlist.isEmpty()) {
            LogConfig.e(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase 播放列表为空")
            return null
        }
        // 1. 根据播放模式计算下一首索引
        val nextIndex = when (playMode) {
            PlayMode.SEQUENCE -> {
                // 顺序播放：循环到下一首
                if (currentIndex < playlist.size - 1) {
                    currentIndex + 1
                } else {
                    0 // 循环到第一首
                }
            }
            PlayMode.SHUFFLE -> {
                // 随机播放：随机选择（排除当前歌曲）
                val availableIndices = playlist.indices.filter { it != currentIndex }
                if (availableIndices.isEmpty()) return null
                availableIndices.random()
            }
            PlayMode.REPEAT_ONE -> {
                // 单曲循环：保持当前索引
                currentIndex
            }
        }
        val nextSong = playlist.getOrNull(nextIndex)
        if (nextSong == null) {
            LogConfig.e(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase 下一首歌曲不存在，索引=$nextIndex")
            return null
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_DOMAIN,
            "PlayNextSongUseCase 计算下一首: 模式=$playMode, 当前索引=$currentIndex, 下一首索引=$nextIndex, 歌曲=${nextSong.title}"
        )
        // 2. 插入播放记录（确保外键约束）
        try {
            musicLocalRepository.addToPlayHistory(nextSong)
            LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase 已插入播放记录: ${nextSong.title}")
        } catch (e: Exception) {
            LogConfig.e(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase 插入播放记录失败: ${e.message}")
        }
        // 3. 获取播放URL（如果是在线歌曲且URL为空）
        val finalSong = if (!nextSong.isLocal && nextSong.path.isNullOrEmpty()) {
            LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase 开始获取播放URL: ${nextSong.title}")
            when (val result = musicOnlineRepository.getSongPlayUrl(nextSong)) {
                is Result.Success -> {
                    LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase URL获取成功: ${result.data}")
                    nextSong.copy(path = result.data)
                }
                is Result.Error -> {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DOMAIN,
                        "PlayNextSongUseCase URL获取失败: ${result.exception.message}"
                    )
                    return null // 获取失败，返回 null
                }
                else -> {
                    LogConfig.w(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase URL获取结果未知")
                    return null
                }
            }
        } else {
            LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PlayNextSongUseCase 使用已有URL或本地路径")
            nextSong
        }
        return Pair(nextIndex, finalSong)
    }
}
