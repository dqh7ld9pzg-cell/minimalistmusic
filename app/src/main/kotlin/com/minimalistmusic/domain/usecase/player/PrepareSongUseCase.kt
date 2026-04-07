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

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.cache.CachePerformanceTracker
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.Result
import javax.inject.Inject
/**
 * 准备歌曲用例 (2025-11-22)
 *
 * 职责：
 * - 插入播放历史记录
 * - 获取歌曲的播放URL（如果需要）
 * - 开始性能跟踪（2025-12-03新增）
 *
 * 使用场景：
 * - skipToPrevious/skipToNext 已经计算好索引，只需要准备歌曲
 * - 避免在 Service 中重复"插入记录 + 获取URL"的逻辑
 *
 * 架构优势：
 * - 业务逻辑封装在 UseCase，Service 保持简洁
 * - 可复用，所有需要准备歌曲的地方都可以调用
 *
 * @param song 要准备的歌曲
 * @return 准备好的歌曲（包含URL），如果失败返回 null
 */
@UnstableApi
class PrepareSongUseCase @Inject constructor(
    private val musicOnlineRepository: MusicOnlineRepository,
    private val musicLocalRepository: MusicLocalRepository,
) {
    /**
     * 准备歌曲：插入记录 + 获取URL
     *
     * @param song 要准备的歌曲
     * @return 准备好的歌曲，失败返回 null
     */
    suspend operator fun invoke(song: Song): Song? {
        // 1. 插入播放记录（确保外键约束）
        try {
            musicLocalRepository.addToPlayHistory(song)
            LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PrepareSongUseCase 已插入播放记录: ${song.title}")
        } catch (e: Exception) {
            LogConfig.e(LogConfig.TAG_PLAYER_DOMAIN, "PrepareSongUseCase 插入播放记录失败: ${e.message}")
        }
        // 2. 获取播放URL（如果是在线歌曲且URL为空）
        val finalSong = if (!song.isLocal && song.path.isNullOrEmpty()) {
            LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PrepareSongUseCase 开始获取播放URL: ${song.title}")
            when (val result = musicOnlineRepository.getSongPlayUrl(song)) {
                is Result.Success -> {
                    LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PrepareSongUseCase URL获取成功: ${result.data}")
                    val songWithUrl = song.copy(path = result.data)
                    songWithUrl
                }
                is Result.Error -> {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DOMAIN,
                        "PrepareSongUseCase URL获取失败: ${result.exception.message}"
                    )
                    return null // 获取失败，返回 null
                }
                else -> {
                    LogConfig.w(LogConfig.TAG_PLAYER_DOMAIN, "PrepareSongUseCase URL获取结果未知")
                    return null
                }
            }
        } else {
            LogConfig.d(LogConfig.TAG_PLAYER_DOMAIN, "PrepareSongUseCase 使用已有URL或本地路径")
            song
        }
        return finalSong
    }

}
