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

package com.minimalistmusic.domain.usecase.history

import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.data.repository.UserRepositoryImpl
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.util.Result
import kotlinx.coroutines.flow.first
import javax.inject.Inject
/**
 * 同步播放历史到云端 UseCase
 *
 * 职责：
 * - 检查用户是否已登录
 * - 检查是否开启了播放历史同步开关
 * - 获取本地播放历史（限制数量，避免同步过多数据）
 * - 调用 UserRepositoryImpl 同步到云端
 * - 统一错误处理和日志记录
 *
 * 复用场景：
 * - PlayerViewModel：播放歌曲后自动同步历史
 * - ProfileViewModel：用户手动触发同步
 *
 * 重构说明（2025-11-11）:
 * - 迁移依赖：MusicRepository → LocalMusicRepository
 * - 原因：需要获取本地播放历史，应使用 LocalMusicRepository
 */
class SyncHistoryToCloudUseCase @Inject constructor(
    private val musicLocalRepository: MusicLocalRepository,
    private val userRepository: UserRepositoryImpl,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    /**
     * 执行同步操作
     *
     * @param maxHistoryCount 最大同步历史数量，默认 100 首
     * @return Result<Int> 成功时返回同步的历史记录数量，失败时返回错误信息
     */
    suspend operator fun invoke(maxHistoryCount: Int = DEFAULT_MAX_HISTORY): Result<Int> {
        return try {
            // 1. 检查登录状态
            if (!userPreferencesDataStore.isLoggedIn.value) {
                return Result.Error(Exception("用户未登录"))
            }
            // 2. 检查同步开关
            if (!userPreferencesDataStore.syncHistory.value) {
                return Result.Error(Exception("未开启播放历史同步"))
            }
            // 3. 获取本地播放历史（限制数量）
            val history = musicLocalRepository.getPlayHistory().first()
            if (history.isEmpty()) {
                return Result.Success(0)
            }
            // 4. 只同步最近的 N 首，避免数据量过大
            val recentHistory = history.take(maxHistoryCount)
            // 5. 调用 UserRepositoryImpl 同步
            when (val result = userRepository.syncHistory(recentHistory)) {
                is Result.Success -> {
                    val syncedCount = result.data.data?.syncedCount ?: recentHistory.size
                    Result.Success(syncedCount)
                }
                is Result.Error -> {
                    Result.Error(result.exception)
                }
                else -> {
                    Result.Error(Exception("未知错误"))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    companion object {
        private const val DEFAULT_MAX_HISTORY = 100
    }
}