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

package com.minimalistmusic.performance.monitor

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
/**
 * 启动性能监控器
 * 
 * 监控目标详解：
 * 
 * 1. 冷启动时间监控
 *   - 问题：应用首次启动耗时过长
 *   - 影响：用户第一印象差，流失率增加
 *   - 监控：进程创建到首屏可交互的完整时间
 *   - 解决：优化Application初始化，延迟加载非关键组件
 * 
 * 2. 温启动时间监控
 *   - 问题：后台唤醒应用响应慢
 *   - 影响：用户体验不连贯，操作等待感强
 *   - 监控：后台状态到前台可交互的时间
 *   - 解决：合理管理后台资源，预加载关键数据
 * 
 * 3. 首屏渲染时间监控
 *   - 问题：界面显示但用户无法操作
 *   - 影响：用户误以为应用卡死，重复点击
 *   - 监控：Activity创建到内容绘制完成的时间
 *   - 解决：优化布局层次，减少主线程阻塞
 * 
 * 4. 关键路径监控
 *   - 问题：启动阶段某些操作耗时过长
 *   - 影响：整体启动时间被个别慢操作拖累
 *   - 监控：各初始化阶段的详细耗时
 *   - 解决：异步初始化，并行执行独立任务
 */
@Singleton
class StartupPerformanceMonitor @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) : DefaultLifecycleObserver {
    
    private var appCreateTime: Long = 0
    private var firstActivityCreateTime: Long = 0
    private var firstActivityResumeTime: Long = 0
    private var firstContentDrawTime: Long = 0
    
    private val startupPhases = mutableMapOf<String, Long>()
    private var isFirstActivity = true
    private var startupType: StartupType = StartupType.COLD
    // 启动阶段耗时阈值
    private val phaseThresholds = mapOf(
        "app_init" to 1000.0,
        "di_setup" to 500.0,
        "db_init" to 2000.0,
        "network_init" to 1000.0,
        "ui_setup" to 1500.0
    )
    /**
     * 记录应用创建时间点
     * 
     * 调用时机：Application.onCreate()开始时
     * 监控目的：建立启动时间基准点
     */
    fun onApplicationCreate() {
        appCreateTime = System.currentTimeMillis()
        startupType = determineStartupType()
        
        // 记录进程启动时间（更精确的基准）
        val processStartTime = getProcessStartTime()
        
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "application_create",
                value = 0.0,
                metadata = mapOf(
                    "startup_type" to startupType.name,
                    "app_create_time" to appCreateTime.toString(),
                    "process_start_time" to processStartTime.toString(),
                    "time_since_process_start" to (appCreateTime - processStartTime).toString()
                )
            )
        )
        
        // 开始监控关键启动路径
        startCriticalPathMonitoring()
        
        // 注册应用生命周期监听
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        logDebug("启动监控已初始化，启动类型: ${startupType.name}")
    }
    
    /**
     * 记录Activity创建时间点
     * 
     * 调用时机：Activity.onCreate()开始时
     * 监控目的：追踪界面创建耗时和顺序
     */
    fun onActivityCreate(activity: Activity) {
        val currentTime = System.currentTimeMillis()
        val activityName = activity::class.java.simpleName
        
        if (isFirstActivity) {
            firstActivityCreateTime = currentTime
            val timeToFirstActivity = firstActivityCreateTime - appCreateTime
            
            performanceMonitor.recordMetric(
                PerformanceMetric.startup(
                    name = "time_to_first_activity",
                    value = timeToFirstActivity.toDouble(),
                    metadata = mapOf(
                        "activity" to activityName,
                        "startup_type" to startupType.name,
                        "total_time_ms" to timeToFirstActivity.toString()
                    )
                )
            )
            
            isFirstActivity = false
            logDebug("首Activity创建耗时: ${timeToFirstActivity}ms")
        }
        
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "activity_create",
                value = (currentTime - appCreateTime).toDouble(),
                metadata = mapOf(
                    "activity" to activityName,
                    "startup_type" to startupType.name
                )
            )
        )
    }
    
    /**
     * 记录Activity恢复时间点
     * 
     * 调用时机：Activity.onResume()结束时
     * 监控目的：追踪界面可见和可交互时间
     */
    fun onActivityResume(activity: Activity) {
        val currentTime = System.currentTimeMillis()
        val activityName = activity::class.java.simpleName
        
        if (firstActivityResumeTime == 0L) {
            firstActivityResumeTime = currentTime
            val timeToFirstResume = firstActivityResumeTime - appCreateTime
            
            performanceMonitor.recordMetric(
                PerformanceMetric.startup(
                    name = "time_to_first_resume",
                    value = timeToFirstResume.toDouble(),
                    metadata = mapOf(
                        "activity" to activityName,
                        "startup_type" to startupType.name,
                        "total_time_ms" to timeToFirstResume.toString()
                    )
                )
            )
            
            logDebug("首Activity恢复耗时: ${timeToFirstResume}ms")
        }
        
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "activity_resume",
                value = (currentTime - appCreateTime).toDouble(),
                metadata = mapOf(
                    "activity" to activityName,
                    "startup_type" to startupType.name
                )
            )
        )
    }
    
    /**
     * 记录首屏内容绘制完成时间点
     * 
     * 调用时机：首屏内容完全绘制完成时
     * 监控目的：确定用户真正可操作的时间点
     */
    fun onFirstContentDraw() {
        if (firstContentDrawTime == 0L) {
            firstContentDrawTime = System.currentTimeMillis()
            val timeToFirstContent = firstContentDrawTime - appCreateTime
            
            performanceMonitor.recordMetric(
                PerformanceMetric.startup(
                    name = "time_to_first_content",
                    value = timeToFirstContent.toDouble(),
                    metadata = mapOf(
                        "startup_type" to startupType.name,
                        "total_time_ms" to timeToFirstContent.toString()
                    )
                )
            )
            
            // 启动完成，记录完整的启动时间
            recordCompleteStartupTime()
            
            logDebug("首屏内容绘制完成耗时: ${timeToFirstContent}ms")
        }
    }
    
    /**
     * 记录启动阶段开始
     * 
     * 使用场景：标记关键初始化操作的开始
     * 示例：startupMonitor.startPhase("database_init")
     */
    fun startPhase(phaseName: String) {
        startupPhases[phaseName] = System.nanoTime()
        logDebug("启动阶段开始: $phaseName")
    }
    
    /**
     * 记录启动阶段结束
     * 
     * 使用场景：标记关键初始化操作的结束
     * 示例：startupMonitor.endPhase("database_init")
     */
    fun endPhase(phaseName: String) {
        val startTime = startupPhases[phaseName] ?: return
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0
        
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "phase_$phaseName",
                value = durationMs,
                metadata = mapOf(
                    "phase" to phaseName,
                    "startup_type" to startupType.name
                )
            )
        )
        
        // 检查阶段耗时是否异常
        val threshold = getPhaseThreshold(phaseName)
        if (durationMs > threshold) {
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.STARTUP,
                    name = "slow_startup_phase",
                    value = durationMs,
                    metadata = mapOf(
                        "phase" to phaseName,
                        "threshold_ms" to threshold.toString(),
                        "actual_ms" to durationMs.toString()
                    )
                )
            )
            
            logDebug("启动阶段超时: $phaseName, 耗时: ${durationMs}ms, 阈值: ${threshold}ms")
        }
        
        logDebug("启动阶段完成: $phaseName, 耗时: ${durationMs}ms")
    }
    
    /**
     * 记录关键业务数据加载完成
     * 
     * 使用场景：核心业务数据准备就绪时调用
     * 监控目的：追踪业务层面的启动完成时间
     */
    fun onBusinessDataLoaded() {
        val dataLoadedTime = System.currentTimeMillis()
        val timeToDataLoaded = dataLoadedTime - appCreateTime
        
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "time_to_business_data",
                value = timeToDataLoaded.toDouble(),
                metadata = mapOf(
                    "startup_type" to startupType.name,
                    "total_time_ms" to timeToDataLoaded.toString()
                )
            )
        )
        
        logDebug("业务数据加载完成耗时: ${timeToDataLoaded}ms")
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 确定启动类型
     * 
     * 判断逻辑：
     * - 冷启动：进程首次创建或长时间后台后被系统杀死后重启
     * - 温启动：应用在后台被系统回收部分资源后恢复
     * - 热启动：应用完全在后台，只是切换到前台
     */
    private fun determineStartupType(): StartupType {
        return try {
            // 通过进程启动时间判断是否为冷启动
            val processStartTime = getProcessStartTime()
            val timeSinceProcessStart = System.currentTimeMillis() - processStartTime
            
            when {
                timeSinceProcessStart < 5000 -> StartupType.COLD   // 进程启动5秒内
                timeSinceProcessStart < 30000 -> StartupType.WARM  // 进程启动30秒内
                else -> StartupType.HOT                           // 进程已存在较长时间
            }
        } catch (e: Exception) {
            StartupType.UNKNOWN
        }
    }
    
    /**
     * 获取进程启动时间（Linux系统时间）
     */
    private fun getProcessStartTime(): Long {
        return try {
            val stat = File("/proc/self/stat").readText()
            val fields = stat.split(" ")
            // 第22个字段是进程启动时间（时钟滴答数）
            val startTimeTicks = fields[21].toLong()
            
            // 转换为毫秒（需要系统启动时间）
            val systemUptime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            systemUptime + (startTimeTicks * 1000 / getClockTicksPerSecond())
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    /**
     * 获取时钟滴答频率
     */
    private fun getClockTicksPerSecond(): Long {
        return try {
            File("/proc/self/stat").readText().split(" ")[7].toLong()
        } catch (e: Exception) {
            100 // 默认值
        }
    }
    
    /**
     * 记录完整的启动时间
     */
    private fun recordCompleteStartupTime() {
        val totalStartupTime = firstContentDrawTime - appCreateTime
        
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "total_startup_time",
                value = totalStartupTime.toDouble(),
                metadata = mapOf(
                    "startup_type" to startupType.name,
                    "total_time_ms" to totalStartupTime.toString(),
                    "app_create_to_activity_create" to (firstActivityCreateTime - appCreateTime).toString(),
                    "activity_create_to_resume" to (firstActivityResumeTime - firstActivityCreateTime).toString(),
                    "resume_to_content" to (firstContentDrawTime - firstActivityResumeTime).toString()
                )
            )
        )
        
        // 检查启动时间是否超过阈值
        val threshold = getStartupTimeThreshold(startupType)
        if (totalStartupTime > threshold) {
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.STARTUP,
                    name = "slow_startup",
                    value = totalStartupTime.toDouble(),
                    metadata = mapOf(
                        "startup_type" to startupType.name,
                        "threshold_ms" to threshold.toString(),
                        "actual_time_ms" to totalStartupTime.toString()
                    )
                )
            )
            
            logDebug("启动时间超时: ${totalStartupTime}ms, 阈值: ${threshold}ms")
        }
        
        logDebug("完整启动耗时: ${totalStartupTime}ms, 类型: ${startupType.name}")
    }
    
    /**
     * 启动关键路径监控
     */
    private fun startCriticalPathMonitoring() {
        // 监控主线程阻塞
        monitorMainThreadBlocking()
        
        // 监控启动阶段的资源竞争
        monitorResourceContention()
    }
    
    /**
     * 监控主线程阻塞
     */
    private fun monitorMainThreadBlocking() {
        val mainThread = Looper.getMainLooper().thread
        
        // 启动阶段加强监控（前30秒）
        Handler(Looper.getMainLooper()).postDelayed({
            val blockingMonitor = object : Runnable {
                override fun run() {
                    val stackTrace = mainThread.stackTrace
                    val blockedTime = analyzeStartupBlocking(stackTrace)
                    
                    if (blockedTime > 50) { // 启动阶段阈值较低
                        performanceMonitor.recordMetric(
                            PerformanceMetric.error(
                                type = MetricType.STARTUP,
                                name = "startup_main_thread_blocked",
                                value = blockedTime.toDouble(),
                                metadata = mapOf(
                                    "blocked_time_ms" to blockedTime.toString(),
                                    "phase" to "startup",
                                    "stack_trace" to getSimplifiedStackTrace(stackTrace)
                                )
                            )
                        )
                        
                        logDebug("启动阶段主线程阻塞: ${blockedTime}ms")
                    }
                    
                    // 继续监控（启动阶段监控频率较高）
                    Handler(Looper.getMainLooper()).postDelayed(this, 500)
                }
            }
            
            Handler(Looper.getMainLooper()).post(blockingMonitor)
        }, 1000)
    }
    
    /**
     * 分析启动阶段阻塞时间
     */
    private fun analyzeStartupBlocking(stackTrace: Array<StackTraceElement>): Long {
        // 分析堆栈，识别启动阶段的阻塞操作
        val blockingMethods = setOf(
            "ContentProvider.attachInfo",
            "Application.onCreate",
            "Activity.onCreate",
            "inflate",
            "setContentView",
            "obtainStyledAttributes",
            "measure",
            "layout",
            "draw"
        )
        
        val isStartupBlocking = stackTrace.any { element ->
            blockingMethods.any { method -> 
                element.methodName.contains(method) 
            }
        }
        
        return if (isStartupBlocking) {
            // 根据堆栈深度和方法的严重程度估算阻塞时间
            val severity = when {
                stackTrace.any { it.methodName.contains("measure") } -> 100
                stackTrace.any { it.methodName.contains("inflate") } -> 80
                stackTrace.any { it.methodName.contains("onCreate") } -> 60
                else -> 30
            }
            severity.toLong()
        } else {
            0
        }
    }
    
    /**
     * 监控资源竞争
     */
    private fun monitorResourceContention() {
        // 监控数据库初始化竞争
        monitorDatabaseContention()
        
        // 监控网络请求竞争
        monitorNetworkContention()
    }
    
    private fun monitorDatabaseContention() {
        // 实际实现需要通过数据库连接池监控或自定义Wrapper
    }
    
    private fun monitorNetworkContention() {
        // 实际实现需要通过OkHttp拦截器监控
    }
    
    /**
     * 获取阶段耗时阈值
     */
    private fun getPhaseThreshold(phaseName: String): Double {
        return phaseThresholds[phaseName] ?: 500.0
    }
    
    /**
     * 获取启动时间阈值
     */
    private fun getStartupTimeThreshold(startupType: StartupType): Long {
        return when (startupType) {
            StartupType.COLD -> 5000   // 冷启动5秒
            StartupType.WARM -> 2000   // 温启动2秒
            StartupType.HOT -> 1000    // 热启动1秒
            StartupType.UNKNOWN -> 3000
        }
    }
    
    /**
     * 获取简化的堆栈信息
     */
    private fun getSimplifiedStackTrace(stackTrace: Array<StackTraceElement>): String {
        return stackTrace
            .take(6)
            .filter { 
                // 过滤系统堆栈，关注应用代码
                !it.className.startsWith("android.") && 
                !it.className.startsWith("java.") &&
                !it.className.startsWith("kotlin.") &&
                !it.className.startsWith("androidx.")
            }
            .joinToString(" <- ") { 
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }
    }
    
    // ========== 生命周期回调 ==========
    
    override fun onStart(owner: LifecycleOwner) {
        // 应用进入前台
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "app_enter_foreground",
                value = 1.0,
                metadata = mapOf("timestamp" to System.currentTimeMillis().toString())
            )
        )
    }
    
    override fun onStop(owner: LifecycleOwner) {
        // 应用进入后台
        performanceMonitor.recordMetric(
            PerformanceMetric.startup(
                name = "app_enter_background", 
                value = 1.0,
                metadata = mapOf("timestamp" to System.currentTimeMillis().toString())
            )
        )
    }
    
    private fun logDebug(message: String) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE_STARTUP, "StartupPerformanceMonitor: $message")
        }
    }
}
/**
 * 启动类型枚举
 */
enum class StartupType {
    COLD,   // 冷启动：进程首次创建
    WARM,   // 温启动：部分资源已缓存
    HOT,    // 热启动：完全在内存中
    UNKNOWN // 未知类型
}