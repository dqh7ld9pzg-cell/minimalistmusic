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
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import com.minimalistmusic.util.LogConfig
import kotlinx.coroutines.runBlocking
import java.util.TreeSet
/**
 * 支持完整缓存文件保护的 LRU 缓存驱逐器（2025-11-25 方案B优化版本 + 动态maxBytes优化）
 *
 * 架构设计：双层缓存管理机制
 * ========================================
 *
 * ## 职责定位（ExoPlayer物理层）
 *
 * 本类负责ExoPlayer物理层的缓存管理，只关心文件保护，不涉及业务规则：
 * - 保护：白名单歌曲（isProtected=true）
 * - 不保护：非白名单歌曲（即使isFullyCached=true也可清理）
 * - 业务规则由 CacheStateManager/Repository 处理
 *
 * ## 动态maxBytes机制（2025-11-25新增）
 *
 * **核心改进**：maxBytes不再是固定值，而是动态计算
 * - 旧方案：maxBytes = maxCachedSongs * 8MB * 1.5（固定映射）
 * - 新方案：maxBytes = cache.cacheSpace + 400MB（动态计算）
 *
 * **优势**：
 * - 保证上限：无论歌曲大小，都能缓存到maxCachedSongs首
 * - 固定buffer：400MB buffer始终存在，支持平滑替换
 * - 无死锁：动态maxBytes永远≥实际占用，不会陷入"无空间可清理"
 * - 空间高效：不浪费空间，buffer大小恒定
 *
 * **实现方式**：
 * - 构造函数接受函数引用 getMaxBytes: () -> Long
 * - evictCache()每次调用时重新计算maxBytes
 * - 无需重启应用，实时生效
 *
 * ## ExoPlayer 缓存驱逐机制原理
 *
 * ### CacheEvictor 回调时机
 *
 * 1. **onStartFile(key)**
 *    - 触发时机：开始写入缓存文件时
 *    - 用途：通知 evictor 有新文件即将写入
 *    - 注意：这里 return 不会阻止删除
 *
 * 2. **onSpanAdded(span)** ← 优化 (2025-11-25): 新增缓存完成监听
 *    - 触发时机：缓存 span 被添加/更新后
 *    - 用途：
 *      a. 将 span 加入 LRU 追踪列表
 *      b. 触发 evictCache() 检查是否需要清理
 *      c. 检查歌曲是否已完整缓存，触发回调通知业务层
 *
 * 3. **onSpanRemoved(span)**
 *    - 触发时机：缓存 span 被删除后
 *    - 用途：从 LRU 列表中移除 span
 *
 * 4. **onSpanTouched(oldSpan, newSpan)**
 *    - 触发时机：已缓存的文件被再次访问
 *    - 用途：更新访问时间（将 span 移到列表尾部）
 *
 * ### 删除流程（LRU 内部逻辑）
 *
 * ```
 * onSpanAdded() 被调用
 *   ↓
 * 调用 evictCache(cache, 0)
 *   ↓
 * 动态获取 maxBytes = getMaxBytes()
 *   ↓
 * 计算需要清理的空间：
 *   bytesToEvict = currentSize - maxBytes + requiredSpace
 *   ↓
 * 如果 bytesToEvict > 0：
 *   ↓
 * 遍历 leastRecentlyUsed 列表（从头到尾，头部是最久未使用）
 *   ↓
 * 对每个 span：
 *   - 检查是否受保护（调用 isProtected）
 *   - 如果受保护 → 跳过（白名单）
 *   - 如果不受保护 → 删除文件（非白名单）
 *   - bytesToEvict -= span.length
 *   - 如果 bytesToEvict <= 0，停止清理
 * ```
 *
 * ## 保护机制实现
 *
 * 1. 维护独立的 LRU 列表（TreeSet，按访问时间排序）
 * 2. 在 evictCache() 中遍历时：
 *    - 调用 isProtected(url) 检查文件是否在白名单中
 *    - 跳过受保护的文件（isProtected=true）
 *    - 删除非白名单文件（即使isFullyCached=true）
 * 3. 确保白名单歌曲永不被误删
 *
 * ## 与业务层的协作
 *
 * - **物理层（本类）**：保护白名单歌曲，动态调整maxBytes
 * - **业务层（CacheStateManager）**：管理白名单、歌曲上限、智能监听控制
 * - 参考文档：docs/2025-11-25-缓存上限机制和ExoPlayer-LRU缓存清理机制的协调.md
 *
 * @param getMaxBytes 动态获取最大缓存字节数的函数（实时计算 = cache.cacheSpace + 400MB）
 * @param isProtected 保护检查函数（URL -> 是否在白名单中）
 * @param onCacheRemoved 缓存被删除时的回调（同步删除数据库记录）
 * @param onCacheCompleted 缓存完成时的回调（通知业务层歌曲已完整缓存）- 新增 (2025-11-25)
 */
@UnstableApi
class ProtectedLruCacheEvictor(
    // 优化 maxBytes 改为动态函数，支持实时计算
    // 旧方案：private val maxBytes: Long（固定值，创建时计算一次）
    // 新方案：private val getMaxBytes: () -> Long（函数引用，每次evictCache()时重新计算）
    private val getMaxBytes: () -> Long,
    private val isProtected: suspend (String) -> Boolean,
    private val onCacheRemoved: ((String) -> Unit)? = null,
    private val onCacheCompleted: ((String) -> Unit)? = null,  // 新增：缓存完成回调
    private val isProcessingCompletion: ((String) -> Boolean)? = null  // 修复 (2025-12-04): 检查是否正在处理完成事件
) : CacheEvictor {
    /**
     * LRU 列表（按最后访问时间排序）
     *
     * 使用 TreeSet 实现 LRU：
     * - 比较器：先按 lastTouchTimestamp 排序（旧的在前），再按 key 排序（保证唯一性）
     * - 最久未使用的在头部，最近使用的在尾部
     */
    private val leastRecentlyUsed = TreeSet<CacheSpan> { a, b ->
        when {
            a.lastTouchTimestamp != b.lastTouchTimestamp ->
                a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
            else -> a.key.compareTo(b.key)
        }
    }
    override fun requiresCacheSpanTouches(): Boolean = true
    override fun onCacheInitialized() {
        // 初始化时清空 LRU 列表
        leastRecentlyUsed.clear()
    }
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        // 开始写入文件时触发，触发驱逐检查
        evictCache(cache, length)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        // 添加到 LRU 列表
        leastRecentlyUsed.add(span)
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "ProtectedLruCacheEvictor onSpanAdded: key=${span.key}, size=${span.length.formatBytes()}, " +
                        "LRU size=${leastRecentlyUsed.size}, " +
                        "cache.cacheSpace=${cache.cacheSpace.formatBytes()}, currentThread: ${Thread.currentThread().name}"
            )
        }
        // 优化 (2025-11-25): 检查歌曲是否已完整缓存
        // 使用与 CacheStateManager.performMonitorLoop 相同的判断逻辑
        // 不遍历所有 span，只检查当前 URL 的完整性
        checkCacheCompletionAndNotify(cache, span.key)
        // 检查是否需要清理
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "ProtectedLruCacheEvictor onSpanAdded: 准备调用 evictCache..."
            )
        }
        evictCache(cache, 0)
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "ProtectedLruCacheEvictor onSpanAdded: evictCache 调用完成"
            )
        }
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        // 从 LRU 列表中移除, evictCache中遍历leastRecentlyUsed时会最终回调该接口存在ConcurrentModificationException问题,
        // 直接在evictCache中通过迭代器安全移除
       // leastRecentlyUsed.remove(span)
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "ProtectedLruCacheEvictor onSpanRemoved: key=${span.key}, LRU size=${leastRecentlyUsed.size}"
        )
        }
        // 通知外部（删除数据库记录）
        onCacheRemoved?.invoke(span.key)
    }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        // 更新 LRU 列表（移除旧的，添加新的）
        leastRecentlyUsed.remove(oldSpan)
        leastRecentlyUsed.add(newSpan)
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "ProtectedLruCacheEvictor onSpanTouched: key=${newSpan.key}, " +
                    "timestamp=${newSpan.lastTouchTimestamp}"
        )
        }
    }

    /**
     * 核心：驱逐缓存逻辑（方案B：只保护白名单歌曲 + 动态maxBytes优化）
     *
     * 算法流程：
     * 1. 动态获取maxBytes = getMaxBytes()（实时计算 = cache.cacheSpace + 400MB）
     * 2. 计算需要清理的空间：currentSize - maxBytes + requiredSpace
     * 3. 如果需要清理（bytesToEvict > 0）：
     *    a. 遍历 LRU 列表（从最久未使用到最近使用）
     *    b. 对每个 span（缓存文件）：
     *       - 调用 isProtected(url) 检查是否在白名单中
     *       - 如果 isProtected=true → 跳过（白名单保护）
     *       - 如果 isProtected=false → 删除文件（非白名单，即使isFullyCached=true）
     *    c. 直到清理足够的空间或遍历完列表
     * 4. 如果所有文件都受保护且空间不足，记录警告
     *
     * 保护策略：
     * - 保护：白名单歌曲（isProtected=true）
     * - 删除：非白名单歌曲（即使isFullyCached=true也可清理）
     * - 白名单、上限等业务规则由 CacheStateManager 处理
     *
     * 动态maxBytes优化 (2025-11-25):
     * - maxBytes = cache.cacheSpace + 400MB（动态计算）
     * - buffer固定为400MB，不再使用比例
     * - 保证能缓存到上限数量，避免死锁
     * - 无需重启应用，实时生效
     *
     * @param cache Cache 实例
     * @param requiredSpace 需要的额外空间（写入新文件时使用）
     */
    private fun evictCache(cache: Cache, requiredSpace: Long) {
        // 优化 (2025-11-25): 每次清理时动态获取maxBytes
        // 旧方案：使用固定的 maxBytes（创建时计算一次，永不改变）
        // 新方案：调用 getMaxBytes() 实时计算（= cache.cacheSpace + 400MB）
        val maxBytes = getMaxBytes()
        val currentSize = cache.cacheSpace
        // 1. 计算基础需要清理的空间
        val baseEvict = currentSize - maxBytes + requiredSpace
        // 2. 判断是否需要清理
        if (baseEvict <= 0) {
            // 无需清理
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "ProtectedLruCacheEvictor evictCache: 无需清理，返回 " +
                        "(当前=${currentSize.formatBytes()}, 最大=${maxBytes.formatBytes()}, " +
                        "需要=${requiredSpace.formatBytes()})"
            )
            }
            return
        }
        // 3. 优化 (2025-11-25): buffer已包含在动态maxBytes中，不需要额外添加
        // 旧方案：val bufferSpace = (maxBytes * 0.3).toLong(); bytesToEvict = baseEvict + bufferSpace
        // 新方案：maxBytes = cache.cacheSpace + 400MB，buffer已经是400MB固定值，直接使用baseEvict
        var bytesToEvict = baseEvict
        // 修复 (2025-11-25): 合并重复日志，添加更多调试信息
        // 优化 (2025-11-25): 移除bufferSpace日志，因为buffer已包含在动态maxBytes中
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "ProtectedLruCacheEvictor evictCache: " +
                    "当前大小=${currentSize.formatBytes()}, " +
                    "动态maxBytes=${maxBytes.formatBytes()}, " +
                    "需要空间=${requiredSpace.formatBytes()}, " +
                    "需清理=${bytesToEvict.formatBytes()}"
        )
        }
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "ProtectedLruCacheEvictor evictCache: 开始清理，LRU列表大小=${leastRecentlyUsed.size}"
        )
        }
        var evictedCount = 0
        var protectedCount = 0
        var evictedBytes = 0L
        // 遍历 LRU 列表（最久未使用的在前）
        val iterator = leastRecentlyUsed.iterator()
        while (iterator.hasNext() && bytesToEvict > 0) {
            val span = iterator.next()

            // 修复 (2025-12-04): 先检查是否正在处理完成事件（临时保护）
            val isProcessing = isProcessingCompletion?.invoke(span.key) ?: false
            if (isProcessing) {
                // 正在处理完成事件，临时跳过（防止竞态条件）
                protectedCount++
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "ProtectedLruCacheEvictor evictCache: [跳过] 跳过正在处理完成的文件，key=${span.key}, " +
                                "size=${span.length.formatBytes()}"
                    )
                }
                continue
            }

            // 检查是否受保护（使用 runBlocking 因为在 Cache 线程中）
            val protected = try {
                runBlocking { isProtected(span.key) }
            } catch (e: Exception) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "ProtectedLruCacheEvictor evictCache: 保护检查失败，key=${span.key}, error=${e.message}"
                )
                }
                false // 检查失败，默认不保护
            }
            if (protected) {
                // 受保护，跳过
                protectedCount++
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "ProtectedLruCacheEvictor evictCache: [跳过] 跳过受保护文件，key=${span.key}, " +
                            "size=${span.length.formatBytes()}"
                )
                }
                continue
            }
            // 不受保护，删除
            try {
                // 先从迭代器中移除（避免 onSpanRemoved 重复删除）
                iterator.remove()
                // 删除文件（会触发 onSpanRemoved，但已经从 iterator 中移除了）
                cache.removeSpan(span)
                evictedBytes += span.length
                bytesToEvict -= span.length
                evictedCount++
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "ProtectedLruCacheEvictor evictCache: [删除] 删除文件: size=${span.length.formatBytes()}, 剩余需清理 ${bytesToEvict.formatBytes()}, "+
                            "key=${span.key} "
                )
                }
            } catch (e: Exception) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "ProtectedLruCacheEvictor evictCache: 删除失败，key=${span.key}, error=${e.message}"
                )
                }
            }
        }
        // 最终统计
        if (bytesToEvict > 0) {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "ProtectedLruCacheEvictor evictCache: [警告] 清理完成但空间仍不足！" +
                        "已删除 $evictedCount 个文件（${evictedBytes.formatBytes()}），" +
                        "跳过 $protectedCount 个受保护文件，" +
                        "仍需 ${bytesToEvict.formatBytes()}"
            )
            }
        } else {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "ProtectedLruCacheEvictor evictCache: 清理完成，" +
                        "已删除 $evictedCount 个文件（${evictedBytes.formatBytes()}），" +
                        "跳过 $protectedCount 个受保护文件"
            )
            }
        }
    }

    /**
     * 检查缓存完成并通知业务层（2025-11-25 新增）
     *
     * 实现说明：
     * - 使用与 CacheStateManager.performMonitorLoop 相同的判断逻辑
     * - 不遍历所有 span，直接使用 ExoPlayer 的 API 检查完整性
     * - 如果检测到完整缓存，触发 onCacheCompleted 回调
     *
     * 判断逻辑（与轮询方式完全一致）：
     * 1. 获取文件总长度（len）
     * 2. 检查是否有有效长度（len != Long.MAX_VALUE && len > 0）
     * 3. 使用 cache.isCached(url, 0, len) 检查是否已缓存
     * 4. 使用 cache.getCachedLength(url, 0, len) 获取已缓存大小
     * 5. 判断：isCached && cachedLen == len → 完整缓存
     *
     * @param cache Cache 实例
     * @param url 歌曲 URL（span.key）
     */
    private fun checkCacheCompletionAndNotify(cache: Cache, url: String) {
        try {
            // 1. 获取文件总长度
            val len = cache.getContentMetadata(url).get(
                androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH,
                Long.MAX_VALUE
            )
            // 2. 检查是否有有效长度
            val hasValidLength = (len != Long.MAX_VALUE && len > 0)
            if (!hasValidLength) {
                // 长度未知，无法判断完整性
                return
            }
            // 3. 检查是否已完整缓存（与 CacheStateManager.performMonitorLoop 逻辑一致）
            val isCached = cache.isCached(url, 0, len)
            val cachedLen = cache.getCachedLength(url, 0, len)
            val isFullyCached = isCached && (cachedLen == len)
            if (isFullyCached) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "ProtectedLruCacheEvictor 检测到缓存完成: url=$url, size=${len.formatBytes()}"
                    )
                }
                // 4. 触发回调通知业务层
                onCacheCompleted?.invoke(url)
            }
        } catch (e: Exception) {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "ProtectedLruCacheEvictor 检查缓存完成失败: url=$url, error=${e.message}"
                )
            }
        }
    }

    /**
     * 格式化字节数（扩展函数）
     */
    private fun Long.formatBytes(): String {
        return when {
            this < 1024 -> "${this}B"
            this < 1024 * 1024 -> "${this / 1024}KB"
            else -> "${this / 1024 / 1024}MB"
        }
    }
}
