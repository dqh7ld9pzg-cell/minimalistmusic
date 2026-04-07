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

package com.minimalistmusic.data.local.entity

import androidx.room.*

/**
 * 通用缓存实体
 *
 * 用于存储各种网络请求的缓存数据
 */
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val key: String, // 缓存键（如 "playlists_recommend"）
    val data: String, // JSON 序列化的数据
    val cachedAt: Long = System.currentTimeMillis(), // 缓存时间
    val expiresAt: Long // 过期时间
)
