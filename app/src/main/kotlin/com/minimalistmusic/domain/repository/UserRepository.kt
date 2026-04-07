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

package com.minimalistmusic.domain.repository

import com.minimalistmusic.data.remote.dto.GetFavoritesResponse
import com.minimalistmusic.data.remote.dto.GetHistoryResponse
import com.minimalistmusic.data.remote.dto.LoginResponse
import com.minimalistmusic.data.remote.dto.SendCodeResponse
import com.minimalistmusic.data.remote.dto.SyncFavoritesResponse
import com.minimalistmusic.data.remote.dto.SyncHistoryResponse
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.util.Result
import javax.inject.Singleton
/**
 * 用户认证和云端同步 Repository
 *
 * 模式选择：
 * - USE_MOCK_MODE = true: 使用模拟模式（无需后端）
 * - USE_MOCK_MODE = false: 使用真实 API（需要部署后端）
 */
@Singleton
interface UserRepository {
    /**
     * 发送验证码
     */
    suspend fun sendVerificationCode(phone: String): Result<SendCodeResponse>
    /**
     * 手机号登录/注册
     */
    suspend fun login(phone: String, code: String): Result<LoginResponse>
    /**
     * 同步"我喜欢"到云端
     */
    suspend fun syncFavorites(favorites: List<Song>): Result<SyncFavoritesResponse>
    /**
     * 获取云端"我喜欢"
     */
    suspend fun getFavorites(): Result<GetFavoritesResponse>
    /**
     * 同步"播放历史"到云端
     */
    suspend fun syncHistory(history: List<Song>): Result<SyncHistoryResponse>
    /**
     * 获取云端"播放历史"
     */
    suspend fun getHistory(limit: Int = 100, offset: Int = 0): Result<GetHistoryResponse>
}
