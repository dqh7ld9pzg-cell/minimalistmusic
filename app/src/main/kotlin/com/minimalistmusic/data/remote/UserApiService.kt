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

package com.minimalistmusic.data.remote

import com.minimalistmusic.data.remote.dto.*
import retrofit2.http.*
/**
 * 用户认证和同步 API 服务
 *
 * 后端部署说明：
 * 1. 访问 https://github.com/vercel/vercel 了解 Vercel 部署
 * 2. 或者使用提供的简化版后端代码（见项目 README）
 * 3. 将 BASE_URL 替换为您的 Vercel 部署地址
 *
 * 当前使用模拟模式，可以在没有后端的情况下测试功能
 */
interface UserApiService {
    /**
     * 发送验证码
     * POST /api/auth/send-code
     */
    @POST("api/auth/send-code")
    suspend fun sendVerificationCode(
        @Body request: SendCodeRequest
    ): SendCodeResponse
    /**
     * 手机号登录/注册
     * POST /api/auth/login
     */
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse
    /**
     * 同步"我喜欢"到云端
     * POST /api/sync/favorites
     */
    @POST("api/sync/favorites")
    suspend fun syncFavorites(
        @Header("Authorization") token: String,
        @Body request: SyncFavoritesRequest
    ): SyncFavoritesResponse
    /**
     * 获取云端"我喜欢"
     * GET /api/sync/favorites
     */
    @GET("api/sync/favorites")
    suspend fun getFavorites(
        @Header("Authorization") token: String,
        @Query("user_id") userId: String
    ): GetFavoritesResponse
    /**
     * 同步"播放历史"到云端
     * POST /api/sync/history
     */
    @POST("api/sync/history")
    suspend fun syncHistory(
        @Header("Authorization") token: String,
        @Body request: SyncHistoryRequest
    ): SyncHistoryResponse
    /**
     * 获取云端"播放历史"
     * GET /api/sync/history
     */
    @GET("api/sync/history")
    suspend fun getHistory(
        @Header("Authorization") token: String,
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): GetHistoryResponse
}
