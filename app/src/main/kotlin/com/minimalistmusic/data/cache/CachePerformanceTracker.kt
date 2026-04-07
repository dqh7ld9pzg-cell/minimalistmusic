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

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.util.LogConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * 缓存性能跟踪器
 *
 * 功能:
 * - 跟踪每首歌曲的缓存性能指标
 * - 生成详细的性能日志（包括歌曲名、URL、文件大小、耗时、速度等）
 * - 支持策略对比，清晰展示每种优化方案的提升效果
 * - 支持导出性能报告（CSV格式）
 *
 * 使用场景:
 * - A/B测试不同优化策略
 * - 性能问题诊断
 * - 优化效果验证
 *
 * 架构设计:
 * - 单例模式：全局共享同一份性能数据
 * - 线程安全：使用Mutex保护并发访问
 * - 可配置：通过UserPreferencesDataStore控制是否启用
 *
 * @since 2025-12-03
 */
@UnstableApi
@Singleton
class CachePerformanceTracker @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    /**
     * 性能跟踪数据
     */
    data class PerformanceRecord(
        // 歌曲基本信息
        val songId: Long,
        val songTitle: String,
        val songArtist: String,
        val songDuration: Long,  // 歌曲时长(毫秒)

        // 缓存URL信息
        val url: String,
        val cacheKey: String,

        // 网络和策略信息
        val networkType: String,  // WiFi / Mobile / Unknown

        // 时间戳
        val startTime: Long,      // 开始缓存时间戳
        var firstByteTime: Long = 0L,  // 首字节到达时间戳（暂不支持，需自定义HttpDataSource）
        var endTime: Long = 0L,   // 缓存完成时间戳

        // 文件信息（完成时填充）
        var fileSize: Long = 0L,  // 文件大小(字节)
        var shardCount: Int = 0,  // 分片数量
        var audioFormat: String = "unknown",  // 音频格式（如 mp3, flac）
        var bitrate: Int = 0,     // 比特率（kbps）

        // 状态
        var isCompleted: Boolean = false,
        var isCancelled: Boolean = false
    ) {
        /**
         * 计算缓存耗时（毫秒）
         */
        fun getDuration(): Long {
            if (endTime == 0L || startTime == 0L) return 0L
            return endTime - startTime
        }

        /**
         * 计算首字节延迟（毫秒）
         */
        fun getFirstByteLatency(): Long {
            if (firstByteTime == 0L || startTime == 0L) return 0L
            return firstByteTime - startTime
        }

        /**
         * 计算平均速度（KB/s）
         */
        fun getAverageSpeed(): Double {
            val durationSeconds = getDuration() / 1000.0
            if (durationSeconds <= 0 || fileSize <= 0) return 0.0
            return (fileSize / 1024.0) / durationSeconds
        }

        /**
         * 计算完成进度（百分比）
         */
        fun getProgressPercentage(currentCachedBytes: Long): Int {
            if (fileSize <= 0) return 0
            return ((currentCachedBytes * 100) / fileSize).toInt().coerceIn(0, 100)
        }

        /**
         * 格式化为易读的性能报告
         */
        fun toFormattedReport(): String {
            return buildString {
                appendLine("========================================")
                appendLine("缓存性能报告")
                appendLine("========================================")
                appendLine("歌曲信息:")
                appendLine("  - ID: $songId")
                appendLine("  - 标题: $songTitle")
                appendLine("  - 艺术家: $songArtist")
                appendLine("  - 时长: ${formatDuration(songDuration)}")
                appendLine()
                appendLine("缓存URL:")
                appendLine("  - 原始URL: $url")
                appendLine("  - 缓存Key: $cacheKey")
                appendLine()
                appendLine("网络和策略:")
                appendLine("  - 网络类型: $networkType")
                appendLine()
                appendLine("性能指标:")
                appendLine("  - 文件大小: ${formatFileSize(fileSize)}")
                appendLine("  - 分片数量: $shardCount")
                appendLine("  - 音频格式: $audioFormat")
                appendLine("  - 比特率: ${bitrate} kbps")
                appendLine("  - 首字节延迟: ${getFirstByteLatency()} ms")
                appendLine("  - 总耗时: ${getDuration()} ms")
                appendLine("  - 平均速度: ${getAverageSpeed().roundToLong()} KB/s")
                appendLine("  - 完成状态: ${when {
                    isCancelled -> "已取消"
                    isCompleted -> "已完成"
                    else -> "进行中"
                }}")
                appendLine("========================================")
            }
        }

        /**
         * 导出为CSV行
         */
        fun toCsvRow(): String {
            return listOf(
                songId,
                "\"$songTitle\"",  // 用引号包裹，避免逗号问题
                "\"$songArtist\"",
                songDuration,
                "\"$url\"",
                "\"$cacheKey\"",
                networkType,
                fileSize,
                shardCount,
                audioFormat,
                bitrate,
                getFirstByteLatency(),
                getDuration(),
                getAverageSpeed().roundToLong(),
                when {
                    isCancelled -> "已取消"
                    isCompleted -> "已完成"
                    else -> "进行中"
                }
            ).joinToString(",")
        }

        companion object {
            /**
             * 格式化文件大小
             */
            fun formatFileSize(bytes: Long): String {
                return when {
                    bytes < 1024 -> "${bytes}B"
                    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
                }
            }

            /**
             * 格式化时长
             */
            fun formatDuration(ms: Long): String {
                val seconds = ms / 1000
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                return "%d:%02d".format(minutes, remainingSeconds)
            }

            /**
             * CSV头部
             */
            fun getCsvHeader(): String {
                return listOf(
                    "歌曲ID",
                    "歌曲标题",
                    "艺术家",
                    "歌曲时长(ms)",
                    "原始URL",
                    "缓存Key",
                    "网络类型",
                    "优化策略",
                    "文件大小(bytes)",
                    "分片数量",
                    "音频格式",
                    "比特率(kbps)",
                    "首字节延迟(ms)",
                    "总耗时(ms)",
                    "平均速度(KB/s)",
                    "完成状态"
                ).joinToString(",")
            }
        }
    }

    /**
     * 当前正在跟踪的缓存任务（URL -> PerformanceRecord）
     */
    private val activeTracking = mutableMapOf<String, PerformanceRecord>()

    /**
     * 已完成的性能记录（最多保存最近100条）
     */
    private val completedRecords = mutableListOf<PerformanceRecord>()
    private val maxCompletedRecords = 100

    /**
     * 互斥锁（保护并发访问）
     */
    private val mutex = Mutex()

    /**
     * 开始跟踪缓存性能
     *
     * @param songId 歌曲ID
     * @param songTitle 歌曲标题
     * @param songArtist 艺术家
     * @param songDuration 歌曲时长(毫秒)
     * @param url 完整URL（含token）
     * @param cacheKey 缓存Key（不含token）
     * @param networkType 网络类型
     * @param optimizationStrategy 优化策略
     */
    suspend fun startTracking(
        songId: Long,
        songTitle: String,
        songArtist: String,
        songDuration: Long,
        url: String,
        cacheKey: String,
        networkType: String,
    ) {
        // 检查是否启用调试模式和性能跟踪（2025-12-04优化：先检查调试模式）
        if (!userPreferencesDataStore.debugModeEnabled.value) {
            return
        }
        if (!userPreferencesDataStore.enableCachePerformanceTracking.value) {
            return
        }

        mutex.withLock {
            val record = PerformanceRecord(
                songId = songId,
                songTitle = songTitle,
                songArtist = songArtist,
                songDuration = songDuration,
                url = url,
                cacheKey = cacheKey,
                networkType = networkType,
                startTime = System.currentTimeMillis()
            )
            activeTracking[cacheKey] = record

            if (userPreferencesDataStore.enableCacheDetailedLogging.value) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachePerformanceTracker startTracking: 开始跟踪缓存 - $songTitle ($songArtist)"
                )
            }
        }
    }

    /**
     * 记录首字节到达时间
     *
     * 注意：此功能暂不支持，因为ExoPlayer的DefaultHttpDataSource没有暴露首字节回调
     * 如需支持，需要自定义HttpDataSource并重写open()方法
     *
     * @param cacheKey 缓存Key
     */
    @Deprecated("暂不支持，需自定义HttpDataSource")
    suspend fun recordFirstByte(cacheKey: String) {
        // 检查是否启用调试模式和性能跟踪（2025-12-04优化：先检查调试模式）
        if (!userPreferencesDataStore.debugModeEnabled.value) {
            return
        }
        if (!userPreferencesDataStore.enableCachePerformanceTracking.value) {
            return
        }

        mutex.withLock {
            val record = activeTracking[cacheKey] ?: return
            if (record.firstByteTime == 0L) {
                record.firstByteTime = System.currentTimeMillis()

                if (userPreferencesDataStore.enableCacheDetailedLogging.value) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CachePerformanceTracker recordFirstByte: ${record.songTitle} - 首字节延迟=${record.getFirstByteLatency()}ms"
                    )
                }
            }
        }
    }

    /**
     * 完成跟踪（缓存完成）
     *
     * @param cacheKey 缓存Key
     * @param simpleCache SimpleCache实例（用于提取文件大小和分片信息）
     */
    suspend fun completeTracking(cacheKey: String, simpleCache: SimpleCache) {
        // 检查是否启用调试模式和性能跟踪（2025-12-04优化：先检查调试模式）
        if (!userPreferencesDataStore.debugModeEnabled.value) {
            return
        }
        if (!userPreferencesDataStore.enableCachePerformanceTracking.value) {
            return
        }

        mutex.withLock {
            val record = activeTracking.remove(cacheKey) ?: return
            record.endTime = System.currentTimeMillis()
            record.isCompleted = true

            // 从SimpleCache提取文件信息
            try {
                val metadata = simpleCache.getContentMetadata(cacheKey)
                record.fileSize = metadata.get(ContentMetadata.KEY_CONTENT_LENGTH, 0L)

                // 尝试从URL推断音频格式
                record.audioFormat = extractAudioFormat(cacheKey)

                // 根据文件大小和歌曲时长估算比特率
                if (record.fileSize > 0 && record.songDuration > 0) {
                    val durationSeconds = record.songDuration / 1000.0
                    record.bitrate = ((record.fileSize * 8) / (1024.0 * durationSeconds)).toInt()
                }

                // TODO: 分片数量需要通过Cache.CacheSpan获取，暂时设为0
                record.shardCount = 0
            } catch (e: Exception) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachePerformanceTracker completeTracking: 提取文件信息失败: ${e.message}"
                )
            }

            // 保存到已完成记录
            completedRecords.add(0, record)  // 添加到开头
            if (completedRecords.size > maxCompletedRecords) {
                completedRecords.removeAt(completedRecords.lastIndex)  // 移除最旧的
            }

            // 输出性能报告
            if (userPreferencesDataStore.enableCacheDetailedLogging.value) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachePerformanceTracker completeTracking:\n${record.toFormattedReport()}"
                )
            } else {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachePerformanceTracker completeTracking: ${record.songTitle} - " +
                            "文件大小=${PerformanceRecord.formatFileSize(record.fileSize)}, " +
                            "耗时=${record.getDuration()}ms, " +
                            "速度=${record.getAverageSpeed().roundToLong()}KB/s"
                )
            }
        }
    }

    /**
     * 取消跟踪（缓存失败或被中断）
     *
     * @param cacheKey 缓存Key
     */
    suspend fun cancelTracking(cacheKey: String) {
        // 检查是否启用调试模式和性能跟踪（2025-12-04优化：先检查调试模式）
        if (!userPreferencesDataStore.debugModeEnabled.value) {
            return
        }
        if (!userPreferencesDataStore.enableCachePerformanceTracking.value) {
            return
        }

        mutex.withLock {
            val record = activeTracking.remove(cacheKey) ?: return
            record.isCancelled = true
            record.endTime = System.currentTimeMillis()

            if (userPreferencesDataStore.enableCacheDetailedLogging.value) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CachePerformanceTracker cancelTracking: ${record.songTitle} - 已取消"
                )
            }
        }
    }

    /**
     * 获取策略对比报告
     *
     * 对比不同优化策略的平均性能
     *
     * @return 格式化的对比报告
     */
    suspend fun getStrategyComparisonReport(): String = mutex.withLock {
        if (completedRecords.isEmpty()) {
            return "暂无性能数据"
        }

        val groupedByStrategy = completedRecords.groupBy { it.networkType }

        buildString {
            appendLine("========================================")
            appendLine("优化策略对比报告")
            appendLine("========================================")

            groupedByStrategy.forEach { (strategy, records) ->
                val avgSpeed = records.map { it.getAverageSpeed() }.average()
                val avgDuration = records.map { it.getDuration() }.average()
                val avgFileSize = records.map { it.fileSize }.average()
                val count = records.size

                appendLine()
                appendLine("策略: $strategy")
                appendLine("  - 样本数量: $count")
                appendLine("  - 平均文件大小: ${PerformanceRecord.formatFileSize(avgFileSize.toLong())}")
                appendLine("  - 平均耗时: ${avgDuration.roundToLong()} ms")
                appendLine("  - 平均速度: ${avgSpeed.roundToLong()} KB/s")
            }

            // 计算提升百分比
            val defaultRecords = groupedByStrategy["DEFAULT"] ?: emptyList()
            val optimizedRecords = groupedByStrategy["OPTIMIZED"] ?: emptyList()

            if (defaultRecords.isNotEmpty() && optimizedRecords.isNotEmpty()) {
                val defaultAvgSpeed = defaultRecords.map { it.getAverageSpeed() }.average()
                val optimizedAvgSpeed = optimizedRecords.map { it.getAverageSpeed() }.average()
                val improvement = ((optimizedAvgSpeed - defaultAvgSpeed) / defaultAvgSpeed * 100).roundToLong()

                appendLine()
                appendLine("性能提升:")
                appendLine("  - 优化策略比默认策略快 $improvement%")
            }

            appendLine("========================================")
        }
    }

    /**
     * 导出CSV报告
     *
     * @return CSV格式的性能报告
     */
    suspend fun exportCsvReport(): String = mutex.withLock {
        buildString {
            appendLine(PerformanceRecord.getCsvHeader())
            completedRecords.forEach { record ->
                appendLine(record.toCsvRow())
            }
        }
    }

    /**
     * 清空性能记录
     */
    suspend fun clearRecords() = mutex.withLock {
        activeTracking.clear()
        completedRecords.clear()
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "CachePerformanceTracker clearRecords: 已清空所有性能记录"
        )
    }

    /**
     * 从URL提取音频格式
     *
     * 示例: https://m701.music.126.net/.../file.mp3 -> mp3
     */
    private fun extractAudioFormat(url: String): String {
        return try {
            val lastSegment = url.substringAfterLast('/')
            val extension = lastSegment.substringAfterLast('.', "")
            if (extension.isNotEmpty() && extension.length <= 5) {
                extension.lowercase()
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
