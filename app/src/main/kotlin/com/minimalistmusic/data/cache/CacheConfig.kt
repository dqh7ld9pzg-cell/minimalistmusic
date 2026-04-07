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

import android.content.Context
import com.minimalistmusic.data.local.UserPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存配置管理
 *
 * 职责：
 * - 提供缓存相关的配置参数（目录、大小、上限等）
 * - 作为配置层，打破 AudioCacheManager 和 CacheStateManager 的循环依赖
 *
 * 架构优势：
 * - 单一职责：只负责配置，不涉及物理缓存或业务逻辑
 * - 打破循环：AudioCacheManager 和 CacheStateManager 都依赖它，但它不依赖任何一方
 * - 易于测试：配置逻辑独立，易于单元测试
 * - 易于扩展：新增配置参数只需修改此类
 *
 * 重构背景：
 * - 问题：AudioCacheManager 依赖 CacheStateManager.getDynamicMaxBytes()
 * - 问题：CacheStateManager 依赖 AudioCacheManager.getCache()
 * - 形成循环依赖，之前使用 Provider 延迟注入打破
 * - 解决：提取配置逻辑到独立的 CacheConfig，消除循环
 *
 * @param context 应用上下文
 * @param userPreferences 用户偏好设置
 *
 * @since 2025-12-02
 */
@Singleton
class CacheConfig @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {

    /**
     * 缓存目录名称
     */
    private val cacheDirName = "exoplayer_audio_cache"

    /**
     * 获取缓存根目录
     *
     * @return 缓存目录 File 对象
     */
    fun getCacheDir(): File {
        return File(context.cacheDir, cacheDirName).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * 获取缓存最大容量（字节）
     *
     * 策略：
     * - 用户启用缓存：使用用户设置的大小（cacheSizeMB）
     * - 用户禁用缓存：返回 0（不缓存）
     *
     * 注意：
     * - 返回值为字节数，需要转换 MB → Bytes
     * - 默认范围：50MB - 2048MB（由 UserPreferencesDataStore 控制）
     *
     * @return 缓存最大容量（字节）
     */
    fun getMaxCacheBytes(): Long {
        // TODO: 优化为 suspend 或 Flow，避免 runBlocking
        return if (userPreferencesDataStore.cacheEnabled.value) {
            val cacheSizeMB = userPreferencesDataStore.cacheSizeMB.value
            cacheSizeMB * 1024L * 1024L  // MB → Bytes
        } else {
            0L  // 禁用缓存
        }
    }

    /**
     * 获取动态缓存最大容量（字节）
     *
     * 优化 (2025-12-04): 解决无限增长问题
     * - 问题：currentCacheSpace包含碎片，可能无限增长
     * - 方案：达到上限后，使用实际歌曲大小而非currentCacheSpace
     *
     * 核心逻辑：
     * 1. 未达上限：maxBytes = currentCacheSpace + 100MB（动态适应）
     * 2. 达到上限：maxBytes = actualSongSize + 100MB（精确控制）
     *
     * 为什么这样设计？
     * - 未达上限：需要灵活适应歌曲大小变化，使用物理层实际占用
     * - 达到上限：防止碎片累积导致无限增长，使用业务层精确值
     * - 100MB buffer：足够容纳10首歌的临时缓存，减少清理频率
     *
     * 重构说明 (2025-12-02):
     * - 从 CacheStateManager.getDynamicMaxBytes() 迁移到此处
     * - 避免 AudioCacheManager 依赖 CacheStateManager
     *
     * @param currentCacheSpace 当前已使用的缓存空间（字节，ExoPlayer物理层）
     * @param currentCachedCount 当前已缓存歌曲数量
     * @param actualSongSize 实际歌曲占用大小（字节，不含碎片）
     * @return 动态缓存最大容量（字节）
     */
    fun getDynamicMaxBytes(
        currentCacheSpace: Long,
        currentCachedCount: Int,
        actualSongSize: Long
    ): Long {
        val maxCachedSongs = userPreferencesDataStore.maxCachedSongs.value
        val bufferBytes = 100L * 1024 * 1024  // 100MB 固定buffer

        return if (currentCachedCount >= maxCachedSongs) {
            // ========== 达到上限：使用精确的实际歌曲大小 ==========
            // 原理：
            // - actualSongSize 来自数据库，只包含完整缓存的歌曲
            // - 不包含碎片和临时文件，避免无限增长
            // - 100MB buffer 足够容纳新歌替换时的临时缓存
            val maxBytes = actualSongSize + bufferBytes

            com.minimalistmusic.util.LogConfig.d(
                com.minimalistmusic.util.LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheConfig getDynamicMaxBytes [已达上限]: " +
                    "实际歌曲=${actualSongSize.formatBytes()}, " +
                    "buffer=${bufferBytes.formatBytes()}, " +
                    "maxBytes=${maxBytes.formatBytes()}, " +
                    "当前歌曲数=$currentCachedCount/$maxCachedSongs"
            )

            maxBytes
        } else {
            // ========== 未达上限：动态计算 ==========
            // 原理：
            // - 使用 currentCacheSpace（物理层实际占用）
            // - 灵活适应歌曲大小变化
            // - 100MB buffer 减少清理频率
            val maxBytes = currentCacheSpace + bufferBytes

            com.minimalistmusic.util.LogConfig.d(
                com.minimalistmusic.util.LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheConfig getDynamicMaxBytes [未达上限]: " +
                    "当前占用=${currentCacheSpace.formatBytes()}, " +
                    "buffer=${bufferBytes.formatBytes()}, " +
                    "maxBytes=${maxBytes.formatBytes()}, " +
                    "当前歌曲数=$currentCachedCount/$maxCachedSongs"
            )

            maxBytes
        }
    }

    /**
     * 格式化字节数（辅助函数）
     */
    private fun Long.formatBytes(): String {
        return when {
            this < 1024 * 1024 -> "${this / 1024}KB"
            else -> "${this / 1024 / 1024}MB"
        }
    }

}
