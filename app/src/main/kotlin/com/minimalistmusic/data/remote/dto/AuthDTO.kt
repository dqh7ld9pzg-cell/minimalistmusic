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

package com.minimalistmusic.data.remote.dto

import com.google.gson.annotations.SerializedName
/**
 * 发送验证码请求
 */
data class SendCodeRequest(
    @SerializedName("phone")
    val phone: String
)
/**
 * 发送验证码响应
 */
data class SendCodeResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SendCodeData? = null
)
data class SendCodeData(
    @SerializedName("expire_time")
    val expireTime: Long // 过期时间戳（秒）
)
/**
 * 登录请求
 */
data class LoginRequest(
    @SerializedName("phone")
    val phone: String,
    @SerializedName("code")
    val code: String
)
/**
 * 登录响应
 */
data class LoginResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: LoginData? = null
)
data class LoginData(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("token")
    val token: String,
    @SerializedName("created_at")
    val createdAt: Long
)
/**
 * 同步"我喜欢"请求
 */
data class SyncFavoritesRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("favorites")
    val favorites: List<SyncSong>
)
/**
 * 同步"我喜欢"响应
 */
data class SyncFavoritesResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SyncFavoritesData? = null
)
data class SyncFavoritesData(
    @SerializedName("favorites")
    val favorites: List<SyncSong>,
    @SerializedName("synced_count")
    val syncedCount: Int,
    @SerializedName("updated_at")
    val updatedAt: Long
)
/**
 * 同步"播放历史"请求
 */
data class SyncHistoryRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("history")
    val history: List<SyncSong>
)
/**
 * 同步"播放历史"响应
 */
data class SyncHistoryResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: SyncHistoryData? = null
)
data class SyncHistoryData(
    @SerializedName("history")
    val history: List<SyncSong>,
    @SerializedName("synced_count")
    val syncedCount: Int,
    @SerializedName("updated_at")
    val updatedAt: Long
)
/**
 * 同步用的歌曲数据
 */
data class SyncSong(
    @SerializedName("id")
    val id: Long,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist")
    val artist: String,
    @SerializedName("album")
    val album: String? = null,
    @SerializedName("duration")
    val duration: Long,
    @SerializedName("album_art")
    val albumArt: String? = null,
    @SerializedName("path")
    val path: String? = null,
    @SerializedName("added_at")
    val addedAt: Long? = null
)
/**
 * 获取云端数据请求
 */
data class GetSyncDataRequest(
    @SerializedName("user_id")
    val userId: String
)
/**
 * 获取"我喜欢"响应
 */
data class GetFavoritesResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: GetFavoritesData? = null
)
data class GetFavoritesData(
    @SerializedName("favorites")
    val favorites: List<SyncSong>,
    @SerializedName("total")
    val total: Int,
    @SerializedName("updated_at")
    val updatedAt: Long
)
/**
 * 获取"播放历史"响应
 */
data class GetHistoryResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: GetHistoryData? = null
)
data class GetHistoryData(
    @SerializedName("history")
    val history: List<SyncSong>,
    @SerializedName("total")
    val total: Int,
    @SerializedName("updated_at")
    val updatedAt: Long
)
