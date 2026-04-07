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
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.util.Result
import javax.inject.Inject

/**
 * 处理URL失效UseCase (2025-12-03)
 *
 * 职责：
 * - 从播放列表中找到失效URL对应的歌曲
 * - 重新获取播放URL
 * - 返回更新后的歌曲对象
 *
 * 使用场景：
 * - 播放器检测到403错误时调用
 * - PlayerViewModel.handleUrlExpired() 使用
 *
 * 架构优势：
 * - 业务逻辑封装在Domain层
 * - ViewModel只负责协调和UI更新
 * - 便于单元测试
 *
 * @param playlist 当前播放列表
 * @param songId 失效URL对应的歌曲ID
 * @param currentIndex 歌曲在播放列表中的索引
 * @return HandleResult 成功返回更新后的歌曲，失败返回错误信息
 */
class HandleUrlExpiredUseCase @Inject constructor(
    private val musicOnlineRepository: MusicOnlineRepository
) {
    /**
     * 处理URL失效
     *
     * 流程：
     * 1. 验证歌曲存在且ID匹配
     * 2. 重新获取播放URL
     * 3. 返回更新后的歌曲对象
     *
     * @param playlist 当前播放列表
     * @param songId 失效的歌曲ID
     * @param currentIndex 歌曲索引
     * @return HandleResult 成功或失败结果
     */
    suspend operator fun invoke(
        playlist: List<Song>,
        songId: Long,
        currentIndex: Int
    ): HandleResult {
        LogConfig.d(
            LogConfig.TAG_PLAYER_DOMAIN,
            "HandleUrlExpiredUseCase invoke: 处理URL失效，songId=$songId, index=$currentIndex"
        )

        // 步骤1: 验证歌曲存在
        val song = playlist.getOrNull(currentIndex)
        if (song == null || song.id != songId) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DOMAIN,
                "HandleUrlExpiredUseCase 歌曲不存在或ID不匹配"
            )
            return HandleResult.Error("歌曲不存在")
        }

        // 步骤2: 重新获取播放URL
        LogConfig.d(
            LogConfig.TAG_PLAYER_DOMAIN,
            "HandleUrlExpiredUseCase 重新获取URL: ${song.title}"
        )

        val result = musicOnlineRepository.getSongPlayUrl(song)
        return when (result) {
            is Result.Success -> {
                val newUrl = result.data
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DOMAIN,
                    "HandleUrlExpiredUseCase URL获取成功: $newUrl"
                )
                val updatedSong = song.copy(path = newUrl)
                HandleResult.Success(updatedSong, currentIndex)
            }

            is Result.Error -> {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DOMAIN,
                    "HandleUrlExpiredUseCase URL重新获取失败: ${result.exception.message}"
                )
                HandleResult.Error(
                    "无法播放 ${song.title}：${result.exception.message ?: "网络错误"}"
                )
            }

            else -> {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DOMAIN,
                    "HandleUrlExpiredUseCase URL获取结果未知: ${song.title}"
                )
                HandleResult.Error("无法播放 ${song.title}")
            }
        }
    }

    /**
     * 处理结果
     */
    sealed class HandleResult {
        /**
         * 成功
         * @param updatedSong 更新后的歌曲（包含新URL）
         * @param index 歌曲索引
         */
        data class Success(
            val updatedSong: Song,
            val index: Int
        ) : HandleResult()

        /**
         * 失败
         * @param message 错误信息
         */
        data class Error(val message: String) : HandleResult()
    }
}
