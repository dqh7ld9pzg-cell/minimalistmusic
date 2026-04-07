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

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.performance.config.MemoryConfig
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
/**
 * 内存性能监控器
 * 
 * 监控目标详解：
 * 
 * 1. 内存泄漏检测
 *   - 问题：Activity/Fragment未正确释放导致内存持续增长
 *   - 影响：应用OOM崩溃，用户体验下降
 *   - 监控：内存使用趋势分析，识别异常增长模式
 *   - 解决：使用LeakCanary等工具定位泄漏点
 * 
 * 2. 内存抖动监控  
 *   - 问题：频繁创建销毁对象导致GC卡顿
 *   - 影响：界面卡顿，响应延迟
 *   - 监控：GC频率和对象分配模式
 *   - 解决：对象池化，减少临时对象创建
 * 
 * 3. 大对象分配追踪
 *   - 问题：大Bitmap、数组等占用过多内存
 *   - 影响：内存碎片，GC效率下降
 *   - 监控：大对象分配堆栈和频率
 *   - 解决：图片压缩，数据分页加载
 * 
 * 4. 堆内存趋势分析
 *   - 问题：内存使用无限制增长
 *   - 影响：系统稳定性风险
 *   - 监控：长期内存使用模式和增长速率
 *   - 解决：合理设置缓存大小，及时释放资源
 */
@Singleton
class MemoryPerformanceMonitor @Inject constructor(
    private val performanceMonitor: PerformanceMonitor,
    private val context: Context,
    private val config: MemoryConfig
) {
    private val handler = Handler(Looper.getMainLooper())
    private val monitoringScope = CoroutineScope(Dispatchers.Default + Job())
    
    private val memoryHistory = ArrayDeque<MemorySnapshot>(60) // 保留最近60个采样点
    private var isMonitoring = false
    private var samplingJob: Job? = null
    
    // GC监控
    private val lastGcCount = AtomicLong(0)
    private var gcEventCount = 0
    
    /**
     * 开始内存监控
     * 
     * 监控策略：
     * - 定期采样：每30秒收集一次内存快照
     * - 趋势分析：基于历史数据识别异常模式
     * - 阈值告警：超过配置阈值立即上报
     * - 低性能影响：采样间隔合理，异步处理
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        logDebug("开始内存监控")
        
        // 启动定期内存采样
        samplingJob = monitoringScope.launch {
            while (isActive && isMonitoring) {
                collectMemorySnapshot()
                delay(config.samplingInterval)
            }
        }
        
        // 设置GC监控
        setupGCMonitoring()
        
        logDebug("内存监控已启动，采样间隔: ${config.samplingInterval}")
    }
    
    /**
     * 停止内存监控
     */
    fun stopMonitoring() {
        isMonitoring = false
        samplingJob?.cancel()
        memoryHistory.clear()
        logDebug("内存监控已停止")
    }
    
    /**
     * 手动触发内存分析
     * 
     * 使用场景：
     * - 测试阶段验证监控功能
     * - 用户反馈卡顿时主动分析
     * - 特定操作前后对比内存变化
     */
    fun triggerMemoryAnalysis() {
        monitoringScope.launch {
            collectMemorySnapshot()
            logDebug("手动内存分析完成")
        }
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 收集完整的内存快照
     * 
     * 采集数据：
     * - 基础内存信息：使用量、最大值、使用率
     * - GC统计：GC次数和频率
     * - 组件数量：活跃Activity/Fragment计数
     * - 系统信息：可用内存、低内存状态
     */
    private suspend fun collectMemorySnapshot() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
            
            // 创建内存快照
            val snapshot = MemorySnapshot(
                timestamp = System.currentTimeMillis(),
                usedMemoryBytes = usedMemory,
                maxMemoryBytes = maxMemory,
                memoryUsagePercent = memoryUsagePercent,
                gcCount = getGCCountSinceLastSample(),
                activityCount = getActiveActivityCount(),
                fragmentCount = getActiveFragmentCount()
            )
            
            // 存储历史数据
            memoryHistory.addLast(snapshot)
            if (memoryHistory.size > 60) {
                memoryHistory.removeFirst()
            }
            
            // 分析内存趋势
            analyzeMemoryTrend(snapshot)
            
            // 上报基础内存指标
            reportBasicMemoryMetrics(snapshot)
            
        } catch (e: Exception) {
            logError("内存快照收集失败", e)
        }
    }
    
    /**
     * 分析内存趋势，检测异常模式
     * 
     * 检测算法：
     * 1. 内存泄漏检测：线性回归分析内存增长趋势
     * 2. 内存抖动检测：GC频率超过阈值
     * 3. 堆异常增长：短期内存大幅增长
     * 4. 内存压力：持续高内存使用率
     */
    // 在 MemoryPerformanceMonitor.kt 中修改 analyzeMemoryTrend 方法
    /**
     * 分析内存趋势，检测异常模式
     */
    private fun analyzeMemoryTrend(currentSnapshot: MemorySnapshot) {
        if (memoryHistory.size < 10) return // 需要足够的历史数据
        // 修复：使用兼容的写法替代 takeLast
//        val recentSnapshots = if (memoryHistory.size >= 10) {
//            memoryHistory.toList().subList(memoryHistory.size - 10, memoryHistory.size)
//        } else {
//            memoryHistory.toList()
//        }
        // 使用兼容性函数 与上面修复方法等效
        val recentSnapshots = memoryHistory.toList().takeLastCompat(10)
        // 检测内存泄漏模式
        val isMemoryGrowing = isMemoryConsistentlyGrowing(recentSnapshots)
        val isHighUsage = currentSnapshot.memoryUsagePercent > config.memoryLeakThreshold * 100
        if (isMemoryGrowing && isHighUsage) {
            reportMemoryLeakSuspicion(currentSnapshot, recentSnapshots)
        }
        // 检测内存抖动
        if (currentSnapshot.gcCount > config.gcWatchThreshold) {
            reportMemoryChurn(currentSnapshot)
        }
        // 检测堆异常增长
        val heapGrowth = calculateHeapGrowth()
        if (heapGrowth > config.heapGrowthThreshold) {
            reportHeapGrowthAnomaly(currentSnapshot, heapGrowth)
        }
    }
//    private fun analyzeMemoryTrend(currentSnapshot: MemorySnapshot) {
//        if (memoryHistory.size < 10) return // 需要足够的历史数据
//
//        val recentSnapshots = memoryHistory.takeLast(10)
//
//        // 检测内存泄漏模式
//        val isMemoryGrowing = isMemoryConsistentlyGrowing(recentSnapshots)
//        val isHighUsage = currentSnapshot.memoryUsagePercent > config.memoryLeakThreshold * 100
//
//        if (isMemoryGrowing && isHighUsage) {
//            reportMemoryLeakSuspicion(currentSnapshot, recentSnapshots)
//        }
//
//        // 检测内存抖动
//        if (currentSnapshot.gcCount > config.gcWatchThreshold) {
//            reportMemoryChurn(currentSnapshot)
//        }
//
//        // 检测堆异常增长
//        val heapGrowth = calculateHeapGrowth()
//        if (heapGrowth > config.heapGrowthThreshold) {
//            reportHeapGrowthAnomaly(currentSnapshot, heapGrowth)
//        }
//    }
//
    /**
     * 检测内存是否持续增长（线性回归分析）
     */
    private fun isMemoryConsistentlyGrowing(snapshots: List<MemorySnapshot>): Boolean {
        if (snapshots.size < 3) return false
        
        val memoryValues = snapshots.map { it.usedMemoryBytes }
        val trend = calculateLinearTrend(memoryValues)
        
        // 趋势斜率大于0.1表示内存持续增长
        return trend > 0.1
    }
    
    /**
     * 计算线性趋势（简单线性回归）
     */
    private fun calculateLinearTrend(values: List<Long>): Double {
        val n = values.size.toDouble()
        val sumX = (0 until values.size).sum().toDouble()
        val sumY = values.sum().toDouble()
        val sumXY = values.mapIndexed { index, value -> index * value }.sum().toDouble()
        val sumX2 = (0 until values.size).sumOf { it * it }.toDouble()
        
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
    }
    
    /**
     * 计算堆内存增长倍数
     */
    private fun calculateHeapGrowth(): Double {
        if (memoryHistory.size < 2) return 1.0
        
        val first = memoryHistory.first().usedMemoryBytes.toDouble()
        val last = memoryHistory.last().usedMemoryBytes.toDouble()
        
        return if (first > 0) last / first else 1.0
    }
    
    /**
     * 设置GC监控
     */
    private fun setupGCMonitoring() {
        // 初始化GC计数
        lastGcCount.set(getCurrentGCCount())
        
        // 定期检查GC频率
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                
                val currentGcCount = getCurrentGCCount()
                val gcDelta = currentGcCount - lastGcCount.get()
                lastGcCount.set(currentGcCount)
                
                if (gcDelta > 0) {
                    gcEventCount += gcDelta.toInt()
                }
                
                handler.postDelayed(this, 60000) // 每分钟检查一次
            }
        }, 60000)
    }
    
    /**
     * 获取GC计数
     */
    private fun getCurrentGCCount(): Long {
        return try {
            // 通过Debug接口获取GC统计
            Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 获取上次采样后的GC次数
     */
    private fun getGCCountSinceLastSample(): Int {
        val currentCount = gcEventCount
        gcEventCount = 0 // 重置计数
        return currentCount
    }
    
    /**
     * 上报基础内存指标
     */
    private fun reportBasicMemoryMetrics(snapshot: MemorySnapshot) {
        val metadata = mapOf(
            "used_memory_mb" to (snapshot.usedMemoryBytes / 1024 / 1024).toString(),
            "max_memory_mb" to (snapshot.maxMemoryBytes / 1024 / 1024).toString(),
            "usage_percent" to snapshot.memoryUsagePercent.toString(),
            "gc_count" to snapshot.gcCount.toString(),
            "activity_count" to snapshot.activityCount.toString(),
            "fragment_count" to snapshot.fragmentCount.toString()
        )
        
        performanceMonitor.recordMetric(
            PerformanceMetric.memory(
                name = "memory_usage",
                value = snapshot.memoryUsagePercent,
                metadata = metadata
            )
        )
    }
    
    /**
     * 上报内存泄漏嫌疑
     */
    private fun reportMemoryLeakSuspicion(
        current: MemorySnapshot,
        history: List<MemorySnapshot>
    ) {
        val growthRate = calculateLinearTrend(history.map { it.usedMemoryBytes })
        val metadata = mapOf(
            "current_usage_percent" to current.memoryUsagePercent.toString(),
            "growth_rate" to growthRate.toString(),
            "history_size" to history.size.toString(),
            "suspected_reason" to "memory_consistently_growing"
        )
        
        performanceMonitor.recordMetric(
            PerformanceMetric.error(
                type = MetricType.MEMORY,
                name = "memory_leak_suspicion",
                value = current.memoryUsagePercent,
                metadata = metadata
            )
        )
        
        logDebug("检测到内存泄漏嫌疑，使用率: ${current.memoryUsagePercent}%")
    }
    
    /**
     * 上报内存抖动
     */
    private fun reportMemoryChurn(snapshot: MemorySnapshot) {
        performanceMonitor.recordMetric(
            PerformanceMetric.error(
                type = MetricType.MEMORY,
                name = "memory_churn_high",
                value = snapshot.gcCount.toDouble(),
                metadata = mapOf(
                    "gc_count" to snapshot.gcCount.toString(),
                    "threshold" to config.gcWatchThreshold.toString()
                )
            )
        )
        
        logDebug("检测到内存抖动，GC次数: ${snapshot.gcCount}")
    }
    
    /**
     * 上报堆异常增长
     */
    private fun reportHeapGrowthAnomaly(snapshot: MemorySnapshot, growth: Double) {
        performanceMonitor.recordMetric(
            PerformanceMetric.error(
                type = MetricType.MEMORY,
                name = "heap_growth_anomaly",
                value = growth,
                metadata = mapOf(
                    "growth_factor" to growth.toString(),
                    "threshold" to config.heapGrowthThreshold.toString(),
                    "current_usage_mb" to (snapshot.usedMemoryBytes / 1024 / 1024).toString()
                )
            )
        )
        
        logDebug("检测到堆异常增长，增长倍数: $growth")
    }
    
    /**
     * 获取活跃Activity数量（简化实现）
     */
    private fun getActiveActivityCount(): Int {
        // 实际实现应通过ActivityLifecycleCallbacks跟踪
        // 这里返回简化值
        return 1
    }
    
    /**
     * 获取活跃Fragment数量（简化实现）
     */
    private fun getActiveFragmentCount(): Int {
        // 实际实现应通过FragmentLifecycleCallbacks跟踪  
        // 这里返回简化值
        return 0
    }
    
    private fun logDebug(message: String) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE_MEMORY, "MemoryPerformanceMonitor: $message")
        }
    }

    private fun logError(message: String, exception: Exception? = null) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            if (exception != null) {
                LogConfig.e(LogConfig.TAG_PERFORMANCE_MEMORY, "MemoryPerformanceMonitor: $message", exception)
            } else {
                LogConfig.e(LogConfig.TAG_PERFORMANCE_MEMORY, "MemoryPerformanceMonitor: $message")
            }
        }
    }
}
fun <T> List<T>.takeLastCompat(count: Int): List<T> {
    return if (size <= count) {
        this
    } else {
        subList(size - count, size)
    }
}
/**
 * 内存快照数据类
 */
data class MemorySnapshot(
    val timestamp: Long,
    val usedMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val memoryUsagePercent: Double,
    val gcCount: Int,
    val activityCount: Int,
    val fragmentCount: Int
)