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

import android.app.Application
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.performance.config.PerformanceConfig
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import com.minimalistmusic.performance.reporter.PerformanceReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
/**
 * 性能监控核心管理器
 * 
 * 架构设计：
 * - 统一管理所有监控模块
 * - 控制数据采集和上报流程
 * - 提供配置管理和扩展接口
 * - 保证监控系统的高性能和低侵入性
 * 
 * 技术实现：
 * - 使用依赖注入管理组件生命周期
 * - 协程处理异步操作和定时任务
 * - 线程安全的数据结构存储指标
 * - 采样率控制平衡性能和数据量
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    private val application: Application,
    private val performanceReporter: PerformanceReporter,
    private val config: PerformanceConfig
) {
    private val performanceMetrics = ConcurrentHashMap<String, PerformanceMetric>()
    private val performanceListeners = CopyOnWriteArraySet<PerformanceListener>()
    private val monitoringScope = CoroutineScope(Dispatchers.IO + Job())
    
    private var isInitialized = false
    private var metricsFlusherJob: Job? = null
    
    /**
     * 初始化性能监控系统
     * 
     * 初始化流程：
     * 1. 验证配置有效性
     * 2. 启动各模块监控
     * 3. 开启数据上报任务
     * 4. 注册全局监听器
     * 
     * 设计考虑：
     * - 延迟初始化避免启动时性能影响
     * - 模块化启动便于管理和调试
     * - 异常处理保证系统稳定性
     */
    fun initialize() {
        if (isInitialized) {
            logDebug("性能监控系统已经初始化")
            return
        }
        
        try {
            logDebug("开始初始化性能监控系统")
            
            // 验证配置
            validateConfig()
            
            // 启动各模块监控
            if (config.enabledMetrics.contains(MetricType.STARTUP)) {
                logDebug("启动性能监控已启用")
            }
            
            if (config.enabledMetrics.contains(MetricType.MEMORY)) {
                logDebug("内存性能监控已启用")
            }
            
            if (config.enabledMetrics.contains(MetricType.UI_RENDER)) {
                logDebug("UI性能监控已启用")
            }
            
            if (config.enabledMetrics.contains(MetricType.NETWORK)) {
                logDebug("网络性能监控已启用")
            }
            
            // 启动数据上报任务
            startMetricsFlusher()
            
            isInitialized = true
            logDebug("性能监控系统初始化完成")
            
        } catch (e: Exception) {
            logError("性能监控系统初始化失败", e)
            // 不抛出异常，保证应用正常启动
        }
    }
    
    /**
     * 记录性能指标
     * 
     * 处理流程：
     * 1. 采样率检查，控制数据量
     * 2. 数据验证和预处理
     * 3. 存储到临时缓存
     * 4. 通知监听器处理
     * 
     * 性能优化：
     * - ConcurrentHashMap保证线程安全
     * - 采样率减少存储和计算开销
     * - 异步处理避免阻塞调用线程
     */
    fun recordMetric(metric: PerformanceMetric) {
        if (!isInitialized) return
        
        // 采样率控制
        if (!shouldSample(metric.type)) return
        
        try {
            // 存储指标
            performanceMetrics[metric.id] = metric
            
            // 通知监听器
            performanceListeners.forEach { listener ->
                try {
                    listener.onMetricRecorded(metric)
                    
                    // 检查关键阈值
                    if (isCriticalMetric(metric)) {
                        listener.onCriticalThresholdExceeded(metric)
                    }
                } catch (e: Exception) {
                    logError("性能监听器处理失败", e)
                }
            }
        } catch (e: Exception) {
            logError("记录性能指标失败", e)
        }
    }
    
    /**
     * 添加性能监听器
     * 
     * 使用场景：
     * - 实时告警：监控关键指标异常
     * - 调试分析：开发阶段详细日志
     * - 数据预处理：指标数据格式化
     * - 第三方集成：对接外部监控系统
     */
    fun addListener(listener: PerformanceListener) {
        performanceListeners.add(listener)
    }
    
    /**
     * 移除性能监听器
     */
    fun removeListener(listener: PerformanceListener) {
        performanceListeners.remove(listener)
    }
    
    /**
     * 停止性能监控
     * 
     * 清理流程：
     * 1. 停止数据上报任务
     * 2. 清空缓存数据
     * 3. 移除所有监听器
     * 4. 标记系统状态
     */
    fun shutdown() {
        logDebug("开始关闭性能监控系统")
        
        metricsFlusherJob?.cancel()
        performanceMetrics.clear()
        performanceListeners.clear()
        isInitialized = false
        
        logDebug("性能监控系统已关闭")
    }
    
    /**
     * 强制立即上报所有缓存数据
     * 
     * 使用场景：
     * - 应用退出前确保数据不丢失
     * - 手动触发数据上报
     * - 测试验证数据流程
     */
    suspend fun flushImmediately(): Boolean {
        return if (performanceMetrics.isNotEmpty()) {
            val metricsToFlush = performanceMetrics.values.toList()
            performanceMetrics.clear()
            
            val success = performanceReporter.report(metricsToFlush)
            if (!success) {
                // 上报失败，重新存储数据
                metricsToFlush.forEach { performanceMetrics[it.id] = it }
            }
            success
        } else {
            true
        }
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 启动指标上报任务
     * 
     * 设计特点：
     * - 定时批量上报减少网络请求
     * - 失败重试机制保证数据不丢失
     * - 可配置的上报间隔和批量大小
     */
    private fun startMetricsFlusher() {
        metricsFlusherJob = monitoringScope.launch {
            while (isActive) {
                delay(config.flushInterval)
                flushMetrics()
            }
        }
    }
    
    /**
     * 批量上报指标数据
     */
    private suspend fun flushMetrics() {
        val metricsToFlush = performanceMetrics.values.take(config.batchSize)
        if (metricsToFlush.isNotEmpty()) {
            val success = performanceReporter.report(metricsToFlush)
            if (success) {
                // 上报成功，移除已上报数据
                metricsToFlush.forEach { performanceMetrics.remove(it.id) }
                logDebug("成功上报 ${metricsToFlush.size} 个性能指标")
            } else {
                logDebug("性能指标上报失败，等待下次重试")
            }
        }
    }
    
    /**
     * 采样率检查
     * 
     * 采样策略：
     * - 错误类型指标：100%采样
     * - 关键指标：根据配置采样率
     * - 随机采样保证数据代表性
     */
    private fun shouldSample(metricType: MetricType): Boolean {
        return when (metricType) {
            MetricType.ERROR -> true
            else -> Random.nextDouble() <= config.sampleRate
        }
    }
    
    /**
     * 验证配置有效性
     */
    private fun validateConfig() {
        require(config.sampleRate in 0.0..1.0) { "采样率必须在0-1之间" }
        require(config.batchSize > 0) { "批量大小必须大于0" }
    }
    
    /**
     * 检查是否为关键指标
     */
    private fun isCriticalMetric(metric: PerformanceMetric): Boolean {
        return when {
            metric.type == MetricType.ERROR -> true
            metric.value > getCriticalThreshold(metric.name) -> true
            else -> false
        }
    }
    
    /**
     * 获取关键阈值
     */
    private fun getCriticalThreshold(metricName: String): Double {
        return when (metricName) {
            "slow_request" -> config.networkConfig.slowRequestThresholdMs.toDouble()
            "memory_usage" -> config.memoryConfig.memoryLeakThreshold * 100
            "jank_frame" -> config.uiConfig.jankThresholdMs
            else -> Double.MAX_VALUE
        }
    }
    
    /**
     * 调试日志输出
     */
    private fun logDebug(message: String) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE_MONITOR, "PerformanceMonitor: $message")
        }
    }

    private fun logError(message: String, exception: Exception? = null) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            if (exception != null) {
                LogConfig.e(LogConfig.TAG_PERFORMANCE_MONITOR, "PerformanceMonitor: $message", exception)
            } else {
                LogConfig.e(LogConfig.TAG_PERFORMANCE_MONITOR, "PerformanceMonitor: $message")
            }
        }
    }
}
/**
 * 性能监控监听器接口
 * 
 * 扩展能力：
 * - 实时告警：监控关键指标异常
 * - 数据预处理：格式化或过滤指标
 * - 多路输出：同时上报多个后端
 * - 本地存储：持久化重要指标
 */
interface PerformanceListener {
    fun onMetricRecorded(metric: PerformanceMetric)
    fun onCriticalThresholdExceeded(metric: PerformanceMetric)
}