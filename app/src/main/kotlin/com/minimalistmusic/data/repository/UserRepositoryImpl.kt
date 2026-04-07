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

import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.data.remote.UserApiService
import com.minimalistmusic.data.remote.dto.*
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.UserRepository
import com.minimalistmusic.util.Result
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
/**
 * 用户认证和云端同步 Repository
 *
 * 模式选择：
 * - USE_MOCK_MODE = true: 使用模拟模式（无需后端）
 * - USE_MOCK_MODE = false: 使用真实 API（需要部署后端）
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userPreferencesDataStore: UserPreferencesDataStore
) : UserRepository{
    companion object Companion {
        // 切换为 false 以使用真实 API
        private const val USE_MOCK_MODE = true
    }
    /**
     * 发送验证码
     */
    override suspend fun sendVerificationCode(phone: String): Result<SendCodeResponse> {
        return try {
            if (USE_MOCK_MODE) {
                // 模拟模式：延迟 1 秒后返回成功
                delay(1000)
                Result.Success(
                    SendCodeResponse(
                        code = 200,
                        message = "验证码已发送（模拟）",
                        data = SendCodeData(
                            expireTime = System.currentTimeMillis() / 1000 + 300 // 5分钟后过期
                        )
                    )
                )
            } else {
                // 真实 API 模式
                val response = userApiService.sendVerificationCode(
                    SendCodeRequest(phone)
                )
                if (response.code == 200) {
                    Result.Success(response)
                } else {
                    Result.Error(Exception(response.message))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 手机号登录/注册
     */
    override suspend fun login(phone: String, code: String): Result<LoginResponse> {
        return try {
            if (USE_MOCK_MODE) {
                // 模拟模式：延迟 1.5 秒后返回成功
                delay(1500)
                // 生成模拟的用户ID和token
                val userId = "user_${phone.takeLast(4)}_${System.currentTimeMillis()}"
                val token = "mock_token_${System.currentTimeMillis()}"
                Result.Success(
                    LoginResponse(
                        code = 200,
                        message = "登录成功",
                        data = LoginData(
                            userId = userId,
                            phone = phone,
                            token = token,
                            createdAt = System.currentTimeMillis() / 1000
                        )
                    )
                )
            } else {
                // 真实 API 模式
                val response = userApiService.login(
                    LoginRequest(phone, code)
                )
                if (response.code == 200 && response.data != null) {
                    Result.Success(response)
                } else {
                    Result.Error(Exception(response.message))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 同步"我喜欢"到云端
     */
    override suspend fun syncFavorites(favorites: List<Song>): Result<SyncFavoritesResponse> {
        return try {
            val token = userPreferencesDataStore.userToken.value
            if (token.isNullOrEmpty()) {
                return Result.Error(Exception("未登录"))
            }
            val userId = extractUserIdFromToken(token)
            val syncSongs = favorites.map { song ->
                SyncSong(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    albumArt = song.albumArt,
                    path = song.path,
                    addedAt = System.currentTimeMillis() / 1000
                )
            }
            if (USE_MOCK_MODE) {
                // 模拟模式：延迟 500ms 后返回成功
                delay(500)
                Result.Success(
                    SyncFavoritesResponse(
                        code = 200,
                        message = "同步成功（模拟）",
                        data = SyncFavoritesData(
                            favorites = syncSongs,
                            syncedCount = syncSongs.size,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                    )
                )
            } else {
                // 真实 API 模式
                val response = userApiService.syncFavorites(
                    token = "Bearer $token",
                    request = SyncFavoritesRequest(userId, syncSongs)
                )
                if (response.code == 200) {
                    Result.Success(response)
                } else {
                    Result.Error(Exception(response.message))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 获取云端"我喜欢"
     */
    override suspend fun getFavorites(): Result<GetFavoritesResponse> {
        return try {
            val token = userPreferencesDataStore.userToken.value
            if (token.isNullOrEmpty()) {
                return Result.Error(Exception("未登录"))
            }
            val userId = extractUserIdFromToken(token)
            if (USE_MOCK_MODE) {
                // 模拟模式：返回空列表
                delay(300)
                Result.Success(
                    GetFavoritesResponse(
                        code = 200,
                        message = "获取成功（模拟）",
                        data = GetFavoritesData(
                            favorites = emptyList(),
                            total = 0,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                    )
                )
            } else {
                // 真实 API 模式
                val response = userApiService.getFavorites(
                    token = "Bearer $token",
                    userId = userId
                )
                if (response.code == 200) {
                    Result.Success(response)
                } else {
                    Result.Error(Exception(response.message))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 同步"播放历史"到云端
     */
    override suspend fun syncHistory(history: List<Song>): Result<SyncHistoryResponse> {
        return try {
            val token = userPreferencesDataStore.userToken.value
            if (token.isNullOrEmpty()) {
                return Result.Error(Exception("未登录"))
            }
            val userId = extractUserIdFromToken(token)
            val syncSongs = history.map { song ->
                SyncSong(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    albumArt = song.albumArt,
                    path = song.path,
                    addedAt = System.currentTimeMillis() / 1000
                )
            }
            if (USE_MOCK_MODE) {
                // 模拟模式：延迟 500ms 后返回成功
                delay(500)
                Result.Success(
                    SyncHistoryResponse(
                        code = 200,
                        message = "同步成功（模拟）",
                        data = SyncHistoryData(
                            history = syncSongs,
                            syncedCount = syncSongs.size,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                    )
                )
            } else {
                // 真实 API 模式
                val response = userApiService.syncHistory(
                    token = "Bearer $token",
                    request = SyncHistoryRequest(userId, syncSongs)
                )
                if (response.code == 200) {
                    Result.Success(response)
                } else {
                    Result.Error(Exception(response.message))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 获取云端"播放历史"
     */
    override suspend fun getHistory(limit: Int, offset: Int): Result<GetHistoryResponse> {
        return try {
            val token = userPreferencesDataStore.userToken.value
            if (token.isNullOrEmpty()) {
                return Result.Error(Exception("未登录"))
            }
            val userId = extractUserIdFromToken(token)
            if (USE_MOCK_MODE) {
                // 模拟模式：返回空列表
                delay(300)
                Result.Success(
                    GetHistoryResponse(
                        code = 200,
                        message = "获取成功（模拟）",
                        data = GetHistoryData(
                            history = emptyList(),
                            total = 0,
                            updatedAt = System.currentTimeMillis() / 1000
                        )
                    )
                )
            } else {
                // 真实 API 模式
                val response = userApiService.getHistory(
                    token = "Bearer $token",
                    userId = userId,
                    limit = limit,
                    offset = offset
                )
                if (response.code == 200) {
                    Result.Success(response)
                } else {
                    Result.Error(Exception(response.message))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 从 token 中提取 userId
     * 简化版：直接从 token 字符串中提取
     */
    private fun extractUserIdFromToken(token: String): String {
        // 模拟模式下，token 格式为 "mock_token_timestamp"
        // 可以从 SharedPreferences 中获取 userId，或者从 token 解析
        // 这里简化处理，使用固定格式
        return "user_${System.currentTimeMillis().toString().takeLast(8)}"
    }
}
