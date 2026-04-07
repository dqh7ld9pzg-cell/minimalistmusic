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

package com.minimalistmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.domain.cache.CacheStateManager
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.usecase.favorite.SyncFavoritesToCloudUseCase
import com.minimalistmusic.domain.usecase.history.SyncHistoryToCloudUseCase
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置 ViewModel
 *
 * 架构改进：
 * - 使用 SyncFavoritesToCloudUseCase 和 SyncHistoryToCloudUseCase
 * - 消除重复的同步逻辑
 * - ViewModel 专注于设置状态管理
 *
 * 缓存管理:
 * - 支持音频缓存和数据缓存的统一管理
 */
@UnstableApi
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val syncFavoritesUseCase: SyncFavoritesToCloudUseCase,
    private val syncHistoryUseCase: SyncHistoryToCloudUseCase,
    private val audioCacheManager: AudioCacheManager,// 音频缓存管理 (2025-11-14)
    private val musicLocalRepository: MusicLocalRepository,
    cacheStateManager: CacheStateManager  // 新增 (2025-11-20): 缓存状态管理器
) : ViewModel() {

    // ==================== 直接暴露 DataStore 的 StateFlow（单一数据源原则） ====================
    // 架构优化 (2025-12-02):
    // - 移除了 ViewModel 中的冗余状态
    // - 直接使用 DataStore 作为唯一数据源
    // - DataStore 的 StateFlow 会自动通知所有订阅者
    // - 消除了状态不同步的风险

    val userPhone: StateFlow<String?> = userPreferencesDataStore.userPhone
    val syncFavorites: StateFlow<Boolean> = userPreferencesDataStore.syncFavorites
    val syncHistory: StateFlow<Boolean> = userPreferencesDataStore.syncHistory
    val cacheSizeMB: StateFlow<Int> = userPreferencesDataStore.cacheSizeMB
    val fastCacheOnWiFi: StateFlow<Boolean> = userPreferencesDataStore.fastCacheOnWiFi
    val fastCacheOnMobile: StateFlow<Boolean> = userPreferencesDataStore.fastCacheOnMobile
    val maxCachedSongs: StateFlow<Int> = userPreferencesDataStore.maxCachedSongs
    val cacheEnabled: StateFlow<Boolean> = userPreferencesDataStore.cacheEnabled
    val debugModeEnabled: StateFlow<Boolean> = userPreferencesDataStore.debugModeEnabled
    val cacheEventLogEnabled: StateFlow<Boolean> = userPreferencesDataStore.cacheEventLogEnabled

    // 缓存优化配置 (2025-12-03)
    val enableCachePerformanceTracking: StateFlow<Boolean> = userPreferencesDataStore.enableCachePerformanceTracking
    val enableCacheDetailedLogging: StateFlow<Boolean> = userPreferencesDataStore.enableCacheDetailedLogging

    // ==================== 临时 UI 状态（不持久化） ====================

    // 缓存大小显示（来自 CacheStateManager）
    val cacheSize: StateFlow<String> = cacheStateManager.cacheSizeFormatted

    // 加载状态（临时 UI 状态）
    private val _isLoadingCache = MutableStateFlow(false)
    val isLoadingCache = _isLoadingCache.asStateFlow()
    /**
     * 获取默认最大缓存歌曲数 (2025-11-22)
     * 暴露给UI使用，根据设备存储空间动态确定
     */
    fun getDefaultMaxCachedSongs(): Int = userPreferencesDataStore.getDefaultMaxCachedSongs()
    /**
     * 获取允许的最大缓存歌曲数 (2025-11-22)
     * 暴露给UI使用，根据设备存储空间动态确定
     */
    fun getMaxAllowedCachedSongs(): Int = userPreferencesDataStore.getMaxAllowedCachedSongs()

    init {
        // 架构优化 (2025-12-02):
        // - 移除 loadSettings()，不再需要同步本地状态
        // - DataStore 的 StateFlow 会自动提供最新值
        // - 同步缓存事件日志开关到LogConfig
        LogConfig.ENABLE_CACHE_EVENT_LOG = userPreferencesDataStore.cacheEventLogEnabled.value
    }
    fun toggleSyncFavorites(enabled: Boolean) {
        viewModelScope.launch {
            // 架构优化 (2025-12-02): 只需调用 DataStore setter
            // StateFlow 会自动更新，无需手动同步本地状态
            userPreferencesDataStore.setSyncFavorites(enabled)
            if (enabled) {
                syncFavoritesUseCase()
            }
        }
    }

    fun toggleSyncHistory(enabled: Boolean) {
        viewModelScope.launch {
            // 架构优化 (2025-12-02): 只需调用 DataStore setter
            userPreferencesDataStore.setSyncHistory(enabled)
            if (enabled) {
                syncHistoryUseCase()
            }
        }
    }
    /**
     * 退出登录
     *
     * 执行流程 (架构优化 2025-12-02):
     * 1. 调用 UserPreferencesDataStore.logout() 清除本地存储的登录信息
     * 2. DataStore 的 StateFlow 自动更新，UI 自动响应状态变化，显示"游客"
     */
    fun logout() {
        viewModelScope.launch {
            userPreferencesDataStore.logout()
            // 架构优化: 不需要手动更新本地状态，DataStore StateFlow 会自动通知
        }
    }

    /**
     * 刷新用户信息 (2025-11-14新增)
     *
     * 用途:
     * - 登录成功后刷新用户状态
     * - DataStore 的 StateFlow 会自动提供最新值
     *
     * 架构优化 (2025-12-02):
     * - 此方法实际上不需要做任何事，因为 DataStore 会自动更新
     * - 保留此方法仅为兼容旧代码
     */
    fun refreshUserInfo() {
        // 架构优化: DataStore StateFlow 已经自动更新，无需手动刷新
    }
    /**
     * 加载缓存信息 (2025-11-12)
     *
     * 已废弃 (2025-11-20):
     * - 缓存大小现在由 CacheStateManager.cacheSizeFormatted 自动管理
     * - 数据库变化时自动触发更新
     * - 保留此方法仅为兼容旧代码，后续可移除
     *
     * @Deprecated("缓存大小由 CacheStateManager 自动管理，无需手动调用")
     */
    @Deprecated("缓存大小由 CacheStateManager 自动管理，无需手动调用")
    fun loadCacheInfo() {
        // 方法已废弃，保留空实现避免编译错误
        // 实际缓存大小由 CacheStateManager.cacheSizeFormatted 自动同步
    }
    /**
     * 清空所有缓存 (2025-11-25优化 - 直接清空)
     *
     * 功能:
     * - 清空音频缓存（ExoPlayer缓存的音频文件）
     * - 清空数据缓存（推荐歌单、播放URL等）
     * - 清空数据库缓存记录
     * - 缓存大小会通过CacheStateManager自动更新
     *
     * 使用场景:
     * - 用户点击"清空缓存"按钮后直接调用
     *
     * 重构 (2025-11-20):
     * - 移除loadCacheInfo()调用
     * - 缓存大小由CacheStateManager自动管理
     *
     * 优化 (2025-11-25):
     * - 移除确认对话框，直接清空（用户体验更流畅）
     * - 使用混合方案：removeResource() + 删除文件（彻底清理）
     * - 不调用 release()，避免 IllegalStateException
     * - 正在播放时也可以安全清空
     */
    fun clearCache() {
        viewModelScope.launch {
            _isLoadingCache.value = true
            try {
                LogConfig.d("ProfileViewModel", "开始清空缓存...")
                // 1. 清空数据库缓存记录
                musicLocalRepository.clearCachedRecords()
                LogConfig.d("ProfileViewModel", "数据库缓存记录已清空")
                // 2. 清空音频缓存文件（使用混合方案：API + 删除文件）
                audioCacheManager.clearCache()
                LogConfig.d("ProfileViewModel", "音频缓存文件已清空")
            } catch (e: Exception) {
                LogConfig.e("ProfileViewModel",
                    "ProfileViewModel clearCache 清空缓存失败: ${e.message}")
            } finally {
                _isLoadingCache.value = false
            }
        }
    }
    /**
     * 更新最大缓存歌曲数量 (2025-11-25优化)
     *
     * 功能:
     * - 设置数据库中保存的完整缓存歌曲数量上限
     * - 自动限制范围（5-100首）
     * - 保存到SharedPreferences
     * - 立即生效，自动清理超过上限的歌曲
     *
     * 清理策略 (2025-11-25):
     * - 与CacheStateManager的策略保持一致
     * - 白名单歌曲不会被清理
     * - 只清理非白名单歌曲，按最近播放时间排序
     * - 计算方式：maxNonProtectedCount = maxCount - protectedCount
     *
     * @param count 最大缓存歌曲数量
     */
    fun updateMaxCachedSongs(count: Int) {
        viewModelScope.launch {
            val oldValue = userPreferencesDataStore.maxCachedSongs.value
            userPreferencesDataStore.setMaxCachedSongs(count)
            // 架构优化: 不需要手动更新本地状态，DataStore StateFlow 会自动通知
            // 如果减少了上限，触发清理
            if (count < oldValue) {
                try {
                    val deleted = musicLocalRepository.trimCachedSongsWithProtection(count)
                    if (deleted > 0) {
                        LogConfig.d(LogConfig.TAG_PLAYER_VIEWMODEL, "清理了 $deleted 首超过上限的缓存歌曲（白名单已保护）")
                    }
                } catch (e: Exception) {
                    LogConfig.e(LogConfig.TAG_PLAYER_VIEWMODEL, "清理缓存歌曲失败: ${e.message}")
                }
            }
        }
    }
    /**
     * 切换WiFi下快速缓存开关 (2025-11-19)
     *
     * 功能:
     * - 启用后在WiFi环境下使用激进缓存策略
     * - ExoPlayer会快速缓存完整音频文件
     * - 6分钟以内的歌曲可在60秒内完整缓存
     * - 监听策略优化为60秒（vs 默认90秒+定时检查）
     *
     * 注意:
     * - 默认关闭，避免过度消耗流量
     * - 修改后需要重新播放歌曲才能生效
     * - 建议在WiFi环境下使用
     * - 优化 (2025-11-23): 仅在启用缓存时才允许操作
     *
     * @param enabled 是否启用快速缓存
     */
    fun toggleFastCacheOnWiFi(enabled: Boolean) {
        viewModelScope.launch {
            if (!userPreferencesDataStore.cacheEnabled.value && enabled) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "ProfileViewModel toggleFastCacheOnWiFi: 缓存功能已关闭，无法启用WiFi快速缓存"
                )
                return@launch
            }
            userPreferencesDataStore.setFastCacheOnWiFi(enabled)
            // 架构优化: 不需要手动更新本地状态，DataStore StateFlow 会自动通知
        }
    }
    /**
     * 切换4/5G下快速缓存开关 (2025-11-19)
     *
     * 功能:
     * - 启用后在移动网络环境下使用激进缓存策略
     * - 逻辑与WiFi下快速缓存一致
     *
     * 注意:
     * - 默认关闭，避免消耗移动流量
     * - 修改后需要重新播放歌曲才能生效
     * - 建议在流量充足时使用
     * - 优化 (2025-11-23): 仅在启用缓存时才允许操作
     *
     * @param enabled 是否启用快速缓存
     */
    fun toggleFastCacheOnMobile(enabled: Boolean) {
        viewModelScope.launch {
            if (!userPreferencesDataStore.cacheEnabled.value && enabled) {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "ProfileViewModel toggleFastCacheOnMobile: 缓存功能已关闭，无法启用移动网络快速缓存"
                )
                return@launch
            }
            userPreferencesDataStore.setFastCacheOnMobile(enabled)
            // 架构优化: 不需要手动更新本地状态，DataStore StateFlow 会自动通知
        }
    }
    /**
     * 切换缓存功能开关 (2025-11-23)
     *
     * 功能:
     * - 启用后：播放在线歌曲时自动边播边存
     * - 首次播放不额外消耗流量，再次播放不消耗流量
     * - 支持断网播放已缓存的歌曲
     *
     * 关闭后:
     * - 不再缓存新播放的歌曲
     * - 已缓存的歌曲仍可播放
     * - 可节省存储空间
     *
     * 注意:
     * - 关闭缓存会显著增加流量消耗（每次播放都需要重新加载）
     * - 断网情况下只能播放本地音乐和已缓存的歌曲
     * - 默认启用
     *
     * @param enabled 是否启用缓存功能
     */
    fun toggleCache(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setCacheEnabled(enabled)
            // 架构优化: 不需要手动更新本地状态，DataStore StateFlow 会自动通知
        }
    }

    /**
     * 切换性能跟踪 (2025-12-03)
     *
     * 功能:
     * - 启用后：记录每首歌曲的缓存性能（耗时、速度、文件大小等）
     * - 用于A/B测试对比不同优化策略的效果
     * - 支持导出性能报告
     *
     * @param enabled 是否启用性能跟踪
     */
    fun togglePerformanceTracking(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setEnableCachePerformanceTracking(enabled)
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "ProfileViewModel togglePerformanceTracking: 性能跟踪已${if (enabled) "启用" else "关闭"}"
            )
        }
    }

    /**
     * 切换详细日志 (2025-12-03)
     *
     * 功能:
     * - 启用后：输出完整的性能报告（包含歌曲名、URL、文件大小、速度等）
     * - 关闭后：仅输出简洁的性能摘要
     *
     * @param enabled 是否启用详细日志
     */
    fun toggleDetailedLogging(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setEnableCacheDetailedLogging(enabled)
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "ProfileViewModel toggleDetailedLogging: 详细日志已${if (enabled) "启用" else "关闭"}"
            )
        }
    }

    /**
     * 切换调试模式
     *
     * 功能:
     * - 启用/禁用调试模式
     * - 调试模式开启后，设置对话框中会显示调试相关开关
     * - 调试模式关闭后，所有调试开关隐藏且自动关闭
     *
     * 优化 (2025-12-04):
     * - 关闭调试模式时，自动关闭所有调试相关开关
     * - 包括：缓存事件日志、性能跟踪、详细日志
     * - 确保不会打印任何调试日志
     */
    fun toggleDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setDebugModeEnabled(enabled)

            // 关闭调试模式时，自动关闭所有调试相关开关
            if (!enabled) {
                // 关闭缓存事件日志
                userPreferencesDataStore.setCacheEventLogEnabled(false)
                LogConfig.ENABLE_CACHE_EVENT_LOG = false

                // 关闭性能跟踪
                userPreferencesDataStore.setEnableCachePerformanceTracking(false)

                // 关闭详细日志
                userPreferencesDataStore.setEnableCacheDetailedLogging(false)

                LogConfig.d("ProfileViewModel", "调试模式已关闭，所有调试开关已自动关闭")
            } else {
                LogConfig.d("ProfileViewModel", "调试模式已开启")
            }
        }
    }

    /**
     * 切换缓存事件日志
     *
     * 功能:
     * - 启用/禁用缓存管理器的详细日志输出
     * - 包括：ProtectedLruCacheEvictor、CacheStateManager
     * - 开启后会打印缓存添加、删除、淘汰、状态更新等详细事件
     * - 关闭后减少日志输出，提升性能
     */
    fun toggleCacheEventLog(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setCacheEventLogEnabled(enabled)
            // 架构优化: 不需要手动更新本地状态，DataStore StateFlow 会自动通知
            LogConfig.ENABLE_CACHE_EVENT_LOG = enabled
            LogConfig.d("ProfileViewModel", "缓存事件日志已${if (enabled) "开启" else "关闭"}")
        }
    }

}
