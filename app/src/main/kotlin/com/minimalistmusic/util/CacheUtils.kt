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

package com.minimalistmusic.util

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.cache.KeyValueCacheManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存工具类
 *
 * 用途:
 * - 为设置页面提供缓存信息查询和管理功能
 * - 统一管理音频缓存和数据缓存
 *
 * 功能:
 * 1. 查询总缓存大小
 * 2. 清空所有缓存
 * 3. 分别查询和清理音频缓存/数据缓存
 */
@OptIn(UnstableApi::class)
@Singleton
class CacheUtils
@Inject constructor(
    private val audioCacheManager: AudioCacheManager,
    private val cacheManager: KeyValueCacheManager,
) {

    /**
     * 清空所有缓存
     *
     * 包括:
     * - 音频缓存（ExoPlayer缓存）
     * - 数据缓存（推荐歌单、播放URL等）
     */
    suspend fun clearAllCache() {
        // 清空音频缓存
        audioCacheManager.clearCache()
        // 清空数据缓存
        cacheManager.clearAllCache()
    }
}
