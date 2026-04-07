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

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.SimpleCache
import com.minimalistmusic.util.LogConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * 缓存进度传输监听器
 *
 * 基线+累加策略：
 * 1. onTransferStart: 查询磁盘获取基线，重置累加值
 * 2. onBytesTransferred: 累加传输字节数，计算最终进度=基线+累加值
 * 3. onTransferEnd: 清理临时状态，保留基线用于下次传输
 * 4. 从 Content-Range 响应头获取完整文件大小
 * 5. 使用防抖机制避免频繁UI更新
 *
 * 下载生命周期管理 (2025-12-04新增):
 * - onDownloadStart: 下载开始时触发，用于添加临时保护
 * - onDownloadEnd: 下载结束时触发，用于清理中断下载的保护
 *
 * @param onProgressUpdate 进度更新回调 (url, bytesDownloaded, totalBytes, isNetwork)
 * @param onDownloadStart 下载开始回调 (url)，用于添加临时保护
 * @param onDownloadEnd 下载结束回调 (url, completed)，completed=false表示中断
 * @param emitIntervalMs 进度更新间隔，默认500ms
 * @param simpleCache ExoPlayer缓存实例，用于查询基线缓存量
 */
@UnstableApi
class CacheProgressTransferListener(
    private val onProgressUpdate: (url: String, bytesDownloaded: Long, totalBytes: Long, isNetwork: Boolean) -> Unit,
    private val onDownloadStart: ((url: String) -> Unit)? = null,
    private val onDownloadEnd: ((url: String, completed: Boolean) -> Unit)? = null,
    private val emitIntervalMs: Long = 500L,
    private val simpleCache: SimpleCache
) : TransferListener {

    /**
     * URL 完整文件大小映射表
     * Key: baseURL
     * Value: 完整文件总大小
     */
    private val fullFileSizeMap = ConcurrentHashMap<String, Long>()

    /**
     * 基线缓存量映射表
     * Key: baseURL
     * Value: 传输开始时磁盘已缓存的字节数
     */
    private val baselineCachedBytesMap = ConcurrentHashMap<String, Long>()

    /**
     * 累计传输字节数映射表
     * Key: baseURL
     * Value: 本次传输会话累计的字节数
     */
    private val accumulatedBytesMap = ConcurrentHashMap<String, Long>()

    /**
     * 上次发送进度的时间
     * Key: baseURL
     * Value: 时间戳
     */
    private val lastEmitTimeMap = ConcurrentHashMap<String, Long>()

    /**
     * 传输初始化
     *
     * 在数据传输开始前调用
     * 初始化防抖时间戳
     */
    override fun onTransferInitializing(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean
    ) {
        if (!isNetwork) return

        val baseUrl = getBaseUrl(dataSpec.uri)

        // 诊断日志：用于调试（通常 dataSpec.length 为 -1）
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            val lengthValue = dataSpec.length
            val isUnset = lengthValue == androidx.media3.common.C.LENGTH_UNSET.toLong()
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener onTransferInitializing: url=$baseUrl, " +
                        "dataSpec.length=$lengthValue, isLengthUnset=$isUnset"
            )
        }

        // 初始化防抖时间戳
        lastEmitTimeMap[baseUrl] = 0L
    }

    /**
     * 传输开始
     *
     * 在数据开始传输时调用
     * 查询磁盘缓存作为基线，重置累加值
     *
     * 修复 (2025-12-04): 添加下载生命周期管理
     * - 触发 onDownloadStart 回调，用于添加临时保护
     */
    override fun onTransferStart(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean
    ) {
        if (!isNetwork) return

        val baseUrl = getBaseUrl(dataSpec.uri)

        // 步骤0: 触发下载开始回调（新增 2025-12-04）
        // 用于在首个chunk到达前就添加临时保护
        onDownloadStart?.invoke(baseUrl)

        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener onTransferStart: 下载开始，已触发临时保护回调, url=$baseUrl"
            )
        }

        // 步骤1: 从 HTTP 响应头获取完整文件大小
        try {
            (source as? androidx.media3.datasource.HttpDataSource)?.let { httpSource ->
                val headers = httpSource.responseHeaders

                // 提取完整文件大小（兼容 Range 请求和完整请求）
                val fullFileSize = extractFullFileSize(headers)

                if (fullFileSize > 0) {
                    // 使用 putIfAbsent 确保只记录一次，避免被后续 Range 请求覆盖
                    val previousSize = fullFileSizeMap.putIfAbsent(baseUrl, fullFileSize)

                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        if (previousSize == null) {
                            LogConfig.d(
                                LogConfig.TAG_PLAYER_DATA_LOCAL,
                                "CacheProgressTransferListener onTransferStart: 从HTTP响应头获取完整文件大小=${fullFileSize.formatBytes()}"
                            )
                        } else if (previousSize != fullFileSize) {
                            LogConfig.w(
                                LogConfig.TAG_PLAYER_DATA_LOCAL,
                                "CacheProgressTransferListener onTransferStart: 文件大小不一致！已记录=${previousSize.formatBytes()}, " +
                                        "新获取=${fullFileSize.formatBytes()}"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheProgressTransferListener onTransferStart: 获取文件大小失败: ${e.message}"
                )
            }
        }

        // 步骤2: 查询磁盘缓存作为基线（本次传输会话只查询一次）
        val fullFileSize = fullFileSizeMap[baseUrl] ?: -1L
        val baseline = if (fullFileSize > 0) {
            try {
                simpleCache.getCachedBytes(baseUrl, 0, fullFileSize)
            } catch (e: Exception) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheProgressTransferListener onTransferStart: 查询基线缓存失败: ${e.message}"
                    )
                }
                0L
            }
        } else {
            0L
        }
        baselineCachedBytesMap[baseUrl] = baseline

        // 步骤3: 重置累计字节数
        // 每次 onTransferStart 都是新的传输会话（首次播放或Seek后）
        // 重置累计值，重新开始累加
        accumulatedBytesMap[baseUrl] = 0L

        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener onTransferStart: url=$baseUrl, 基线=${baseline.formatBytes()}, 已重置累加值"
            )
        }
    }

    /**
     * 数据传输中
     *
     * 每次传输数据块时调用
     * 累加传输字节数，计算最终进度
     *
     * @param bytesTransferred 本次传输的字节数
     */
    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int
    ) {
        // 只监听网络传输（首次缓存），忽略从缓存读取
        if (!isNetwork) return

        val baseUrl = getBaseUrl(dataSpec.uri)

        // 关键修复：先累加字节数（无条件），再判断是否发送更新（防抖）
        // 累加本次传输的字节数（必须在防抖之前）
        val currentAccumulated = accumulatedBytesMap.getOrDefault(baseUrl, 0L)
        val newAccumulated = currentAccumulated + bytesTransferred
        accumulatedBytesMap[baseUrl] = newAccumulated

        // 防抖：避免过于频繁的UI更新（但不影响累加）
        val now = System.currentTimeMillis()
        val lastEmitTime = lastEmitTimeMap.getOrDefault(baseUrl, 0L)
        if (now - lastEmitTime < emitIntervalMs) {
            return  // 不发送更新，但字节数已累加
        }

        // 获取完整文件大小
        val fullFileSize = fullFileSizeMap[baseUrl] ?: -1L
        if (fullFileSize <= 0) {
            return  // 文件大小未知，无法计算进度
        }

        // 计算最终进度：基线 + 累加值
        val baseline = baselineCachedBytesMap.getOrDefault(baseUrl, 0L)
        val totalCached = baseline + newAccumulated

        // 触发进度更新回调
        onProgressUpdate(baseUrl, totalCached, fullFileSize, true)
        lastEmitTimeMap[baseUrl] = now

        // 日志输出（架构优化）
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            val progress = ((totalCached * 100) / fullFileSize).toInt()
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener 缓存进度: $progress% " +
                        "(${totalCached.formatBytes()}/${fullFileSize.formatBytes()}) " +
                        "[基线=${baseline.formatBytes()}, 累加=${newAccumulated.formatBytes()}]"
            )
        }
    }

    /**
     * 传输结束
     *
     * 在数据传输完成时调用
     *
     * 架构优化 (2025-12-04):
     * - 清理临时状态（防抖时间戳、累加值）
     * - 保留基线和文件大小（用于下次传输）
     * - 触发 onDownloadEnd 回调，用于清理中断下载的临时保护
     */
    override fun onTransferEnd(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean
    ) {
        if (!isNetwork) return

        val baseUrl = getBaseUrl(dataSpec.uri)

        // 计算最终进度并发送最后一次更新
        val fullFileSize = fullFileSizeMap[baseUrl] ?: -1L
        val baseline = baselineCachedBytesMap[baseUrl] ?: 0L
        val accumulated = accumulatedBytesMap[baseUrl] ?: 0L
        val totalCached = baseline + accumulated

        if (fullFileSize > 0 && totalCached > 0) {
            onProgressUpdate(baseUrl, totalCached, fullFileSize, true)
        }

        // 判断下载是否完成（修复 2025-12-04）
        // completed = true: 文件完整下载完成
        // completed = false: 下载中断（用户切歌、网络错误等）
        val completed = fullFileSize > 0 && totalCached >= fullFileSize

        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener onTransferEnd: url=$baseUrl, " +
                        "totalCached=${totalCached.formatBytes()}, fullFileSize=${fullFileSize.formatBytes()}, " +
                        "completed=$completed"
            )
        }

        // 触发下载结束回调（新增 2025-12-04）
        // 用于清理中断下载的临时保护
        onDownloadEnd?.invoke(baseUrl, completed)

        // 清理临时数据
        lastEmitTimeMap.remove(baseUrl)
        accumulatedBytesMap.remove(baseUrl)
        // 注意：保留 fullFileSizeMap 和 baselineCachedBytesMap 用于下次传输
    }

    /**
     * 提取完整文件大小（修复 2025-12-04）
     *
     * 支持 HTTP Range 请求和完整请求：
     * 1. 优先从 Content-Range 响应头获取（Range 请求场景）
     *    格式: "bytes 6850695-10082263/10082264"
     *                                ^^^^^^^^ 完整文件大小
     * 2. 回退到 Content-Length（首次完整请求场景）
     *
     * 为什么需要这样做？
     * - Range 请求的 Content-Length 是片段大小，不是完整文件大小
     * - 用户拖动进度条会触发 Range 请求，导致文件大小显示错误
     * - 只有 Content-Range 的最后一个数字才是完整文件大小
     *
     * @param headers HTTP 响应头
     * @return 完整文件大小（字节），失败返回 -1
     */
    private fun extractFullFileSize(headers: Map<String, List<String>>): Long {
        // 1. 优先从 Content-Range 获取完整文件大小
        //    示例: "bytes 6850695-10082263/10082264" -> 10082264
        headers["Content-Range"]?.firstOrNull()?.let { contentRange ->
            try {
                val regex = Regex("""bytes\s+\d+-\d+/(\d+)""")
                regex.find(contentRange)?.groupValues?.get(1)?.toLongOrNull()?.let { totalSize ->
                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "CacheProgressTransferListener extractFullFileSize: 从Content-Range获取完整大小=$totalSize, " +
                                    "原始值='$contentRange'"
                        )
                    }
                    return totalSize
                }
            } catch (e: Exception) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheProgressTransferListener extractFullFileSize: 解析Content-Range失败: ${e.message}"
                )
            }
        }

        // 2. 回退到 Content-Length（首次完整请求）
        headers["Content-Length"]?.firstOrNull()?.toLongOrNull()?.let { contentLength ->
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheProgressTransferListener extractFullFileSize: 从Content-Length获取大小=$contentLength"
                )
            }
            return contentLength
        }

        // 3. 都失败了
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener extractFullFileSize: 无法获取文件大小，响应头: $headers"
            )
        }
        return -1L
    }

    /**
     * 从完整URL中提取基础URL（不含查询参数）
     *
     * 与 AudioCacheManager.getBaseUrl 保持一致
     */
    private fun getBaseUrl(uri: Uri): String {
        return try {
            buildString {
                append(uri.scheme ?: "https")
                append("://")
                append(uri.host ?: "")
                append(uri.path ?: "")
            }
        } catch (e: Exception) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheProgressTransferListener getBaseUrl解析失败: ${e.message}, 使用原URL"
            )
            uri.toString()
        }
    }

    /**
     * 格式化字节数（修复精度）
     */
    private fun Long.formatBytes(): String {
        return when {
            this < 0 -> "Unknown"
            this < 1024 -> "${this}B"
            this < 1024 * 1024 -> "${this / 1024}KB"
            else -> String.format("%.2fMB", this / 1024.0 / 1024.0)
        }
    }
}
