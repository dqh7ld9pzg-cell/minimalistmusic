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

package com.minimalistmusic.data.cache

import com.google.gson.Gson
import com.minimalistmusic.data.local.dao.CacheDao
import com.minimalistmusic.data.local.entity.CacheEntity
import com.minimalistmusic.util.LogConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * 通用key-Value缓存管理器
 *
 * 职责：
 * - 提供JSON、文本数据的缓存读写接口
 * - 自动处理过期逻辑
 * - 支持 JSON 序列化/反序列化
 *
 * 使用场景：
 * - 歌词缓存 (KEY_LYRIC_PREFIX)
 *
 * 注意：此类专门用于JSON、文本等数据缓存，不用于歌曲URL缓存（由SongUrlCacheManager管理）
 *
 */
@Singleton
class KeyValueCacheManager @Inject constructor(
    private val cacheDao: CacheDao,
    private val gson: Gson,
) {
    /**
     * 获取缓存
     *
     * @param key 缓存键
     * @param clazz 数据类型
     * @return 缓存的数据，如果不存在或已过期则返回 null
     */
    suspend fun <T> getCache(key: String, clazz: Class<T>): T? {
        val cache = cacheDao.getCache(key) ?: return null
        // 检查是否过期
        if (cache.expiresAt < System.currentTimeMillis()) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "KeyValueCacheManager getCache key: $key 已过期"
            )
            cacheDao.deleteCache(key)
            return null
        }
        return try {
            val data = gson.fromJson(cache.data, clazz)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "KeyValueCacheManager getCache data: $data"
            )
            data
        } catch (e: Exception) {
            // 解析失败，删除缓存
            cacheDao.deleteCache(key)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "KeyValueCacheManager getCache error msg: ${e.message}"
            )
            null
        }
    }

    /**
     * 保存缓存
     *
     * @param key 缓存键
     * @param data 要缓存的数据
     * @param expiresIn 过期时长（默认 30 天）
     */
    suspend fun <T> saveCache(
        key: String,
        data: T,
        expiresIn: Duration = 30.days,
    ) {
        val jsonData = gson.toJson(data)
        val expiresAt = System.currentTimeMillis() + expiresIn.inWholeMilliseconds
        val cache = CacheEntity(
            key = key,
            data = jsonData,
            expiresAt = expiresAt
        )
        cacheDao.insertCache(cache)
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "KeyValueCacheManager saveCache key: $key, data: $data, jsonData: $jsonData"
        )
    }

    /**
     * 删除指定缓存
     */
    suspend fun deleteCache(key: String) {
        cacheDao.deleteCache(key)
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "KeyValueCacheManager deleteCache key: $key"
        )
    }

    /**
     * 清理过期缓存
     */
    suspend fun cleanExpiredCache() {
        cacheDao.deleteExpiredCache()
    }

    /**
     * 清空所有缓存
     */
    suspend fun clearAllCache() {
        cacheDao.clearAllCache()
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "KeyValueCacheManager clearAllCache"
        )
    }

    companion object {
        // 缓存键常量
        const val KEY_SONG_URL_PREFIX = "song_url_" // 歌曲播放URL缓存前缀，如 "song_url_123456"
        const val KEY_LYRIC_PREFIX = "lyric_url_" // 歌曲播放URL缓存前缀，如 "song_url_123456"

        /**
         * 生成歌曲URL缓存键
         * @param songId 歌曲ID
         */
        fun getSongUrlKey(songId: String): String = "$KEY_SONG_URL_PREFIX$songId"

        /**
         * 生成歌词缓存键
         * @param songId 歌曲ID
         */
        fun getLyricKey(songId: String): String = "$KEY_LYRIC_PREFIX$songId"
    }
}
