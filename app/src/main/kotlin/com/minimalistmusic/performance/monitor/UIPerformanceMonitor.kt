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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import androidx.annotation.RequiresApi
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import com.minimalistmusic.performance.reporter.PerformanceReporter
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
/**
 * UI性能监控器
 * 
 * 监控目标详解：
 * 
 * 1. 帧率稳定性监控
 *   - 问题：帧率波动大，视觉上卡顿感明显
 *   - 影响：用户体验差，认为应用性能不佳
 *   - 监控：连续帧时间间隔和方差分析
 *   - 解决：优化布局层次，减少主线程耗时操作
 * 
 * 2. 渲染耗时监控
 *   - 问题：单帧渲染时间超过16.67ms（60fps）
 *   - 影响：掉帧现象，动画不流畅
 *   - 监控：measure、layout、draw各阶段耗时
 *   - 解决：使用ConstraintLayout减少布局层次，预计算尺寸
 * 
 * 3. 布局复杂度监控
 *   - 问题：视图层次过深或视图数量过多
 *   - 影响：测量和布局阶段耗时增加
 *   - 监控：View树深度和节点数量统计
 *   - 解决：扁平化布局，使用Merge标签，合理使用ViewStub
 * 
 * 4. 过度绘制监控
 *   - 问题：同一区域被多次绘制
 *   - 影响：GPU负载增加，耗电和发热
 *   - 监控：绘制区域重叠分析和层次评估
 *   - 解决：减少背景重叠，使用合适的绘制顺序
 */
@Singleton
class UIPerformanceMonitor @Inject constructor(
    private val performanceMonitor: PerformanceMonitor
) {
    private val frameMetricsCollectors = mutableMapOf<Window, FrameMetricsCollector>()
    private val choreographerMonitor = ChoreographerMonitor()
    private val layoutComplexityAnalyzer = LayoutComplexityAnalyzer()
    private val activityMonitors = mutableMapOf<Activity, ActivityUIMonitor>()
    
    /**
     * 开始监控指定Activity的UI性能
     * 
     * 监控范围：
     * - 帧率和卡顿检测（所有API版本）
     * - 详细渲染耗时（API 24+）
     * - 布局复杂度分析
     * - 过度绘制检测（需要开发者选项）
     */
    @RequiresApi(Build.VERSION_CODES.N)
    fun startMonitoring(activity: Activity) {
        val activityName = activity::class.java.simpleName
        
        // 启动FrameMetrics监控（API 24+）
        startFrameMetricsMonitoring(activity)
        
        // 启动Choreographer监控（兼容所有版本）
        startChoreographerMonitoring()
        
        // 启动布局复杂度监控
        startLayoutComplexityMonitoring(activity)
        
        // 创建Activity专属监控器
        activityMonitors[activity] = ActivityUIMonitor(activityName)
        
        logDebug("开始监控Activity UI性能: $activityName")
    }
    
    /**
     * 停止监控指定Activity的UI性能
     */
    fun stopMonitoring(activity: Activity) {
        stopFrameMetricsMonitoring(activity)
        activityMonitors.remove(activity)
        
        val activityName = activity::class.java.simpleName
        logDebug("停止监控Activity UI性能: $activityName")
    }
    
    /**
     * 手动记录UI操作耗时
     * 
     * 使用场景：自定义动画、复杂布局计算等
     * 示例：uiMonitor.recordUIOperation("list_scroll", 25.0)
     */
    fun recordUIOperation(operationName: String, durationMs: Double) {
        performanceMonitor.recordMetric(
            PerformanceMetric.ui(
                name = "ui_operation_$operationName",
                value = durationMs,
                metadata = mapOf("operation" to operationName)
            )
        )
        
        if (durationMs > 16.67) { // 超过一帧时间
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "slow_ui_operation",
                    value = durationMs,
                    metadata = mapOf(
                        "operation" to operationName,
                        "threshold_ms" to "16.67"
                    )
                )
            )
            
            logDebug("UI操作超时: $operationName, 耗时: ${durationMs}ms")
        }
    }
    
    /**
     * 记录列表滑动性能
     * 
     * 使用场景：RecyclerView/ListView滑动时调用
     * 监控目的：追踪列表滚动的流畅度
     */
    fun recordListScroll(scrollDistance: Int, durationMs: Double, listType: String) {
        val scrollSpeed = scrollDistance / durationMs // 像素/毫秒
        
        performanceMonitor.recordMetric(
            PerformanceMetric.ui(
                name = "list_scroll",
                value = scrollSpeed,
                metadata = mapOf(
                    "list_type" to listType,
                    "scroll_distance" to scrollDistance.toString(),
                    "duration_ms" to durationMs.toString(),
                    "scroll_speed" to scrollSpeed.toString()
                )
            )
        )
        
        // 检查滑动性能
        if (scrollSpeed < 0.5) { // 滑动速度过慢
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "slow_list_scroll",
                    value = scrollSpeed,
                    metadata = mapOf(
                        "list_type" to listType,
                        "threshold_speed" to "0.5"
                    )
                )
            )
        }
    }
    
    /**
     * 记录动画性能
     * 
     * 使用场景：属性动画、转场动画等
     * 监控目的：确保动画流畅不卡顿
     */
    fun recordAnimation(animationType: String, durationMs: Double, frameCount: Int) {
        val fps = if (durationMs > 0) frameCount * 1000.0 / durationMs else 0.0
        
        performanceMonitor.recordMetric(
            PerformanceMetric.ui(
                name = "animation_performance",
                value = fps,
                metadata = mapOf(
                    "animation_type" to animationType,
                    "duration_ms" to durationMs.toString(),
                    "frame_count" to frameCount.toString(),
                    "fps" to fps.toString()
                )
            )
        )
        
        if (fps < 50) { // 动画帧率过低
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "low_animation_fps",
                    value = fps,
                    metadata = mapOf(
                        "animation_type" to animationType,
                        "threshold_fps" to "50"
                    )
                )
            )
        }
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 启动FrameMetrics监控（API 24+）
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun startFrameMetricsMonitoring(activity: Activity) {
        val collector = FrameMetricsCollector(performanceMonitor)
        frameMetricsCollectors[activity.window] = collector
        
        activity.window.addOnFrameMetricsAvailableListener({ window, frameMetrics, dropCount ->
            collector.onFrameMetricsAvailable(frameMetrics, dropCount)
        }, Handler(Looper.getMainLooper()))
        
        logDebug("FrameMetrics监控已启动")
    }
    
    private fun stopFrameMetricsMonitoring(activity: Activity) {
        frameMetricsCollectors.remove(activity.window)?.cleanup()
    }
    
    /**
     * 启动Choreographer监控
     */
    private fun startChoreographerMonitoring() {
        choreographerMonitor.start { frameStats ->
            reportFrameStats(frameStats)
        }
        
        logDebug("Choreographer监控已启动")
    }
    
    /**
     * 启动布局复杂度监控
     */
    private fun startLayoutComplexityMonitoring(activity: Activity) {
        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(
            layoutComplexityAnalyzer
        )
        
        logDebug("布局复杂度监控已启动")
    }
    
    /**
     * 上报帧统计信息
     */
    private fun reportFrameStats(frameStats: FrameStats) {
        val metadata = mapOf(
            "total_frames" to frameStats.totalFrames.toString(),
            "janky_frames" to frameStats.jankyFrames.toString(),
            "jank_percent" to frameStats.jankPercent.toString(),
            "max_frame_time_ms" to frameStats.maxFrameTimeMs.toString(),
            "avg_frame_time_ms" to frameStats.avgFrameTimeMs.toString(),
            "fps" to frameStats.fps.toString(),
            "frame_time_variance" to frameStats.frameTimeVariance.toString()
        )
        
        performanceMonitor.recordMetric(
            PerformanceMetric.ui(
                name = "frame_performance",
                value = frameStats.jankPercent,
                metadata = metadata
            )
        )
        
        // 检查性能问题
        if (frameStats.jankPercent > 5.0) {
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "high_jank_rate",
                    value = frameStats.jankPercent,
                    metadata = metadata + mapOf("threshold" to "5.0")
                )
            )
            
            logDebug("高卡顿率: ${frameStats.jankPercent}%")
        }
        
        if (frameStats.fps < 50) {
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "low_fps",
                    value = frameStats.fps,
                    metadata = metadata + mapOf("threshold" to "50")
                )
            )
            
            logDebug("低帧率: ${frameStats.fps}fps")
        }
        
        if (frameStats.frameTimeVariance > 5.0) {
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "unstable_frame_rate",
                    value = frameStats.frameTimeVariance,
                    metadata = metadata + mapOf("threshold" to "5.0")
                )
            )
            
            logDebug("帧率不稳定: 方差=${frameStats.frameTimeVariance}")
        }
    }
    
    private fun logDebug(message: String) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE_UI, "UIPerformanceMonitor: $message")
        }
    }
}
/**
 * FrameMetrics收集器（API 24+）
 * 
 * 技术原理：通过Window.OnFrameMetricsAvailableListener获取详细的帧性能数据
 * 优势：提供精确的各阶段耗时（measure、layout、draw等）
 * 限制：仅支持API 24及以上
 */
@RequiresApi(Build.VERSION_CODES.N)
class FrameMetricsCollector(
    private val performanceMonitor: PerformanceMonitor
) {
    private val frameDurations = ArrayDeque<Long>(120) // 保存2秒的帧数据（60fps × 2）
    private val jankFrames = mutableListOf<JankFrame>()
    
    fun onFrameMetricsAvailable(frameMetrics: FrameMetrics, dropCount: Int) {
        val frameDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
        
        // 记录帧耗时
        frameDurations.addLast(frameDuration)
        if (frameDurations.size > 120) {
            frameDurations.removeFirst()
        }
        
        // 检测掉帧
        if (frameDuration > 16_666_667) { // 超过16.67ms
            recordJankFrame(frameDuration, dropCount, frameMetrics)
        }
        
        // 定期分析帧率稳定性
        if (frameDurations.size % 60 == 0) { // 每60帧分析一次
            analyzeFrameStability()
        }
        
        // 记录详细的渲染阶段耗时
        recordRenderStageMetrics(frameMetrics)
    }
    
    private fun recordJankFrame(duration: Long, dropCount: Int, metrics: FrameMetrics) {
        val jankFrame = JankFrame(
            timestamp = System.currentTimeMillis(),
            durationMs = duration / 1_000_000.0,
            dropCount = dropCount,
            layoutDuration = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
            drawDuration = metrics.getMetric(FrameMetrics.DRAW_DURATION),
            syncDuration = metrics.getMetric(FrameMetrics.SYNC_DURATION),
            commandIssueDuration = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
            swapBuffersDuration = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
        )
        
        jankFrames.add(jankFrame)
        
        performanceMonitor.recordMetric(
            PerformanceMetric.error(
                type = MetricType.UI_RENDER,
                name = "jank_frame",
                value = jankFrame.durationMs,
                metadata = mapOf(
                    "duration_ms" to jankFrame.durationMs.toString(),
                    "drop_count" to dropCount.toString(),
                    "layout_ms" to (jankFrame.layoutDuration / 1_000_000).toString(),
                    "draw_ms" to (jankFrame.drawDuration / 1_000_000).toString(),
                    "sync_ms" to (jankFrame.syncDuration / 1_000_000).toString()
                )
            )
        )
        
        // 限制jank帧记录数量
        if (jankFrames.size > 100) {
            jankFrames.removeAt(0)
        }
    }
    
    private fun analyzeFrameStability() {
        if (frameDurations.size < 30) return
        
        val durationsMs = frameDurations.map { it / 1_000_000.0 }
        val avgFrameTime = durationsMs.average()
        val maxFrameTime = durationsMs.maxOrNull() ?: 0.0
        val minFrameTime = durationsMs.minOrNull() ?: 0.0
        val frameTimeVariance = calculateVariance(durationsMs)
        
        val fps = if (avgFrameTime > 0) 1000.0 / avgFrameTime else 0.0
        
        performanceMonitor.recordMetric(
            PerformanceMetric.ui(
                name = "frame_stability",
                value = frameTimeVariance,
                metadata = mapOf(
                    "avg_frame_time_ms" to avgFrameTime.toString(),
                    "max_frame_time_ms" to maxFrameTime.toString(),
                    "min_frame_time_ms" to minFrameTime.toString(),
                    "variance" to frameTimeVariance.toString(),
                    "fps" to fps.toString()
                )
            )
        )
    }
    
    private fun recordRenderStageMetrics(metrics: FrameMetrics) {
        val stages = mapOf(
            "input_handling" to FrameMetrics.INPUT_HANDLING_DURATION,
            "animation" to FrameMetrics.ANIMATION_DURATION,
            "layout_measure" to FrameMetrics.LAYOUT_MEASURE_DURATION,
            "draw" to FrameMetrics.DRAW_DURATION,
            "sync" to FrameMetrics.SYNC_DURATION,
            "command_issue" to FrameMetrics.COMMAND_ISSUE_DURATION,
            "swap_buffers" to FrameMetrics.SWAP_BUFFERS_DURATION
        )
        
        stages.forEach { (stageName, metricId) ->
            val duration = metrics.getMetric(metricId)
            if (duration > 0) {
                performanceMonitor.recordMetric(
                    PerformanceMetric.ui(
                        name = "render_stage_$stageName",
                        value = duration / 1_000_000.0,
                        metadata = mapOf("stage" to stageName)
                    )
                )
            }
        }
    }
    
    private fun calculateVariance(values: List<Double>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
    
    fun cleanup() {
        frameDurations.clear()
        jankFrames.clear()
    }
}
/**
 * Jank帧详细信息
 */
data class JankFrame(
    val timestamp: Long,
    val durationMs: Double,
    val dropCount: Int,
    val layoutDuration: Long,
    val drawDuration: Long,
    val syncDuration: Long,
    val commandIssueDuration: Long,
    val swapBuffersDuration: Long
)
/**
 * 布局复杂度分析器
 */
class LayoutComplexityAnalyzer : ViewTreeObserver.OnGlobalLayoutListener {
    private var lastAnalysisTime = 0L
    private val analysisInterval = 2000L // 2秒分析一次
    
    override fun onGlobalLayout() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalysisTime < analysisInterval) return
        
        lastAnalysisTime = currentTime
        
        // 分析根视图的复杂度
        val rootView = getRootView() ?: return
        analyzeLayoutComplexity(rootView)
    }
    
    private fun analyzeLayoutComplexity(rootView: View) {
        val complexity = calculateLayoutComplexity(rootView)
        
        // 上报布局复杂度
        PerformanceReporter.recordMetric(
            PerformanceMetric.ui(
                name = "layout_complexity",
                value = complexity.maxDepth.toDouble(),
                metadata = mapOf(
                    "total_views" to complexity.totalViews.toString(),
                    "max_depth" to complexity.maxDepth.toString(),
                    "deep_views" to complexity.deepViews.toString(),
                    "complexity_score" to complexity.complexityScore.toString()
                )
            )
        )
        
        // 检测复杂布局
        if (complexity.totalViews > 100 || complexity.maxDepth > 10) {
            PerformanceReporter.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "complex_layout",
                    value = complexity.totalViews.toDouble(),
                    metadata = mapOf(
                        "total_views" to complexity.totalViews.toString(),
                        "max_depth" to complexity.maxDepth.toString(),
                        "threshold_views" to "100",
                        "threshold_depth" to "10"
                    )
                )
            )
        }
        
        if (complexity.complexityScore > 500) {
            PerformanceReporter.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.UI_RENDER,
                    name = "high_layout_complexity",
                    value = complexity.complexityScore.toDouble(),
                    metadata = mapOf("threshold_score" to "500")
                )
            )
        }
    }
    
    private fun calculateLayoutComplexity(view: View): LayoutComplexity {
        var totalViews = 0
        var maxDepth = 0
        var deepViews = 0
        
        fun traverseView(currentView: View, depth: Int) {
            totalViews++
            maxDepth = maxOf(maxDepth, depth)
            
            if (depth > 8) {
                deepViews++
            }
            
            if (currentView is ViewGroup) {
                for (i in 0 until currentView.childCount) {
                    traverseView(currentView.getChildAt(i), depth + 1)
                }
            }
        }
        
        traverseView(view, 1)
        
        // 计算复杂度评分（视图数量 × 最大深度）
        val complexityScore = totalViews * maxDepth
        
        return LayoutComplexity(totalViews, maxDepth, deepViews, complexityScore)
    }
    
    private fun getRootView(): View? {
        // 获取当前Activity的根视图
        // 实际实现需要通过Application的Activity栈管理
        return null // 简化实现
    }
}
/**
 * 布局复杂度数据
 *
 * 属性说明：
 * - totalViews: 视图树中总节点数量
 * - maxDepth: 视图树的最大深度（关键指标）
 * - deepViews: 深度超过8层的视图数量
 * - complexityScore: 复杂度评分 = totalViews × maxDepth
 */
data class LayoutComplexity(
    val totalViews: Int,      // 总视图数量
    val maxDepth: Int,        // 最大深度（关键指标）
    val deepViews: Int,       // 深度视图数量
    val complexityScore: Int  // 复杂度评分
)
/**
 * Activity专属UI监控器
 */
class ActivityUIMonitor(private val activityName: String) {
    private val uiOperations = mutableMapOf<String, Long>()
    
    fun startUIOperation(operationName: String) {
        uiOperations[operationName] = System.nanoTime()
    }
    
    fun endUIOperation(operationName: String) {
        val startTime = uiOperations[operationName] ?: return
        val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
        
        PerformanceReporter.recordMetric(
            PerformanceMetric.ui(
                name = "activity_ui_operation",
                value = durationMs,
                metadata = mapOf(
                    "activity" to activityName,
                    "operation" to operationName
                )
            )
        )
        
        uiOperations.remove(operationName)
    }
}