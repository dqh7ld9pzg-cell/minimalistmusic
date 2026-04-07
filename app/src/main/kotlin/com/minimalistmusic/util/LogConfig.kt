package com.minimalistmusic.util

import android.util.Log
import com.minimalistmusic.BuildConfig

/**
 * LogConfig 统一控制日志开关,并控制日志分类打印
 * - Tag suffix约束: UI(x_ui)->ViewModel(x_viewmodel)->Domain(x_domain)->DataRepo(x_data_remote,x_data_local),x为统一前缀,比如:tag_player_viewmodel,tag_player_ui
 * - Message约束: FileName -> MethodName -> Message,比如:PlayerViewModel smartPreloadSurroundingSongs:
 *
 *   example:
 *        private suspend fun smartPreloadSurroundingSongs(songs: List<Song>, currentIndex: Int){
 *
 *        //logic code
 *
 *        LogConfig.d(
 *             LogConfig.TAG_PLAYER_VIEWMODEL,
 *             "PlayerViewModel smartPreloadSurroundingSongs: [预加载] 开始智能预加载：当前索引=$currentIndex, 预加载范围=${preloadIndices.firstOrNull() ?: "无"}~${preloadIndices.lastOrNull() ?: "无"}, 共${preloadIndices.size}首"
 *         ),
 *        }
 *
 */
object LogConfig {
    const val ENABLE_LOG_PERFORMANCE = false

    /**
     * LRU缓存事件日志开关 (2025-11-30)
     * - 控制ProtectedLruCacheEvictor中的日志打印
     * - 可通过调试模式在UI中动态控制
     * - 默认启用
     */
    @Volatile
    var ENABLE_CACHE_EVENT_LOG = true

    // Architecture Layer Tags
    const val TAG_PLAYER_VIEWMODEL = "viewmodel"
    const val TAG_PLAYER_UI = "ui"
    const val TAG_PLAYER_DOMAIN = "domain"
    const val TAG_PLAYER_DATA_REMOTE = "data_remote"
    const val TAG_PLAYER_DATA_LOCAL = "data_local"

    // Performance Monitoring Tags
    const val TAG_PERFORMANCE = "performance"
    const val TAG_PERFORMANCE_MONITOR = "performance_monitor"
    const val TAG_PERFORMANCE_MEMORY = "performance_memory"
    const val TAG_PERFORMANCE_STARTUP = "performance_startup"
    const val TAG_PERFORMANCE_UI = "performance_ui"
    const val TAG_PERFORMANCE_CHOREOGRAPHER = "performance_choreographer"
    const val TAG_PERFORMANCE_REPORTER = "performance_reporter"

    const val TAG_IMAGE_LOADING = "image_loading"

    fun d(tag: String, msg: String) {
        if(BuildConfig.DEBUG) {
            Log.d("app_arch_$tag",msg)
        }
    }
    fun w(tag: String, msg: String) {
        if(BuildConfig.DEBUG) {
            Log.w("app_arch_$tag",msg)
        }
    }
    fun e(tag: String, msg: String) {
        if(BuildConfig.DEBUG) {
            Log.e("app_arch_$tag",msg)
        }
    }

    fun e(tag: String, msg: String, throwable: Throwable?) {
        if(BuildConfig.DEBUG) {
            Log.e("app_arch_$tag", msg, throwable)
        }
    }
}