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

package com.minimalistmusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.domain.repository.PlaybackController
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import com.minimalistmusic.performance.monitor.MemoryPerformanceMonitor
import com.minimalistmusic.performance.monitor.NetworkPerformanceMonitor
import com.minimalistmusic.performance.monitor.PerformanceListener
import com.minimalistmusic.performance.monitor.PerformanceMonitor
import com.minimalistmusic.performance.monitor.StartupPerformanceMonitor
import com.minimalistmusic.performance.monitor.UIPerformanceMonitor
import com.minimalistmusic.performance.reporter.PerformanceReporter
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
@HiltAndroidApp
class MusicApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var performanceMonitor: PerformanceMonitor
    @Inject
    lateinit var memoryMonitor: MemoryPerformanceMonitor
    @Inject
    lateinit var networkMonitor: NetworkPerformanceMonitor
    @Inject
    lateinit var startupMonitor: StartupPerformanceMonitor
    @Inject
    lateinit var performanceReporter: PerformanceReporter
    @Inject
    lateinit var uiMonitor: UIPerformanceMonitor
    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore
    @Inject
    lateinit var playbackController: PlaybackController

    override fun onCreate() {
        // 必须在super.onCreate()之前记录启动时间，发现这样存在问题依赖未注入问题，挪到后面去了
//        startupMonitor.onApplicationCreate()
        // 必须先调用 super.onCreate() 完成依赖注入
        super.onCreate()
        startupMonitor.onApplicationCreate()

        // 初始化日志开关
        // 从UserPreferencesDataStore加载缓存事件日志开关状态
        LogConfig.ENABLE_CACHE_EVENT_LOG = userPreferencesDataStore.cacheEventLogEnabled.value

        // 绑定播放器Service
        // PlaybackRepository封装了MusicService的生命周期管理
        playbackController.bindService()
        LogConfig.d("MusicApplication", "PlaybackController Service绑定请求已发出")

        // 启动优化:
        // 延迟初始化性能监控，避免阻塞应用启动
        // 性能监控不是关键路径，可以在启动后异步初始化
        GlobalScope.launch(Dispatchers.Default) {
            // 延迟200ms后初始化性能监控
            delay(200)
            initializePerformanceMonitoring()
        }
    }
    private fun initializePerformanceMonitoring() {
        // 初始化PerformanceReporter单例
        PerformanceReporter.initialize(performanceReporter)
        // 启动性能监控
        performanceMonitor.initialize()
        // 启动各模块监控
        memoryMonitor.startMonitoring()
        // 添加全局监听器
        performanceMonitor.addListener(object : PerformanceListener {
            override fun onMetricRecorded(metric: PerformanceMetric) {
                // 实时处理关键指标
                if (metric.type == MetricType.ERROR && BuildConfig.DEBUG) {
                   if(LogConfig.ENABLE_LOG_PERFORMANCE){
                       LogConfig.w(LogConfig.TAG_PERFORMANCE, "MusicApplication onMetricRecorded: 性能告警: ${metric.name} = ${metric.value}")
                   }
                }
            }
            override fun onCriticalThresholdExceeded(metric: PerformanceMetric) {
                // 关键阈值 exceeded处理
                when (metric.name) {
                    "memory_leak_suspicion" -> {
                        if (BuildConfig.DEBUG) {
                            memoryMonitor.triggerMemoryAnalysis()
                        }
                    }
                    "slow_startup" -> {
                        // 可以发送通知给开发团队
                    }
                    "high_jank_rate" -> {
                        // UI卡顿严重，可能需要优化界面
                    }
                }
            }
        })
        if (BuildConfig.DEBUG) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE, "MusicApplication setupPerformanceMonitoring: 性能监控系统初始化完成")
        }
    }

    /**
     * Application终止时调用
     *
     * 注意：此方法在真机上永远不会被调用，仅在模拟器的特定情况下调用
     * 系统终止进程时会自动清理所有资源，包括Service绑定
     * 保留此方法仅为代码完整性
     */
    override fun onTerminate() {
        super.onTerminate()
        // 解绑播放器Service
        playbackController.unbindService()
        LogConfig.d("MusicApplication", "Application终止，PlaybackController Service已解绑")
    }

    /**
     * Coil图片加载器配置
     *
     * 优化策略:
     * 1. 图片尺寸压缩：自动调整图片大小以节省流量和内存
     * 2. 内存缓存：25% 可用内存用于图片缓存
     * 3. 磁盘缓存：100MB 磁盘缓存
     * 4. 网易云音乐CDN优化：添加缩略图参数
     *    - 例如：原图1.3MB → 缩略图(400x400) 约50KB
     *    - 参数：?param=400y400 (400x400像素，高质量)
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 使用25%的可用内存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB磁盘缓存
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        var request = chain.request()
                        val url = request.url.toString()
                        // 网易云音乐CDN图片优化：自动添加缩略图参数
                        if (url.contains("music.126.net") ) {
                            LogConfig.d(
                                LogConfig.TAG_PLAYER_DATA_REMOTE,
                                "推荐页高清图原地址 url：$url")
                            if(!url.contains("param=")){
                                val newUrl = "$url?param=600y600" // 800x800高质量缩略图
                                request = request.newBuilder()
                                    .url(newUrl)
                                    .build()
                                LogConfig.d(
                                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                                    "推荐页高清原图原地址省流地址 newUrl：$newUrl")
                            }
                        }
                        val startTime = System.currentTimeMillis()
                        val response = chain.proceed(request)
                        val duration = System.currentTimeMillis() - startTime
                        // 记录图片下载流量和耗时
                        val contentLength = response.body?.contentLength() ?: 0
                        if (BuildConfig.DEBUG && contentLength > 0) {
                            val sizeInKB = contentLength / 1024.0
                            val sizeInMB = sizeInKB / 1024.0
                            val sizeStr = if (sizeInMB >= 1) {
                                String.format("%.2f MB", sizeInMB)
                            } else {
                                String.format("%.2f KB", sizeInKB)
                            }
                            LogConfig.d(LogConfig.TAG_IMAGE_LOADING, "MusicApplication ImageLoading: 下载图片流量消耗: ${request.url.pathSegments.lastOrNull()} - 大小: $sizeStr, 耗时: ${duration}ms")
                        }
                        response
                    }
                    .build()
            }
            .respectCacheHeaders(false) // 忽略服务器缓存头，使用本地缓存策略
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger()) // Debug模式下启用日志
                }
            }
            .build()
    }
}
