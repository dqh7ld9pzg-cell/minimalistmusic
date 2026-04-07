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

import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.util.Result
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * 准备播放列表UseCase (2025-12-03)
 *
 * 职责：
 * - 确保目标歌曲记录在数据库中（外键依赖）
 * - 为在线歌曲获取播放URL（支持超时保护）
 * - 返回准备好的歌曲列表和目标歌曲
 *
 * 使用场景：
 * - PlayerViewModel.playSongs() 调用
 * - 简化ViewModel逻辑，将复杂的准备流程封装在UseCase
 *
 * 架构优势：
 * - 业务逻辑集中在Domain层，便于测试和复用
 * - ViewModel保持简洁，只负责协调和状态管理
 *
 * @param songs 原始歌曲列表
 * @param startIndex 目标播放索引
 * @return PrepareResult 包含准备好的歌曲列表和目标歌曲，失败返回错误信息
 */
class PreparePlaylistUseCase @Inject constructor(
    private val musicOnlineRepository: MusicOnlineRepository,
    private val musicLocalRepository: MusicLocalRepository
) {
    /**
     * 准备播放列表
     *
     * 流程：
     * 1. 验证索引有效性
     * 2. 确保目标歌曲在数据库中（外键约束）
     * 3. 获取目标歌曲的播放URL（如果是在线歌曲）
     * 4. 返回更新后的歌曲列表
     *
     * @param songs 歌曲列表
     * @param startIndex 起始播放索引
     * @return PrepareResult 成功返回准备好的数据，失败返回错误
     */
    suspend operator fun invoke(songs: List<Song>, startIndex: Int): PrepareResult {
        val targetSong = songs.getOrNull(startIndex)
            ?: return PrepareResult.Error("歌曲不存在，索引越界: $startIndex")

        LogConfig.d(
            LogConfig.TAG_PLAYER_DOMAIN,
            "PreparePlaylistUseCase invoke: 开始准备播放列表，目标歌曲=${targetSong.title}"
        )

        // 步骤1: 确保目标歌曲在数据库中（避免外键约束失败）
        try {
            musicLocalRepository.addToPlayHistory(targetSong)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DOMAIN,
                "PreparePlaylistUseCase 目标歌曲已插入数据库: ${targetSong.title}"
            )
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DOMAIN,
                "PreparePlaylistUseCase 插入歌曲记录失败: ${e.message}"
            )
            return PrepareResult.Error("数据库操作失败: ${e.message}")
        }

        // 步骤2: 获取目标歌曲的播放URL（如果是在线歌曲）
        val updatedSong: Song = if (!targetSong.isLocal) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DOMAIN,
                "PreparePlaylistUseCase 开始获取在线歌曲URL: ${targetSong.title}"
            )

            // 10秒超时保护
            val result = try {
                withTimeout(10_000L) {
                    musicOnlineRepository.getSongPlayUrl(targetSong)
                }
            } catch (e: TimeoutCancellationException) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DOMAIN,
                    "PreparePlaylistUseCase URL获取超时: ${targetSong.title}"
                )
                return PrepareResult.Error("播放 ${targetSong.title} 超时，请检查网络连接")
            }

            when (result) {
                is Result.Success -> {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DOMAIN,
                        "PreparePlaylistUseCase URL获取成功: ${result.data}"
                    )
                    targetSong.copy(path = result.data)
                }
                is Result.Error -> {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DOMAIN,
                        "PreparePlaylistUseCase URL获取失败: ${result.exception.message}"
                    )
                    return PrepareResult.Error(
                        "无法播放 ${targetSong.title}：${result.exception.message ?: "网络错误"}"
                    )
                }
                else -> {
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DOMAIN,
                        "PreparePlaylistUseCase URL结果未知: ${targetSong.title}"
                    )
                    return PrepareResult.Error("无法播放 ${targetSong.title}")
                }
            }
        } else {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DOMAIN,
                "PreparePlaylistUseCase 本地歌曲，使用已有路径: ${targetSong.title}"
            )
            targetSong
        }

        // 步骤3: 更新歌曲列表
        val updatedSongs = songs.toMutableList().apply {
            set(startIndex, updatedSong)
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_DOMAIN,
            "PreparePlaylistUseCase 播放列表准备完成，歌曲数=${updatedSongs.size}"
        )

        return PrepareResult.Success(updatedSongs, updatedSong, startIndex)
    }

    /**
     * 准备结果
     */
    sealed class PrepareResult {
        /**
         * 成功
         * @param songs 更新后的歌曲列表
         * @param targetSong 目标歌曲（已包含URL）
         * @param startIndex 播放索引
         */
        data class Success(
            val songs: List<Song>,
            val targetSong: Song,
            val startIndex: Int
        ) : PrepareResult()

        /**
         * 失败
         * @param message 错误信息
         */
        data class Error(val message: String) : PrepareResult()
    }
}
