package com.minimalistmusic.domain.cache

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.ContentMetadata
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.cache.CacheConfig
import com.minimalistmusic.data.cache.KeyValueCacheManager
import com.minimalistmusic.data.local.MusicDatabase
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.data.local.entity.CachedSongEntity
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.util.LogConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存状态管理器
 *
 * 架构设计：
 * - 单例模式：全局共享同一份缓存状态数据
 * - StateFlow：响应式状态管理，自动通知UI更新
 * - 解耦设计：不修改Song领域模型，缓存状态作为独立层管理
 *
 * 职责：
 * - 维护歌曲ID→缓存状态的映射（Map<Long, Boolean>）
 * - 提供异步查询方法，避免阻塞UI线程
 * - 支持批量查询和单个查询
 * - 在播放、缓存完成时自动更新状态
 *
 * 性能优化：
 * - StateFlow自动去重：相同状态不会触发重组
 * - 懒加载：仅在需要时查询缓存状态
 * - 批量更新：减少StateFlow发射频率
 *
 * 使用场景：
 * - ViewModel通过isSongCached()查询缓存状态
 * - PlayerViewModel在播放时更新缓存状态
 * - UI通过collectAsState()响应状态变化
 *
 * @param audioCacheManager ExoPlayer音频缓存管理器
 * @param cacheManager JSON缓存管理器
 *
 * @since 2025-11-14
 */
@UnstableApi
@Singleton
class CacheStateManager @Inject constructor(
    private val audioCacheManager: AudioCacheManager,
    private val cacheManager: KeyValueCacheManager,
    private val musicDatabase: MusicDatabase,  // v7新增：用于记录缓存音乐元数据
    private val userPreferencesDataStore: UserPreferencesDataStore,  // 新增 (2025-11-19)：读取WiFi快速缓存设置
    private val connectivityManager: ConnectivityManager,  // 新增 (2025-11-25)：网络类型检测
    private val performanceTracker: com.minimalistmusic.data.cache.CachePerformanceTracker  // 新增 (2025-12-03)：性能跟踪
) {
    companion object {
        private const val TAG = "CacheStateManager"
    }
    /**
     * 独立协程作用域（使用SupervisorJob避免子协程异常影响全局）
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 内存映射表：baseURL → songId
     *
     * 架构重构：
     * - 用途：在缓存完成时通过 URL 反查 songId
     * - 时机：获取 URL 后立即注册，缓存完成后移除
     * - 生命周期：应用重启后清空（重新播放会重新注册）
     * - 线程安全：使用 ConcurrentHashMap
     */
    private val urlToSongIdMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /**
     * 缓存插入互斥锁（修复 2025-11-25）
     *
     * 用途：
     * - 保护 handleCacheCompletion() 的临界区
     * - 防止并发插入导致超过缓存上限
     *
     * 问题场景：
     * - 协程A和B同时读取 count=17 < 18
     * - 两者都认为可以插入
     * - 最终 count=19，超过上限18
     *
     * 解决方案：
     * - 使用 Mutex 保证"检查空间→清理→插入"的原子性
     * - 同一时刻只有一个协程可以执行插入逻辑
     */
    private val insertMutex = Mutex()
    init {
        // 修复 (2025-12-03): 分离两个 collect 到独立协程
        // 原问题：两个 collect 在同一协程中按顺序执行，第一个会永久阻塞第二个
        // 解决方案：使用独立的 launch 块，让两个 Flow 并行监听

        // 1. 监听缓存完成事件（事件驱动触发）
        scope.launch {
            try {
                audioCacheManager.cacheCompletedEvents.collect { url ->
                    handleCacheCompletion(url)
                }
            } catch (e: Exception) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager 监听缓存完成事件失败: ${e.message}"
                    )
                }
            }
        }

        // 2. 监听数据库变化（Single Source of Truth）
        scope.launch {
            try {
                // 架构重构: 持续监听数据库变化,实现Single Source of Truth
                // 数据库是唯一真实数据源,cacheStateMap自动同步数据库状态
                // 持续收集数据库的Flow,数据库变化时自动更新cacheStateMap
                musicDatabase.cachedSongDao().observeAllCachedSongIds().collect { cachedIds ->
                    // 将数据库中的已缓存歌曲ID转换为Map<Long, Boolean>
                    val newCacheStateMap = cachedIds.associateWith { true }
                    // 更新StateFlow,触发所有订阅者(UI层)自动重组
                    _cacheStateMap.value = newCacheStateMap
                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "CacheStateManager 数据库状态同步: ${cachedIds.size} 首已缓存歌曲"
                        )
                    }
                }
            } catch (e: Exception) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager 监听数据库失败: ${e.message}"
                    )
                }
            }
        }

        // 3. 监听缓存进度事件（TransferListener实时进度）
        scope.launch {
            try {
                audioCacheManager.cacheProgressEvents.collect { (url, bytesDownloaded, totalBytes) ->
                    // 通过URL反查songId
                    val songId = urlToSongIdMap[url]
                    if (songId == null) {
                        // URL未注册，跳过（可能是其他来源的缓存）
                        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                            LogConfig.w(
                                LogConfig.TAG_PLAYER_DATA_LOCAL,
                                "CacheStateManager 缓存进度事件: URL未注册映射，跳过, url=$url"
                            )
                        }
                        return@collect
                    }

                    // 计算进度百分比
                    val progressPercent = com.minimalistmusic.domain.model.CacheProgress.calculateProgress(
                        bytesDownloaded,
                        totalBytes
                    )

                    // 创建CacheProgress对象
                    val cacheProgress = com.minimalistmusic.domain.model.CacheProgress(
                        songId = songId,
                        url = url,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        progressPercent = progressPercent,
                        isComplete = (progressPercent >= 100)
                    )

                    // 更新进度映射表
                    val currentMap = _cacheProgressMap.value.toMutableMap()
                    currentMap[songId] = cacheProgress
                    _cacheProgressMap.value = currentMap

                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "CacheStateManager 缓存进度更新: ${cacheProgress.formatForLog()}"
                        )
                    }

                    // 不再自动清理进度状态，改为切歌时手动清理
                }
            } catch (e: Exception) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager 监听缓存进度失败: ${e.message}"
                    )
                }
            }
        }
    }
    /**
     * currentMonitorJob 和 currentMonitoringSongId 已移除 (2025-12-04)
     *
     * 移除理由：
     * - 轮询监听机制（monitorCacheProgress）已废弃
     * - TransferListener 自动监听，无需手动管理监听任务
     * - 不再需要跟踪和取消监听Job
     */
    /**
     * 缓存状态映射表（基础状态 - Single Source of Truth）
     * - Key: 歌曲ID
     * - Value: 是否已缓存（true=已缓存, false=未缓存）
     *
     * 说明：
     * - 本地歌曲不会出现在此Map中（默认已缓存，无需查询）
     * - 仅存储在线歌曲的缓存状态
     * - 所有其他缓存相关状态都从此派生
     */
    private val _cacheStateMap = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val cacheStateMap: StateFlow<Map<Long, Boolean>> = _cacheStateMap.asStateFlow()
    /**
     * 已缓存歌曲ID集合（派生状态）
     *
     * 架构优化 (2025-11-15):
     * - 从 cacheStateMap 派生，自动同步
     * - 当 cacheStateMap 更新时，此状态自动更新
     * - 移除了 CachedMusicViewModel 中的重复状态
     */
    val cachedSongIds: StateFlow<Set<Long>> = _cacheStateMap
        .map { map -> map.filter { it.value }.keys }
        .stateIn(scope, SharingStarted.Companion.Lazily, emptySet())
    /**
     * 已缓存歌曲数量（派生状态）
     *
     * 架构优化 (2025-11-15):
     * - 从 cachedSongIds 派生，自动同步
     * - 用于UI显示"已缓存的音乐"数量
     * - 替代了 CachedMusicViewModel._cachedSongCount
     */
    val cachedSongCount: StateFlow<Int> = cachedSongIds
        .map { it.size }
        .stateIn(scope, SharingStarted.Companion.Lazily, 0)
    /**
     * 缓存大小（字节）- Single Source of Truth
     *
     * 架构优化 (2025-11-20):
     * - 直接监听数据库的 observeTotalCacheSize()
     * - 数据库变化时自动更新
     * - ProfileViewModel 和 CachedMusicViewModel 共享此状态
     * - 替代了各自独立维护的 _cacheSize
     * - 使用 Eagerly 确保立即开始收集，避免初始值为0的问题
     */
    val cacheSizeBytes: StateFlow<Long> = musicDatabase.cachedSongDao().observeTotalCacheSize()
        .map { it ?: 0L }
        .stateIn(scope, SharingStarted.Companion.Eagerly, 0L)
    /**
     * 缓存大小（格式化字符串）- 派生状态
     *
     * 架构优化 (2025-11-20):
     * - 从 cacheSizeBytes 派生
     * - 格式化为可读字符串（如 "12.5 MB"）
     * - 用于 UI 直接显示
     * - 使用 Eagerly 确保立即更新
     */
    val cacheSizeFormatted: StateFlow<String> = cacheSizeBytes
        .map { bytes -> bytes.formatCacheSize() }
        .stateIn(scope, SharingStarted.Companion.Eagerly, "0 MB")
    /**
     * 缓存完成事件（已废弃 2025-11-19）
     *
     * 废弃原因：
     * - 数据库Flow自动监听已取代事件驱动模式
     * - 数据库变化时自动更新cacheStateMap,无需手动发送事件
     * - 保留该字段仅为兼容旧代码,后续可完全移除
     *
     * @Deprecated("使用数据库Flow自动监听机制,无需事件驱动")
     */
    @Deprecated("使用数据库Flow自动监听机制,无需事件驱动")
    private val _cacheCompletedEvents =
        MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 10)
    @Deprecated("使用数据库Flow自动监听机制,无需事件驱动")
    val cacheCompletedEvents: SharedFlow<Long> = _cacheCompletedEvents.asSharedFlow()

    /**
     * 缓存进度状态映射表
     *
     * 架构重构：基于 TransferListener 的实时进度监听
     * - Key: 歌曲ID
     * - Value: 缓存进度（CacheProgress）
     * - 数据来源：AudioCacheManager.cacheProgressEvents
     * - 用途：在 PlayerScreen 显示缓存进度条
     */
    private val _cacheProgressMap = MutableStateFlow<Map<Long, com.minimalistmusic.domain.model.CacheProgress>>(emptyMap())
    val cacheProgressMap: StateFlow<Map<Long, com.minimalistmusic.domain.model.CacheProgress>> = _cacheProgressMap.asStateFlow()
    /**
     * 检查歌曲是否已缓存（同步方法，适合UI层调用）
     *
     * 架构重构 (2025-12-03):
     * - 完全依赖数据库 Flow 监听（Single Source of Truth）
     * - 移除了手动查询逻辑（updateSongCacheState），避免状态覆盖
     * - _cacheStateMap 由 observeAllCachedSongIds() 自动同步（init 第112-123行）
     *
     * 查询策略：
     * 1. 本地歌曲：直接返回true（无需缓存）
     * 2. 在线歌曲：从 _cacheStateMap 读取（来自数据库监听）
     *    - 存在且为true：已缓存
     *    - 不存在或为false：未缓存
     *
     * @param songId 歌曲ID
     * @param isLocal 是否为本地歌曲
     * @return 是否已缓存
     */
    fun isSongCached(songId: Long, isLocal: Boolean): Boolean {
        // 本地歌曲无需缓存检查
        if (isLocal) return true

        // 从 StateFlow 中获取缓存状态（来自数据库 Flow 监听）
        return _cacheStateMap.value[songId] ?: false
    }

    /**
     * 注册缓存尝试（URL → songId 映射）
     *
     * 架构重构：
     * - 用途：在获取播放 URL 后立即注册，建立 baseURL → songId 映射
     * - 时机：每次调用 getSongPlayUrl() 成功后调用
     * - 生命周期：应用重启后清空（重新播放会重新注册）
     *
     * 防御性检查：
     * - 确保 songs 表中存在该记录（满足外键约束）
     * - 如果记录不存在，跳过注册并记录警告
     *
     * @param songId 歌曲ID
     * @param baseUrl 播放URL的基础部分（不含查询参数）
     */
    suspend fun registerCacheAttempt(songId: Long, baseUrl: String) {
        try {
            // 防御性检查：确保 songs 表中存在该记录
            val songExists = withContext(Dispatchers.IO) {
                musicDatabase.songDao().getSongById(songId) != null
            }

            if (!songExists) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager registerCacheAttempt: songs表中不存在songId=$songId，跳过注册（外键约束保护）"
                    )
                }
                return
            }

            // 注册映射
            urlToSongIdMap[baseUrl] = songId
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheStateManager registerCacheAttempt: 已注册缓存尝试, songId=$songId, baseUrl=$baseUrl"
                )
            }
        } catch (e: Exception) {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheStateManager registerCacheAttempt: 注册失败, songId=$songId, error=${e.message}"
                )
            }
        }
    }

    /**
     * 清理非当前播放歌曲的缓存进度
     *
     * 使用场景：切歌时清理上一首歌曲的缓存进度
     *
     * 策略：
     * - 保留当前播放歌曲的进度（让用户看到缓存状态）
     * - 清理其他歌曲的进度（避免内存泄漏）
     *
     * @param currentSongId 当前播放歌曲的ID
     */
    fun clearCacheProgressExcept(currentSongId: Long) {
        val oldMap = _cacheProgressMap.value
        val newMap = oldMap.filterKeys { it == currentSongId }

        if (newMap.size < oldMap.size) {
            _cacheProgressMap.value = newMap
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheStateManager 清理缓存进度: 保留songId=$currentSongId, 清理${oldMap.size - newMap.size}首歌曲"
                )
            }
        }
    }

    /**
     * monitorCacheProgress() 方法已废弃 (2025-12-04架构优化)
     *
     * @Deprecated("已被 TransferListener 实时进度监听机制完全替代")
     *
     * 废弃理由：
     * - 依赖的 performMonitorLoop() 轮询逻辑已移除
     * - 已被 TransferListener 事件驱动机制替代
     * - PlayerViewModel 中的调用已被注释掉（不再使用）
     *
     * 新方案：
     * - 缓存进度监听：CacheProgressTransferListener 自动监听
     * - 缓存完成检测：ProtectedLruCacheEvictor.onSpanAdded() 自动触发
     * - 无需手动启动监听任务，完全自动化
     *
     * 如需重新启用缓存进度监听，不需要调用此方法，TransferListener 会自动工作。
     */
    @Deprecated(
        message = "已被 TransferListener 实时进度监听机制完全替代，无需手动调用",
        replaceWith = ReplaceWith("无需替代，TransferListener 自动监听"),
        level = DeprecationLevel.ERROR
    )
    fun monitorCacheProgress(songId: Long, url: String?, isLocal: Boolean, durationMs: Long = 0L) {
        // 方法已废弃，不执行任何操作
        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheStateManager monitorCacheProgress() 已废弃，请移除此调用。TransferListener会自动监听缓存进度。"
            )
        }
    }
    /**
     * performMonitorLoop() 方法已移除 (2025-12-04架构优化)
     *
     * 移除理由：
     * - 已被 TransferListener 实时进度监听机制完全替代
     * - 轮询方式存在性能开销（每500ms查询一次SimpleCache）
     * - 存在检测延迟（最多500ms）
     * - 存在时间窗口限制（只监听播放前30-60秒）
     *
     * 新方案（TransferListener）优势：
     * - 实时性：每次数据传输立即触发回调，零延迟
     * - 零开销：事件驱动，不占用CPU资源持续轮询
     * - 全生命周期：覆盖整个下载过程，无时间窗口限制
     * - 精确性：bytesTransferred参数提供精确字节数
     *
     * 新方案实现位置：
     * - CacheProgressTransferListener.onBytesTransferred()  // 实时监听
     * - CacheStateManager.init (缓存进度事件订阅)          // 进度管理
     * - ProtectedLruCacheEvictor.onSpanAdded()             // 完成检测
     * - CacheStateManager.handleCacheCompletion()          // 业务处理
     */
    /**
     * 处理缓存完成事件
     *
     * 优化说明：
     * - 旧方案：轮询检测（performMonitorLoop），每500ms检查一次缓存是否完成
     * - 新方案：事件驱动（onSpanAdded回调），缓存span添加时立即检测
     * - 优势：实时检测、无轮询开销、更精确的触发时机
     *
     * 实现逻辑（复用performMonitorLoop的核心逻辑）：
     * 1. 通过URL反查songId
     * 2. 检查缓存功能是否启用
     * 3. 插入/更新数据库记录（isFullyCached=true）
     * 4. 更新StateFlow通知UI
     * 5. 检查是否超过缓存上限，自动清理超限歌曲
     *
     * @param url 缓存完成的歌曲URL
     */
    suspend fun handleCacheCompletion(url: String) = withContext(Dispatchers.IO) {
        try {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheStateManager handleCacheCompletion url=$url"
            )
            // 架构重构：优先使用内存映射表反查 songId
            // 1. 从内存映射表中获取 songId（主要路径）
            var songId = urlToSongIdMap[url]

            // 2. 回退方案：如果内存映射表中没有（可能是应用重启后），尝试从数据库查询
            if (songId == null) {
                val cachedSong = musicDatabase.cachedSongDao().getCachedSongByUrl(url)
                songId = cachedSong?.songId

                if (songId != null) {
                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "CacheStateManager handleCacheCompletion: 内存映射表未找到，从数据库恢复映射, songId=$songId"
                        )
                    }
                    // 恢复映射到内存（避免下次再查数据库）
                    urlToSongIdMap[url] = songId
                } else {
                    // 两种方式都未找到，记录警告
                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        LogConfig.w(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "CacheStateManager handleCacheCompletion: 无法获取songId（内存映射和数据库均未找到），跳过处理, url=$url"
                        )
                    }
                    // 取消性能跟踪（如果有）
                    performanceTracker.cancelTracking(url)
                    return@withContext
                }
            }

            // 3. 检查是否已标记为完整缓存（去重保护）
            val existingCachedSong = musicDatabase.cachedSongDao().getCachedSong(songId)

            // 架构重构：如果已存在完整缓存记录，跳过重复处理
            // 避免应用启动时 onSpanAdded 遍历已有缓存时重复处理和触发清理
            if (existingCachedSong?.isFullyCached == true) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager handleCacheCompletion: 歌曲已标记为完整缓存，跳过重复处理: songId=$songId"
                    )
                }
                // 取消性能跟踪（避免重复记录）
                performanceTracker.cancelTracking(url)
                // 移除内存映射（释放内存）
                urlToSongIdMap.remove(url)
                return@withContext
            }
            // 2. 检查缓存功能是否启用
            if (!userPreferencesDataStore.cacheEnabled.value) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager handleCacheCompletion: 缓存功能已关闭，跳过数据库插入: songId=$songId"
                    )
                }
                return@withContext
            }

            // 3. 获取文件大小（修复 2025-12-04：在临界区外检查，保持原子性）
            // 注意：此时文件可能被LRU删除，通过临时保护机制（processingCompletionUrls）防止
            val len = audioCacheManager.simpleCache.getContentMetadata(url)
                .get(
                    ContentMetadata.KEY_CONTENT_LENGTH,
                    0L
                )
            if (len <= 0) {
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager handleCacheCompletion: 无法获取文件大小（可能已被LRU删除），跳过处理, songId=$songId"
                    )
                }
                // 移除临时保护
                audioCacheManager.finishProcessingCompletion(url)
                return@withContext
            }

            // 4. 检查缓存空间并插入（临界区：防止并发插入导致超限）
            insertMutex.withLock {
                val maxCount = userPreferencesDataStore.maxCachedSongs.value
                val currentCount = musicDatabase.cachedSongDao().getCachedSongCount()
                val protectedCount = musicDatabase.cachedSongDao().getProtectedCount()
                val isFavorite = musicDatabase.songDao().getSongById(songId)?.isFavorite ?: false
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager handleCacheCompletion: 缓存空间检查: 当前=$currentCount 首, 上限=$maxCount 首, 白名单=$protectedCount 首, 新歌曲isProtected=$isFavorite"
                    )
                }
                // 4.1 如果已达到上限，必须先清理1首再插入（修复 2025-11-25：红心歌曲也不能绕过上限）
                if (currentCount >= maxCount) {
                    val maxNonProtectedCount = (maxCount - protectedCount).coerceAtLeast(0)
                    val nonProtectedCount = currentCount - protectedCount
                    if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                        LogConfig.d(
                            LogConfig.TAG_PLAYER_DATA_LOCAL,
                            "CacheStateManager handleCacheCompletion: 已达上限，必须清理: 当前=$currentCount 首, 上限=$maxCount 首, 白名单=$protectedCount 首, 非白名单=$nonProtectedCount 首, 新歌曲isProtected=$isFavorite"
                        )
                    }
                    // 关键修复：无论新歌曲是否红心，都必须先清理1首，确保 count <= maxCount
                    // 优先清理非白名单歌曲
                    val urlsToDelete = musicDatabase.cachedSongDao()
                        .getUrlsToDeleteExcludingProtected(maxNonProtectedCount.coerceAtLeast(1) - 1)
                    if (urlsToDelete.isNotEmpty()) {
                        // 删除ExoPlayer缓存文件
                        urlsToDelete.forEach { urlToDelete ->
                            audioCacheManager.removeCachedSong(urlToDelete)
                        }
                        // 删除数据库记录
                        musicDatabase.cachedSongDao().trimCachedSongsByPlayTimeExcludingProtected(
                            maxNonProtectedCount.coerceAtLeast(1) - 1
                        )
                        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                            LogConfig.d(
                                LogConfig.TAG_PLAYER_DATA_LOCAL,
                                "CacheStateManager handleCacheCompletion: 已清理${urlsToDelete.size}首歌曲，为新歌曲腾出空间"
                            )
                        }
                    } else {
                        // 无可清理歌曲（全是白名单），跳过插入
                        if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                            LogConfig.w(
                                LogConfig.TAG_PLAYER_DATA_LOCAL,
                                "CacheStateManager handleCacheCompletion: 缓存已满且全是白名单，跳过插入: songId=$songId"
                            )
                        }
                        // 移除内存映射和临时保护
                        urlToSongIdMap.remove(url)
                        audioCacheManager.finishProcessingCompletion(url)
                        return@withContext
                    }
                }
                // 5. 插入缓存记录（确保有可用空间后才插入）
                val currentTime = System.currentTimeMillis()
                if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_LOCAL,
                        "CacheStateManager handleCacheCompletion: 插入缓存记录: songId=$songId, isFavorite=$isFavorite, isProtected=$isFavorite"
                    )
                }
                // 修复 (2025-11-25): 使用base URL（不含token），与cache key格式一致
                val baseUrl = AudioCacheManager.Companion.getBaseUrl(url.toUri())
                val cacheSong = CachedSongEntity(
                    songId = songId,
                    url = baseUrl,
                    isFullyCached = true,
                    cacheSize = len,
                    cachedAt = currentTime,
                    urlExpireTime = 0L,  // 架构重构：URL过期逻辑已废弃，保留字段以便将来使用
                    isProtected = isFavorite  // 已收藏的歌曲自动加入白名单
                )
                musicDatabase.cachedSongDao().insertCachedSong(cacheSong)
                // 6. 数据库插入后，Room Flow 会自动触发 cacheStateMap 更新
                // 重构 (2025-12-03): 移除手动更新，依赖 Single Source of Truth
                // observeAllCachedSongIds() 会自动同步数据库变化（< 16ms）

                // 7. 完成性能跟踪并输出报告 (2025-12-03)
                performanceTracker.completeTracking(baseUrl, audioCacheManager.simpleCache)

                // 8. 移除临时保护（修复 2025-12-04）
                audioCacheManager.finishProcessingCompletion(url)
            }

            // 架构重构：成功插入缓存记录后，移除内存映射（释放内存）
            urlToSongIdMap.remove(url)

            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheStateManager handleCacheCompletion: 缓存完成处理成功: songId=$songId, 文件大小=${len / 1024}KB"
                )
            }
        } catch (e: Exception) {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "CacheStateManager handleCacheCompletion: 处理失败, url=$url, error=${e.message}"
                )
            }
            // 异常情况下取消性能跟踪、移除临时保护和内存映射
            performanceTracker.cancelTracking(url)
            audioCacheManager.finishProcessingCompletion(url)
            urlToSongIdMap.remove(url)
        }
    }
    /**
     * cancelCurrentMonitor() 方法已移除 (2025-12-04)
     *
     * 移除理由：
     * - 轮询监听机制（monitorCacheProgress）已废弃
     * - 依赖的状态变量（currentMonitorJob、currentMonitoringSongId）已移除
     * - TransferListener 自动管理监听生命周期，无需手动取消
     *
     * 新方案：
     * - TransferListener 在数据传输结束时自动清理（onTransferEnd）
     * - 无需手动管理监听任务的启动和取消
     */
    /**
     * 重构说明 (2025-12-03): setCacheState() 方法已移除
     *
     * 移除理由：
     * - 违反 Single Source of Truth 原则（数据库应该是唯一可信源）
     * - 容易导致状态不一致（手动更新 vs 数据库状态）
     * - 目前代码中无任何调用，移除不影响功能
     *
     * 替代方案：
     * - 删除缓存：直接调用 musicDatabase.cachedSongDao().deleteCachedSong()
     *   → Room Flow 会自动更新 cacheStateMap
     * - 添加缓存：通过正常的缓存流程触发 handleCacheCompletion()
     *   → 数据库插入后 Flow 自动同步
     *
     * 数据流：
     * 数据库操作 → Room Flow → cacheStateMap → UI 更新
     * 时间差：< 16ms（不到一帧）
     */

    /**
     * 获取当前网络类型 (2025-11-25)
     *
     * 用途：根据网络类型决定是否启用快速缓存
     * - WiFi网络：检查fastCacheOnWiFi设置
     * - 移动网络：检查fastCacheOnMobile设置
     * - 其他/无网络：不启用快速缓存
     *
     * @return true=WiFi网络, false=移动网络或其他
     */
    private fun isWiFiNetwork(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            if (LogConfig.ENABLE_CACHE_EVENT_LOG) {
            LogConfig.w(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "CacheStateManager 获取网络类型失败: ${e.message}"
            )
            }
            false  // 默认返回false（不启用快速缓存）
        }
    }

    /**
     * 检测当前网络类型（字符串形式）(2025-12-03)
     *
     * 用途：性能跟踪时记录网络类型
     *
     * @return "WiFi" / "Mobile" / "Unknown"
     */
    private fun detectNetworkType(): String {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            when {
                capabilities == null -> "Unknown"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * 格式化缓存大小（扩展函数）
     */
    private fun Long.formatCacheSize(): String {
        return when {
            this < 1024 -> "${this}B"
            this < 1024 * 1024 -> "${this / 1024}KB"
            else -> "${this / 1024 / 1024}MB"
        }
    }
}