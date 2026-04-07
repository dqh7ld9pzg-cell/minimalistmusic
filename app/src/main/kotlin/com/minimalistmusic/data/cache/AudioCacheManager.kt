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
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频缓存管理器
 *
 * 功能:
 * - 管理ExoPlayer的音频缓存
 * - 配置缓存策略（LRU，可自定义最大大小）
 * - 提供缓存数据源工厂
 * - 支持缓存清理和查询
 *
 * 性能优化:
 * - 使用SimpleCache实现音频文件缓存
 * - LRU策略自动清理旧缓存
 * - 缓存最大空间支持动态函数拓展
 */
@UnstableApi
@Singleton
class AudioCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheConfig: CacheConfig, // 重构: 依赖配置层，打破循环依赖
    private val cachedSongDao: CachedSongDao, // 新增 白名单检查
) {

    private val _cacheCompletedEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val cacheCompletedEvents: SharedFlow<String> = _cacheCompletedEvents.asSharedFlow()

    private val _cacheRemovedEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val cacheRemovedEvents: SharedFlow<String> = _cacheRemovedEvents.asSharedFlow()

    /**
     * 缓存进度更新事件流
     *
     * 架构重构：基于 TransferListener 的实时进度监听
     * - 数据来源：CacheProgressTransferListener.onBytesTransferred
     * - 更新频率：100ms（防抖）
     * - 用途：在 PlayerScreen 显示缓存进度条
     */
    private val _cacheProgressEvents = MutableSharedFlow<Triple<String, Long, Long>>(
        replay = 0,
        extraBufferCapacity = 10
    )
    val cacheProgressEvents: SharedFlow<Triple<String, Long, Long>> =
        _cacheProgressEvents.asSharedFlow()

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 缓存Key生成记录
     *
     * 用途:
     * - 记录已生成过Key的URL，避免重复打印日志
     * - ExoPlayer在播放过程中会频繁调用setCacheKeyFactory（每次读取数据块）
     * - 使用HashSet快速判断URL是否已记录
     */
    private val generatedCacheKeys = mutableSetOf<String>()

    /**
     * 缓存进度传输监听器
     *
     * 架构重构：基于 TransferListener 的实时进度监听
     * - 监听 ExoPlayer 数据传输事件
     * - 实时更新缓存进度
     * - 通过 Flow 发送进度事件
     */
    /**
     * 正在处理完成事件的URL集合（修复 2025-12-04）
     *
     * 用途：防止竞态条件导致刚完成缓存的歌曲被LRU立即删除
     *
     * 工作流程：
     * 1. onTransferStart 触发时，立即添加临时保护（保护所有chunk）
     * 2. onTransferEnd 触发时，如果下载中断则移除保护
     * 3. 如果下载完成，保护继续保留，直到 handleCacheCompletion() 插入数据库后移除
     *
     * 线程安全：使用 ConcurrentHashMap 确保并发安全
     *
     * 超时机制 (2025-12-04新增):
     * - Key: URL
     * - Value: 添加时间戳（用于超时检测）
     * - 超过5分钟自动清理（异常情况兜底）
     */
    private val processingCompletionUrls = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * 缓存进度传输监听器（修复 2025-12-04）
     *
     * 修改为 lazy 初始化：
     * - 确保在 simpleCache 初始化后创建
     * - 传入 simpleCache 参数用于查询实际缓存量
     * - 移除了回退方案（CacheProgressTransferListener内部已精确处理）
     *
     * 下载生命周期管理 (2025-12-04新增):
     * - onDownloadStart: 下载开始时立即添加临时保护
     * - onDownloadEnd: 下载结束时清理中断下载的保护
     */
    private val progressTransferListener by lazy {
        CacheProgressTransferListener(
            onProgressUpdate = { url, bytesDownloaded, totalBytes, isNetwork ->
                // 修复：移除回退方案
                // CacheProgressTransferListener 内部已通过混合策略（累加+磁盘查询）获取准确进度
                // totalBytes 已经是完整文件大小（从 Content-Range 或 Content-Length 获取）
                // bytesDownloaded 是累加值和磁盘缓存的最大值

                // 通过 Flow 发送进度更新事件
                cacheScope.launch {
                    _cacheProgressEvents.emit(Triple(url, bytesDownloaded, totalBytes))
                }
            },
            onDownloadStart = { url ->
                // 下载开始：立即添加临时保护（保护所有chunk）
                val currentTime = System.currentTimeMillis()
                processingCompletionUrls[url] = currentTime
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager onDownloadStart: 下载开始，添加临时保护, url=$url"
                )
            },
            onDownloadEnd = { url, completed ->
                if (!completed) {
                    // 下载中断：移除临时保护
                    processingCompletionUrls.remove(url)
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "AudioCacheManager onDownloadEnd: 下载中断，移除临时保护, url=$url"
                    )
                }
                // 如果 completed=true，保护继续保留，等待 handleCacheCompletion() 处理
            },
            emitIntervalMs = 500L,  // 优化：500ms更新一次，减少回调频率
            simpleCache = simpleCache
        )
    }

    /**
     * 缓存目录
     *
     * 重构 : 从 CacheConfig 获取
     */
    private val cacheDir: File by lazy {
        cacheConfig.getCacheDir()
    }

    /**
     * SimpleCache实例
     *
     * 策略 (2025-11-12更新):
     * - LRU驱逐策略：缓存满时自动删除最久未使用的文件
     * - 可自定义最大缓存大小：从UserPreferencesDataStore读取（默认200MB）
     * - 范围：50MB-2GB
     *
     * 注意: 修改缓存大小后需要重启应用才能生效（因为SimpleCache在创建时固定大小）
     *
     * 修改 (2025-11-15):
     * - 改为internal可见性，允许CacheStateManager访问以监听缓存进度
     *
     * 白名单保护 (2025-11-22):
     * - 集成白名单保护功能，防止ExoPlayer自动删除白名单歌曲
     * - 白名单歌曲在LRU清理时会被跳过
     *
     */

    internal val simpleCache: SimpleCache by lazy { createSimpleCache() }

    /**
     * 创建只读模式的缓存数据源
     *
     * 用途：缓存总开关关闭时使用
     * - 允许读取已有缓存文件
     * - 不写入新的缓存
     * - 避免播放已缓存歌曲时出错
     *
     * 工作流程:
     * 1. 优先从缓存读取
     * 2. 缓存未命中时从网络下载
     * 3. 不写入缓存
     *
     * @return 只读模式的 DataSource.Factory
     */
    fun createReadOnlyCacheDataSourceFactory(): DataSource.Factory {
        val baseUpstreamFactory = DefaultDataSource.Factory(context)

        val upstreamFactory = DataSource.Factory {
            val dataSource = baseUpstreamFactory.createDataSource()
            dataSource.addTransferListener(progressTransferListener)
            dataSource
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager createReadOnlyCacheDataSourceFactory: 创建只读缓存数据源"
        )

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setCacheKeyFactory { dataSpec ->
                val baseUrl = getBaseUrl(dataSpec.uri)
                if (generatedCacheKeys.add(baseUrl)) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "AudioCacheManager [只读模式] 生成缓存Key: $baseUrl"
                    )
                }
                baseUrl
            }
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)  // 不写入缓存
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 创建支持缓存的DataSource.Factory
     *
     * 工作流程:
     * 1. 优先从缓存读取
     * 2. 缓存未命中时从网络下载
     * 3. 下载的同时写入缓存
     * 4. 下次播放直接从缓存读取
     *
     * FLAG说明：
     * - FLAG_BLOCK_ON_CACHE: 阻塞读取直到缓存可用（用于多个播放器实例共享缓存）
     * - FLAG_IGNORE_CACHE_ON_ERROR: 缓存错误时回退到网络
     * - 不设置flags: 使用默认行为（读写缓存）
     *
     * 修复方案：
     * - 移除所有flags，使用ExoPlayer默认缓存行为
     * - 保留自定义CacheKeyFactory
     *
     * @return 支持缓存的DataSource.Factory
     */
    fun createCacheDataSourceFactory(): DataSource.Factory {
        // 上游数据源：使用DefaultDataSource支持多种协议（http、file、asset等）
        val baseUpstreamFactory = DefaultDataSource.Factory(context)

        // 包装上游数据源，添加 TransferListener
        val upstreamFactory = DataSource.Factory {
            val dataSource = baseUpstreamFactory.createDataSource()
            // 注册进度监听器
            dataSource.addTransferListener(progressTransferListener)
            dataSource
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager    ========================================"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager createCacheDataSourceFactory 创建ExoPlayer缓存数据源"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager      缓存目录: ${cacheDir.absolutePath}"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager      已使用缓存空间: ${simpleCache.cacheSpace.formatCacheSize()}"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager      最大缓存空间: ${
                cacheConfig.getMaxCacheBytes().formatCacheSize()
            }"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager    ========================================"
        )
        // 返回支持缓存的数据源工厂
        // 修复v3 (2025-11-13): 使用ExoPlayer默认缓存行为，不设置任何flags
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setCacheKeyFactory { dataSpec ->
                /**
                 * 修复 (2025-11-25): 使用不含查询参数的基础URL作为缓存key
                 *
                 * 问题根源：
                 * - 网易云音乐的播放URL包含时效性token（如 ?vuutv=...）
                 * - 同一首歌曲的token会变化，导致cache key不匹配
                 * - 离线播放时找不到缓存，尝试网络加载失败
                 *
                 * 解决方案：
                 * - 移除查询参数，只使用基础URL（scheme + host + path）
                 * - 确保cache key稳定，不受token变化影响
                 *
                 * 示例：
                 * 完整URL: https://m701.music.126.net/.../file.mp3?vuutv=TOKEN_A
                 * Cache Key: https://m701.music.126.net/.../file.mp3
                 */

                val baseUrl = getBaseUrl(dataSpec.uri)
                // ExoPlayer在播放过程中会频繁调用此方法（每次读取数据块时）
                // 避免日志刷屏，只记录首次生成
                if (generatedCacheKeys.add(baseUrl)) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "AudioCacheManager 生成稳定缓存Key: $baseUrl (原URL包含token: ${dataSpec.uri.query})"
                    )
                }
                baseUrl  // 返回不含查询参数的稳定key
            } // 设置事件监听器，监控缓存状态
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createOptimizeCacheDataSourceFactory(): DataSource.Factory {
        // 优化策略：自定义DataSource配置
        // 核心优化点：
        // 1. LoadControl：更大的缓冲区（10分钟 vs 默认30秒）→ 减少频繁的网络请求
        // 2. LoadControl：更高的目标字节数（100MB vs 默认13MB）→ 一次性下载更多数据
        // 3. LoadControl：更大的内存块（64KB vs 16KB）→ 减少内存分配开销
        // 4. CacheDataSink：增大写入缓冲区（512KB vs 默认20KB）→ 减少磁盘IO次数
        //
        // 为什么增大写入缓冲区能提速？
        // - 默认20KB：写入8MB需要400次write()系统调用
        // - 优化512KB：写入8MB仅需16次write()系统调用
        // - 减少磁盘IO开销，提升整体吞吐量（实测提升20-30%）

        // 修复：使用DefaultDataSource支持多种协议
        val baseDataSourceFactory = DefaultDataSource.Factory(context)

        // 包装DataSource，添加TransferListener
        val upstreamFactory = DataSource.Factory {
            val dataSource = baseDataSourceFactory.createDataSource()
            // 注册进度监听器（架构优化 2025-12-04：基线+累加策略）
            dataSource.addTransferListener(progressTransferListener)
            dataSource
        }

        // 创建自定义的CacheDataSink.Factory，增大写入缓冲区
        val cacheWriteDataSinkFactory = CacheDataSink.Factory()
            .setCache(simpleCache)
            .setBufferSize(512 * 1024)  // 512KB写入缓冲区（默认20KB）

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setCacheKeyFactory { dataSpec ->
                val baseUrl = AudioCacheManager.getBaseUrl(dataSpec.uri)
                baseUrl
            }
            .setUpstreamDataSourceFactory(upstreamFactory)  // 使用包装后的upstreamFactory
            .setCacheWriteDataSinkFactory(cacheWriteDataSinkFactory)  // 使用优化的写入缓冲区
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 检查歌曲是否已缓存音频数据 (2025-11-13)
     *
     * 判断逻辑：
     * - 本地歌曲：始终返回true（文件在本地存储，无需缓存）
     * - 在线歌曲：检查ExoPlayer是否缓存了该URL的音频数据
     *
     * 修复 (2025-11-18):
     * - 添加严格的完整性检查，确保文件完整缓存
     * - 检查文件长度信息是否有效（len != Long.MAX_VALUE）
     * - 检查缓存字节数是否等于文件总长度
     * - 避免将部分缓存的文件判定为已缓存
     *
     * 修复 (2025-11-24 �16:35): 移除不可靠的 keys.contains() 检查
     * - 问题：simpleCache.keys 是惰性加载的，应用重启后 keys 集合可能为空或不完整
     * - 原因：SimpleCache 的缓存索引只有在访问特定URL时才会从数据库加载到内存
     * - 现象：数据库标记已缓存，但 keys.any { it == url } 返回 false，导致误删缓存记录
     * - 解决：直接使用 getContentMetadata() 和 isCached() 方法，这些方法会正确加载缓存span
     * - 详见文档：docs/2025-11-24-修复缓存key检查不可靠问题.md
     *
     * 注意：此方法仅检查音频文件缓存，不检查URL缓存（URL缓存由Repository管理）
     *
     * @param url 歌曲的播放URL（本地路径或在线URL）
     * @param isLocal 是否为本地歌曲
     * @return true表示已缓存或为本地歌曲
     */
    fun isSongCached(url: String?, isLocal: Boolean): Boolean {
        // 本地歌曲无需缓存检查
        if (isLocal) return true
        // 在线歌曲：检查URL是否有效且已缓存
        if (url.isNullOrBlank()) return false
        return try {
            // debugCacheStatus(url)
            // ========== 旧代码 (2025-11-24 已废弃) ==========
            // 问题：simpleCache.keys 是惰性加载的，应用重启后该集合可能为空
            // 原因：SimpleCache 内部使用 HashMap 存储已加载的缓存key，但这个 HashMap
            //       只有在调用 startReadWrite(key) 或类似方法时才会从 StandaloneDatabaseProvider
            //       加载对应的缓存span信息到内存中
            // 现象：
            //   - 第一天：播放歌曲A -> URL被缓存 -> keys包含URL_A
            //   - 应用被杀死/设备重启
            //   - 第二天：SimpleCache重新初始化 -> 缓存文件仍在磁盘 -> 但keys为空
            //   - 调用 keys.any { it == url } -> 返回false -> 误判为未缓存
            //   - 数据库记录被错误删除
            //
            // val keys = simpleCache.keys.toList()
            // val keyExists = keys.any { it == url }
            // if (!keyExists) {
            //     LogConfig.d(
            //         LogConfig.TAG_PLAYER_DATA_LOCAL,
            //         "AudioCacheManager    isSongCached: URL不在缓存keys中，未缓存"
            //     )
            //     return false
            // }
            // ========== 旧代码结束 ==========
            // 修复 (2025-11-24): 直接使用 getContentMetadata()，它会正确从磁盘加载缓存元数据
            // getContentMetadata() 内部会调用 startReadWrite() 触发缓存span的加载
            // 1. 获取文件长度信息
            val len = simpleCache.getContentMetadata(url)
                .get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            // 文件长度信息无效，说明确实没有缓存过该URL（元数据不存在）
            if (len <= 0) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager isSongCached: 无内容长度元数据 (len=$len)，判定为未缓存"
                )
                return false
            }
            // 2. 检查是否完整缓存
            val cachedLength = simpleCache.getCachedLength(url, 0, len)
            val isCached = simpleCache.isCached(url, 0, len)
            // 双重验证 - isCached必须为true且缓存字节数等于文件总长度
            val isFullyCached = isCached && (cachedLength == len)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager isSongCached: url=$url, 文件大小=${len.formatCacheSize()}, 已缓存=${cachedLength.formatCacheSize()}, " +
                        "isCached=$isCached, 完整性检查=$isFullyCached"
            )
            return isFullyCached
        } catch (e: Exception) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager isSongCached 检查缓存失败: ${e.message}"
            )
            false
        }
    }

    /**
     * 删除单个歌曲的缓存 (2025-11-14)
     *
     * 功能：
     * - 删除指定URL的音频缓存
     * - 用于"已缓存音乐"列表中长按删除功能
     *
     * @param url 要删除的歌曲URL
     * @return 是否删除成功
     */
    fun removeCachedSong(url: String): Boolean {
        return try {
            simpleCache.removeResource(url)
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "AudioCacheManager    删除缓存成功: $url")
            true
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager removeCachedSong: 删除缓存失败: ${e.message}"
            )
            false
        }
    }

    /**
     * 从数据库中删除缓存记录
     *
     * 架构重构：
     * - 直接通过 URL 删除 cached_songs 表记录
     * - 由 ExoPlayer LRU 清理回调触发
     *
     * @param url 缓存的 URL（base URL）
     */
    fun removeCachedSongFromDatabase(url: String) {
        cacheScope.launch {
            try {
                cachedSongDao.deleteCachedSongByCacheKey(url)
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager 删除缓存记录成功: url=$url"
                )
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager 删除缓存记录失败: url=$url, error=${e.message}"
                )
            }
        }
    }

    /**
     * 清空所有音频缓存 (2025-11-25修复 - 混合方案)
     *
     * 策略（结合 API 清理和物理删除，彻底清空且不会崩溃）：
     * 1. 使用 removeResource() 清理 SimpleCache 的元数据
     * 2. 直接删除缓存目录下的所有文件（彻底清理）
     * 3. 不调用 release()，避免 ExoPlayer 崩溃
     * 4. 重建空目录，供下次缓存使用
     *
     * 为什么使用混合方案：
     * - removeResource() 依赖 keys，但 keys 是惰性加载的（可能不完整）
     * - 直接删除文件确保物理文件被清空（彻底清理）
     * - 不调用 release() 避免 IllegalStateException（线程安全）
     * - SimpleCache 会自动检测文件消失并更新内部状态
     *
     * 注意：
     * - 不会抛出 IllegalStateException
     * - 当前正在播放的歌曲可能需要重新缓存
     * - SimpleCache 继续运行，自动处理文件消失的情况
     *
     * 修复 (2025-11-25): 混合方案 - removeResource() + 删除文件
     * - 既清理元数据，又彻底删除文件
     * - 不调用 release()，避免崩溃
     */
    fun clearCache() {
        try {
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "AudioCacheManager 开始清空缓存...")
            // 步骤1: 使用 SimpleCache API 清理元数据（尽力而为，不强求完整）
            val keys = try {
                simpleCache.keys.toList()
            } catch (e: Exception) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager 获取缓存keys失败: ${e.message}"
                )
                emptyList()
            }
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager 找到 ${keys.size} 个缓存资源（keys可能不完整）"
            )
            var removedCount = 0
            keys.forEach { key ->
                try {
                    simpleCache.removeResource(key)
                    removedCount++
                } catch (e: Exception) {
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "AudioCacheManager 删除资源失败: key=$key, error=${e.message}"
                    )
                }
            }
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager 已清理 $removedCount 个缓存元数据"
            )
            // 步骤2: 直接删除缓存目录下的所有文件（彻底清理）
            if (cacheDir.exists()) {
                val fileCount = cacheDir.listFiles()?.size ?: 0
                val totalSize = cacheDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
                // 删除所有文件但保留目录结构
                cacheDir.listFiles()?.forEach { file ->
                    try {
                        if (file.isFile) {
                            file.delete()
                        } else if (file.isDirectory) {
                            file.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        LogConfig.w(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "AudioCacheManager 删除文件失败: ${file.name}, error=${e.message}"
                        )
                    }
                }
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager 已删除缓存文件: ${fileCount}个文件，释放${totalSize.formatCacheSize()}"
                )
            } else {
                LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "AudioCacheManager 缓存目录不存在")
            }
            // 步骤3: 确保缓存目录存在（为下次缓存做准备）
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
                LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "AudioCacheManager 已重建缓存目录")
            }
            LogConfig.d(LogConfig.TAG_PLAYER_DATA_LOCAL, "AudioCacheManager 清空缓存完成")
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager 清空缓存失败: ${e.message}"
            )
        }
    }

    /**
     * 释放缓存资源
     *
     * 注意: 仅在应用退出时调用
     */
    fun release() {
        try {
            simpleCache.release()
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager release 释放资源时失败: ${e.message}"
            )
        }
    }

    fun Long.formatCacheSize(): String {
        return if (this < 1024 * 1024) "${this / 1024} KB" else "${this / (1024 * 1024)} MB"
    }


    fun debugCacheStatus(url: String) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager debugCacheStatus ========================================"
        )
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager debugCacheStatus 缓存状态调试 - URL: $url"
        )
        try {
            // 1. 检查是否在keys中
            val keys = simpleCache.keys.toList()
            val keyExists = keys.any { it == url }
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager debugCacheStatus Key存在于缓存keys中: $keyExists"
            )
            val len = simpleCache.getContentMetadata(url)
                .get(ContentMetadata.KEY_CONTENT_LENGTH, Long.MAX_VALUE)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager debugCacheStatus 获取url为 $url 的音频文件大小: $len"
            )
            // 2. 检查缓存状态(踩坑:最初length参数使用的Long.MAX_VALUE导致isCached始终返回false,看接口这里len指的是内容音频文件大小)
            val isCached = simpleCache.isCached(url, 0, len)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager debugCacheStatus url:$url simpleCache.isCached的结果: $isCached"
            )
            // 3. 获取缓存内容长度
            val contentLength = simpleCache.getCachedLength(url, 0, Long.MAX_VALUE)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager debugCacheStatus 已缓存的内容大小: ${contentLength.formatCacheSize()}"
            )
            // 4. 获取缓存空间使用情况
            val cacheSpace = simpleCache.cacheSpace
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager debugCacheStatus 总缓存空间simpleCache.cacheSpace大小: ${cacheSpace.formatCacheSize()} "
            )
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager debugCacheStatus: 调试缓存状态失败",
                e
            )
        }
    }


    /**
     * 创建SimpleCache实例
     */
    private fun createSimpleCache(): SimpleCache {
        /**
         * 优化 (2025-11-25): 移除固定maxBytes计算，改用动态函数引用
         * 旧方案（已废弃）：
         * val cacheSizeMB = userPreferences.cacheSizeMB
         * val cacheSizeBytes = cacheSizeMB * 1024L * 1024L
         * 问题：
         * - 固定映射公式：maxCachedSongs * 8MB * 1.5
         * - 歌曲大小不一定是8MB（实际范围4-15MB）
         * - 映射系数可能不够（如果歌曲都是12MB）
         * - 无法保证能缓存到上限数量
         * - 修改上限后需要重启应用才生效
         */
        return SimpleCache(
            cacheDir,
            ProtectedLruCacheEvictor(
                /**
                 * 优化 (2025-12-04): 解决无限增长问题
                 * - 问题：currentCacheSpace包含碎片，可能无限增长
                 * - 方案：达到上限后，使用实际歌曲大小而非currentCacheSpace
                 *
                 * 动态计算公式：
                 * - 未达上限：maxBytes = currentCacheSpace + 100MB
                 * - 达到上限：maxBytes = actualSongSize + 100MB
                 *
                 * 优势：
                 * - 保证上限：无论歌曲大小，都能缓存到maxCachedSongs首
                 * - 防止无限增长：达到上限后使用精确值，不会因碎片累积而增长
                 * - 固定buffer：100MB buffer减少清理频率
                 * - 实时生效：修改上限后无需重启，立即响应
                 *
                 * 重构历史：
                 * - 2025-11-25: 首次实现动态maxBytes机制
                 * - 2025-12-02: 移除循环依赖，从CacheStateManager迁移到CacheConfig
                 * - 2025-12-04: 优化为分段计算，解决无限增长问题
                 */
                getMaxBytes = {
                    val currentCacheSpace = simpleCache.cacheSpace

                    // 查询当前已缓存歌曲数量和实际占用大小
                    val currentCachedCount = runBlocking {
                        cachedSongDao.getCachedSongCount()
                    }
                    val actualSongSize = runBlocking {
                        cachedSongDao.getTotalCacheSize() ?: 0L
                    }

                    cacheConfig.getDynamicMaxBytes(
                        currentCacheSpace,
                        currentCachedCount,
                        actualSongSize
                    )
                },
                isProtected = isProtected@{ url ->
                    /**
                     * 缓存保护检查函数 (2025-11-25 方案B优化版本)
                     *
                     * 设计理念：双层管理机制
                     * - ExoPlayer层（本函数）：只负责保护"已缓存列表"中的所有歌曲
                     * - 业务层（CacheStateManager/Repository）：负责白名单、上限管理等业务逻辑
                     *
                     * 保护规则：
                     * - 只要歌曲在数据库中标记为 isFullyCached=true，就保护
                     * - 不关心白名单、上限等业务概念
                     * - 职责明确：ExoPlayer层只管物理文件保护
                     *
                     * 优化收益：
                     * - 代码简洁：减少50%+的逻辑
                     * - 性能优化：只需一次主键查询，无需复杂SQL
                     * - 职责分离：业务逻辑在业务层处理
                     *
                     * - 参考文档：docs/2025-11-25-缓存上限机制和ExoPlayer-LRU缓存清理机制的协调.md
                     */
                    try {
                        val songId = getSongIdByUrl(url) ?: return@isProtected false
                        val cachedSong = cachedSongDao.getCachedSong(songId)
                        // 简化判断：只要是完整缓存的歌曲，就保护
                        val protected = cachedSong?.isFullyCached == true
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "AudioCacheManager 保护检查: songId=$songId, " +
                                    "isFullyCached=${cachedSong?.isFullyCached}, protected=$protected"
                        )
                        return@isProtected protected
                    } catch (e: Exception) {
                        LogConfig.w(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "AudioCacheManager 保护检查失败: ${e.message}"
                        )
                        false // 查询失败时不保护，允许删除
                    }
                },
                onCacheRemoved = { removedKey ->
                    // 当缓存被移除时的回调（删除数据库记录）
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "AudioCacheManager onCacheRemoved: url=$removedKey"
                    )
                    removeCachedSongFromDatabase(removedKey)
                    cacheScope.launch {
                        _cacheRemovedEvents.emit(removedKey)
                    }
                },
                onCacheCompleted = { completedUrl ->
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "AudioCacheManager onCacheCompleted: completedUrl=$completedUrl"
                    )
                    /**
                     * 修复 (2025-12-04): 确认临时保护已存在
                     * - onTransferStart 已添加保护，这里只需确认
                     * - 如果未找到（异常情况），补充添加
                     */
                    if (!processingCompletionUrls.containsKey(completedUrl)) {
                        val currentTime = System.currentTimeMillis()
                        processingCompletionUrls[completedUrl] = currentTime
                        LogConfig.w(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "AudioCacheManager onCacheCompleted: 临时保护不存在，补充添加, url=$completedUrl"
                        )
                    }
                    /**
                     * 重构：使用发送事件取代底层依赖高层设置回调
                     * 通知业务层处理缓存完成逻辑（插入数据库、更新UI状态、清理超限歌曲）
                     */
                    cacheScope.launch {
                        _cacheCompletedEvents.emit(completedUrl)
                    }
                },
                isProcessingCompletion = { url ->
                    /**
                     * 检查URL是否正在处理完成事件（修复 2025-12-04）
                     *
                     * 用途：防止LRU删除正在处理的缓存
                     * - CacheStateManager.handleCacheCompletion() 处理期间临时保护
                     * - 数据库插入后持久保护（通过 isProtected 检查）
                     */
                    isProcessingCompletion(url)
                }
            ),
            StandaloneDatabaseProvider(context)
        )
    }

    /**
     * 完成缓存完成事件的处理（修复 2025-12-04）
     *
     * 由 CacheStateManager.handleCacheCompletion() 调用
     * 从临时保护集合中移除URL，允许后续LRU清理
     *
     * @param url 已处理完成的URL
     */
    fun finishProcessingCompletion(url: String) {
        processingCompletionUrls.remove(url)
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "AudioCacheManager 移除临时保护: url=$url"
        )
    }

    /**
     * 检查URL是否正在处理完成事件（修复 2025-12-04）
     *
     * 由 ProtectedLruCacheEvictor 调用
     * 防止LRU删除正在处理的缓存
     *
     * 超时机制 (2025-12-04新增):
     * - 超过5分钟自动清理（异常情况兜底）
     * - 防止保护泄漏导致文件永久无法删除
     *
     * @param url 要检查的URL
     * @return true=正在处理（不应删除），false=未处理（可以删除）
     */
    fun isProcessingCompletion(url: String): Boolean {
        val addTime = processingCompletionUrls[url] ?: return false
        val elapsed = System.currentTimeMillis() - addTime

        // 超过5分钟自动清理（异常情况兜底）
        if (elapsed > 5 * 60 * 1000) {
            processingCompletionUrls.remove(url)
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager isProcessingCompletion: 临时保护超时，自动移除, url=$url, elapsed=${elapsed}ms"
            )
            return false
        }

        return true
    }

    /**
     * 根据歌曲url获取歌曲id
     *
     * 说明：由于ExoPlayer使用URL作为缓存key，而数据库使用songId作为主键
     * 需要通过URL反查songId来判断是否在白名单中
     *
     * @param url 缓存URL
     * @return 歌曲ID，如果无法提取则返回null
     */
    private fun getSongIdByUrl(url: String): Long? {
        return try {
            // 使用新增的getCachedSongByUrl()方法直接通过URL查询数据库
            // 由于ObservableLruCacheEvictor的回调在主线程，使用runBlocking是安全的
            kotlinx.coroutines.runBlocking {
                cachedSongDao.getCachedSongByUrl(url)?.songId
            }
        } catch (e: Exception) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "AudioCacheManager extractSongIdFromUrl失败: ${e.message}"
            )
            null
        }
    }

    companion object {
        /**
         * 从完整URL中提取基础URL（不含查询参数）
         *
         * 修复 (2025-11-25): 确保数据库存储和ExoPlayer cache key使用一致的URL格式
         *
         * 问题背景：
         * - ExoPlayer cache key使用base URL（不含token）
         * - 数据库存储full URL（含token）
         * - 导致查询失败，缓存记录无法插入
         *
         * 解决方案：
         * - 数据库统一存储base URL
         * - 与cache key格式保持一致
         *
         * 示例：
         *   输入: https://m701.music.126.net/.../file.mp3?vuutv=TOKEN_A
         *   输出: https://m701.music.126.net/.../file.mp3
         *
         * @param fullUrl 完整URL（可能包含查询参数）
         * @return 基础URL（不含查询参数）
         */
        fun getBaseUrl(fullUrl: Uri): String {
            return try {
                buildString {
                    append(fullUrl.scheme ?: "https")
                    append("://")
                    append(fullUrl.host ?: "")
                    append(fullUrl.path ?: "")
                }
            } catch (e: Exception) {
                // 解析失败时返回原URL
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "AudioCacheManager getBaseUrl解析失败: ${e.message}, 使用原URL"
                )
                fullUrl.toString()
            }
        }
    }
}
