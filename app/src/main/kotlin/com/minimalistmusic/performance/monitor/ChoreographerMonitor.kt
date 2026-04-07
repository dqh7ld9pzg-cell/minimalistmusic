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

import android.os.Looper
import android.view.Choreographer
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import com.minimalistmusic.performance.reporter.PerformanceReporter
/**
 * Choreographer帧率监控器
 * 
 * 技术原理：通过Choreographer.FrameCallback在每一帧绘制前回调
 * 优势：兼容所有API版本（API 16+），性能开销小
 * 用途：实时监控帧率和卡顿情况
 */
class ChoreographerMonitor {
    private var isMonitoring = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNanos: Long = 0L
    private val frameIntervals = ArrayDeque<Long>(240) // 保存4秒数据（60fps × 4）
    private var frameStatsCallback: ((FrameStats) -> Unit)? = null
    
    // 统计变量
    private var totalFrames = 0
    private var jankyFrames = 0
    private var lastStatsTime = 0L
    private val statsInterval = 2000L // 每2秒统计一次
    
    companion object {
        private const val NANOS_PER_MILLIS = 1_000_000L
        private const val IDEAL_FRAME_TIME_NS = 16_666_667L // 60fps的帧时间
        private const val JANK_THRESHOLD_NS = 21_000_000L  // 超过21ms视为卡顿
    }
    
    /**
     * 开始帧率监控
     */
    fun start(onFrameStats: (FrameStats) -> Unit) {
        if (isMonitoring) return
        
        isMonitoring = true
        frameStatsCallback = onFrameStats
        lastFrameTimeNanos = System.nanoTime()
        lastStatsTime = System.currentTimeMillis()
        
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMonitoring) return
                
                val currentTime = System.nanoTime()
                val frameInterval = currentTime - lastFrameTimeNanos
                lastFrameTimeNanos = currentTime
                
                // 记录帧间隔
                recordFrameInterval(frameInterval)
                
                // 分析帧率
                analyzeFrameRate()
                
                // 继续监听下一帧
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        // 开始监听
        Choreographer.getInstance().postFrameCallback(frameCallback!!)
        
        logDebug("帧率监控已启动")
    }
    
    /**
     * 停止帧率监控
     */
    fun stop() {
        isMonitoring = false
        frameCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }
        frameCallback = null
        frameIntervals.clear()
        frameStatsCallback = null
        
        logDebug("帧率监控已停止")
    }
    
    /**
     * 获取当前帧率统计
     */
    fun getCurrentFrameStats(): FrameStats {
        val intervalsMs = frameIntervals.map { it / NANOS_PER_MILLIS.toDouble() }
        
        return FrameStats(
            totalFrames = totalFrames,
            jankyFrames = jankyFrames,
            jankPercent = if (totalFrames > 0) (jankyFrames.toDouble() / totalFrames * 100) else 0.0,
            maxFrameTimeMs = intervalsMs.maxOrNull() ?: 0.0,
            minFrameTimeMs = intervalsMs.minOrNull() ?: 0.0,
            avgFrameTimeMs = intervalsMs.average(),
            fps = calculateFPS(intervalsMs),
            frameTimeVariance = calculateVariance(intervalsMs)
        )
    }
    
    // ========== 私有方法 ==========
    
    private fun recordFrameInterval(interval: Long) {
        frameIntervals.addLast(interval)
        
        // 限制队列大小，防止内存无限增长
        if (frameIntervals.size > 240) {
            frameIntervals.removeFirst()
        }
        
        totalFrames++
        
        // 检测卡顿帧
        if (interval > JANK_THRESHOLD_NS) {
            jankyFrames++
            onJankFrameDetected(interval)
        }
    }
    
    private fun analyzeFrameRate() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStatsTime >= statsInterval) {
            calculateFrameStats()
            lastStatsTime = currentTime
        }
    }
    
    private fun calculateFrameStats() {
        if (frameIntervals.size < 10) return // 需要足够的数据
        
        val intervalsMs = frameIntervals.map { it / NANOS_PER_MILLIS.toDouble() }
        
        val stats = FrameStats(
            totalFrames = totalFrames,
            jankyFrames = jankyFrames,
            jankPercent = if (totalFrames > 0) (jankyFrames.toDouble() / totalFrames * 100) else 0.0,
            maxFrameTimeMs = intervalsMs.maxOrNull() ?: 0.0,
            minFrameTimeMs = intervalsMs.minOrNull() ?: 0.0,
            avgFrameTimeMs = intervalsMs.average(),
            fps = calculateFPS(intervalsMs),
            frameTimeVariance = calculateVariance(intervalsMs)
        )
        
        // 回调统计结果
        frameStatsCallback?.invoke(stats)
        
        // 重置计数
        totalFrames = 0
        jankyFrames = 0
    }
    
    private fun calculateFPS(intervalsMs: List<Double>): Double {
        val avgFrameTime = intervalsMs.average()
        return if (avgFrameTime > 0) 1000.0 / avgFrameTime else 0.0
    }
    
    private fun calculateVariance(values: List<Double>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
    
    private fun onJankFrameDetected(interval: Long) {
        val intervalMs = interval / NANOS_PER_MILLIS.toDouble()
        
        // 获取主线程堆栈，分析卡顿原因
        val mainThread = Looper.getMainLooper().thread
        val stackTrace = mainThread.stackTrace
        val simplifiedStack = getSimplifiedStackTrace(stackTrace)
        PerformanceReporter.recordMetric(
            PerformanceMetric.error(
                type = MetricType.UI_RENDER,
                name = "jank_frame_detected",
                value = intervalMs,
                metadata = mapOf(
                    "frame_time_ms" to intervalMs.toString(),
                    "stack_trace" to simplifiedStack
                )
            )
        )
        
        logDebug("检测到卡顿帧: ${intervalMs}ms")
    }
    
    private fun getSimplifiedStackTrace(stackTrace: Array<StackTraceElement>): String {
        return stackTrace
            .take(8) // 取前8个堆栈帧
            .filter { 
                // 过滤系统堆栈，关注应用代码
                !it.className.startsWith("android.") && 
                !it.className.startsWith("java.") &&
                !it.className.startsWith("kotlin.")
            }
            .joinToString(" <- ") { 
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }
    }
    
    private fun logDebug(message: String) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE_CHOREOGRAPHER, "ChoreographerMonitor: $message")
        }
    }
}
/**
 * 帧率统计数据类
 */
data class FrameStats(
    val totalFrames: Int,
    val jankyFrames: Int,
    val jankPercent: Double,
    val maxFrameTimeMs: Double,
    val minFrameTimeMs: Double,
    val avgFrameTimeMs: Double,
    val fps: Double,
    val frameTimeVariance: Double
)