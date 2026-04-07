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

package com.minimalistmusic.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore 实现的用户偏好设置管理
 *
 * 架构设计（2025-12-02重构）:
 * - 使用 StateFlow + stateIn() 提供内存缓存
 * - 支持同步访问(.value)和异步订阅(Flow)
 * - 避免使用 runBlocking，完全异步
 * - 自动响应式更新
 *
 * 核心优势：
 * 1. **性能优化**: StateFlow自动缓存在内存，避免频繁读取DataStore
 * 2. **同步访问**: 通过.value属性可以立即获取当前值（非阻塞）
 * 3. **响应式**: DataStore变化时自动更新所有订阅者
 * 4. **类型安全**: 编译时类型检查
 *
 * 使用示例:
 * ```kotlin
 * // 同步访问（立即返回内存中的值）
 * val isLoggedIn = userPreferencesDataStore.isLoggedIn.value
 *
 * // 异步订阅（响应式更新UI）
 * userPreferencesDataStore.isLoggedIn.collectAsState()
 *
 * // 修改值（suspend函数）
 * userPreferencesDataStore.setIsLoggedIn(true)
 * ```
 */

// DataStore 扩展属性（单例）
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // 使用应用级别的CoroutineScope，确保StateFlow在整个应用生命周期内存在
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val DEFAULT_CACHE_SIZE_MB = 800
        const val MIN_CACHE_SIZE_MB = 10
        const val MAX_CACHE_SIZE_MB = 10240
        const val DEFAULT_MAX_CACHED_SONGS_HIGH = 100
        const val DEFAULT_MAX_CACHED_SONGS_LOW = 50
        const val MIN_CACHED_SONGS = 10
        const val MAX_CACHED_SONGS_HIGH = 200
        const val MAX_CACHED_SONGS_LOW = 100
        const val STORAGE_THRESHOLD_GB = 10L
    }

    /**
     * Preferences Keys 定义
     */
    private object Keys {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val USER_TOKEN = stringPreferencesKey("user_token")
        val SYNC_FAVORITES = booleanPreferencesKey("sync_favorites")
        val SYNC_HISTORY = booleanPreferencesKey("sync_history")
        val DONT_SHOW_SYNC_REMINDER = booleanPreferencesKey("dont_show_sync_reminder")
        val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
        val FAST_CACHE_ON_WIFI = booleanPreferencesKey("fast_cache_on_wifi")
        val FAST_CACHE_ON_MOBILE = booleanPreferencesKey("fast_cache_on_mobile")
        val MAX_CACHED_SONGS = intPreferencesKey("max_cached_songs")
        val CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        val DEBUG_MODE_ENABLED = booleanPreferencesKey("debug_mode_enabled")
        val CACHE_EVENT_LOG_ENABLED = booleanPreferencesKey("cache_event_log_enabled")

        // 缓存优化配置 (2025-12-03)
        val ENABLE_CACHE_PERFORMANCE_TRACKING = booleanPreferencesKey("enable_cache_performance_tracking")
        val ENABLE_CACHE_DETAILED_LOGGING = booleanPreferencesKey("enable_cache_detailed_logging")
    }

    // ==================== StateFlow 版本（推荐使用，支持同步和异步） ====================

    /**
     * 登录状态 StateFlow
     * - 同步访问: isLoggedIn.value
     * - 异步订阅: isLoggedIn.collect { }
     */
    val isLoggedIn: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.IS_LOGGED_IN] ?: false
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly, // 立即开始收集
            initialValue = false
        )

    val userPhone: StateFlow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.USER_PHONE] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val userToken: StateFlow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.USER_TOKEN] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val syncFavorites: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.SYNC_FAVORITES] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val syncHistory: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.SYNC_HISTORY] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val dontShowSyncReminder: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.DONT_SHOW_SYNC_REMINDER] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val cacheSizeMB: StateFlow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.CACHE_SIZE_MB] ?: DEFAULT_CACHE_SIZE_MB }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_CACHE_SIZE_MB)

    val fastCacheOnWiFi: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.FAST_CACHE_ON_WIFI] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val fastCacheOnMobile: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.FAST_CACHE_ON_MOBILE] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val maxCachedSongs: StateFlow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.MAX_CACHED_SONGS] ?: getDefaultMaxCachedSongs() }
        .stateIn(scope, SharingStarted.Eagerly, getDefaultMaxCachedSongs())

    val cacheEnabled: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.CACHE_ENABLED] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val debugModeEnabled: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.DEBUG_MODE_ENABLED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val cacheEventLogEnabled: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.CACHE_EVENT_LOG_ENABLED] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val enableCachePerformanceTracking: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.ENABLE_CACHE_PERFORMANCE_TRACKING] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val enableCacheDetailedLogging: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { it[Keys.ENABLE_CACHE_DETAILED_LOGGING] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // ==================== 写入方法（挂起函数） ====================

    suspend fun setIsLoggedIn(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.IS_LOGGED_IN] = value
        }
    }

    suspend fun setUserPhone(value: String?) {
        dataStore.edit { preferences ->
            if (value != null) {
                preferences[Keys.USER_PHONE] = value
            } else {
                preferences.remove(Keys.USER_PHONE)
            }
        }
    }

    suspend fun setUserToken(value: String?) {
        dataStore.edit { preferences ->
            if (value != null) {
                preferences[Keys.USER_TOKEN] = value
            } else {
                preferences.remove(Keys.USER_TOKEN)
            }
        }
    }

    suspend fun setSyncFavorites(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SYNC_FAVORITES] = value
        }
    }

    suspend fun setSyncHistory(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SYNC_HISTORY] = value
        }
    }

    suspend fun setDontShowSyncReminder(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DONT_SHOW_SYNC_REMINDER] = value
        }
    }

    suspend fun setCacheSizeMB(value: Int) {
        val clampedValue = value.coerceIn(MIN_CACHE_SIZE_MB, MAX_CACHE_SIZE_MB)
        dataStore.edit { preferences ->
            preferences[Keys.CACHE_SIZE_MB] = clampedValue
        }
    }

    suspend fun setFastCacheOnWiFi(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.FAST_CACHE_ON_WIFI] = value
        }
    }

    suspend fun setFastCacheOnMobile(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.FAST_CACHE_ON_MOBILE] = value
        }
    }

    suspend fun setMaxCachedSongs(value: Int) {
        val maxAllowed = getMaxAllowedCachedSongs()
        val clampedValue = value.coerceIn(MIN_CACHED_SONGS, maxAllowed)

        dataStore.edit { preferences ->
            preferences[Keys.MAX_CACHED_SONGS] = clampedValue

            // 自动计算并更新 cacheSizeMB
            val calculatedCacheSizeMB = (clampedValue * 8 * 1.5).toInt()
            val finalCacheSizeMB = calculatedCacheSizeMB.coerceIn(
                MIN_CACHE_SIZE_MB,
                MAX_CACHE_SIZE_MB
            )
            preferences[Keys.CACHE_SIZE_MB] = finalCacheSizeMB
        }
    }

    suspend fun setCacheEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.CACHE_ENABLED] = value
        }
    }

    suspend fun setDebugModeEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DEBUG_MODE_ENABLED] = value
        }
    }

    suspend fun setCacheEventLogEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.CACHE_EVENT_LOG_ENABLED] = value
        }
    }

    suspend fun setEnableCachePerformanceTracking(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ENABLE_CACHE_PERFORMANCE_TRACKING] = value
        }
    }

    suspend fun setEnableCacheDetailedLogging(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ENABLE_CACHE_DETAILED_LOGGING] = value
        }
    }

    // ==================== 事务性操作 ====================

    suspend fun login(phone: String, token: String = "") {
        dataStore.edit { preferences ->
            preferences[Keys.IS_LOGGED_IN] = true
            preferences[Keys.USER_PHONE] = phone
            if (token.isNotEmpty()) {
                preferences[Keys.USER_TOKEN] = token
            }
        }
    }

    suspend fun logout() {
        dataStore.edit { preferences ->
            preferences[Keys.IS_LOGGED_IN] = false
            preferences.remove(Keys.USER_PHONE)
            preferences.remove(Keys.USER_TOKEN)
            preferences[Keys.SYNC_FAVORITES] = true
            preferences[Keys.SYNC_HISTORY] = false
        }
    }

    // ==================== 辅助方法 ====================

    fun calculateCacheSizeFromSongCount(songCount: Int): Int {
        val calculatedSize = (songCount * 8 * 1.5).toInt()
        return calculatedSize.coerceIn(MIN_CACHE_SIZE_MB, MAX_CACHE_SIZE_MB)
    }

    fun getDefaultMaxCachedSongs(): Int {
        return if (isHighStorageDevice()) {
            DEFAULT_MAX_CACHED_SONGS_HIGH
        } else {
            DEFAULT_MAX_CACHED_SONGS_LOW
        }
    }

    fun getMaxAllowedCachedSongs(): Int {
        return if (isHighStorageDevice()) {
            MAX_CACHED_SONGS_HIGH
        } else {
            MAX_CACHED_SONGS_LOW
        }
    }

    fun isHighStorageDevice(): Boolean {
        return try {
            val dataDir = android.os.Environment.getDataDirectory()
            val stat = android.os.StatFs(dataDir.path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val availableGB = availableBytes / (1024 * 1024 * 1024)
            availableGB >= STORAGE_THRESHOLD_GB
        } catch (e: Exception) {
            false
        }
    }
}
