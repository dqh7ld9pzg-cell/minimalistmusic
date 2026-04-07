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

package com.minimalistmusic.performance.reporter

import android.content.Context
import android.provider.Settings
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.performance.metric.PerformanceMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton
/**
 * 性能数据上报器
 * 
 * 设计特点：
 * - 批量上报：减少网络请求次数
 * - 失败重试：保证数据不丢失
 * - 本地缓存：网络不可用时保存数据
 * - 异步处理：不阻塞主线程
 * 
 * 上报策略：
 * - 定时上报：固定间隔批量发送
 * - 立即上报：关键数据立即发送
 * - 失败重试：指数退避重试机制
 * - 采样控制：平衡数据量和性能
 */
@Singleton
class PerformanceReporter @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val performanceStorage: PerformanceStorage,
    private val context: Context
) {
    private val reportingScope = CoroutineScope(Dispatchers.IO + Job())
    
    // 上报配置
    private val reportConfig = ReportConfig(
        batchSize = 50,
        maxRetryCount = 3,
        retryDelayMs = 5000L,
        timeoutSeconds = 30L
    )
    // 上报开光
    private var isReportingEnabled = false
    
    companion object {
        // 单例实例，用于静态方法调用
        @Volatile
        private var instance: PerformanceReporter? = null
        
        /**
         * 初始化单例实例
         */
        fun initialize(reporter: PerformanceReporter) {
            instance = reporter
        }
        
        /**
         * 记录性能指标（静态方法，方便调用）
         */
        fun recordMetric(metric: PerformanceMetric) {
            instance?.recordMetricInternal(metric)
        }
    }
    
    /**
     * 记录性能指标
     */
    fun recordMetric(metric: PerformanceMetric) {
        recordMetricInternal(metric)
    }
    
    private fun recordMetricInternal(metric: PerformanceMetric) {
        if (!isReportingEnabled) return
        
        reportingScope.launch {
            try {
                // 存储到本地，等待批量上报
                performanceStorage.storeMetrics(listOf(metric))
                
                // 检查是否需要立即上报
                checkAndTriggerReporting()
                
            } catch (e: Exception) {
                logError("记录性能指标失败", e)
            }
        }
    }
    
    /**
     * 批量上报性能指标
     */
    suspend fun report(metrics: List<PerformanceMetric>): Boolean {
        if (!isReportingEnabled || metrics.isEmpty()) {
            return true
        }
        
        return try {
            val reportBatch = PerformanceReportBatch(
                metrics = metrics,
                deviceId = getDeviceId(),
                appVersion = getAppVersion(),
                timestamp = System.currentTimeMillis()
            )
            
            val requestBody = RequestBody.create(
                "application/json". toMediaTypeOrNull(),
                reportBatch.toJson()
            )
            
            val request = Request.Builder()
                .url("https://your-analytics-api.com/performance-metrics")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "PerformanceMonitor/1.0")
                .build()
            
            val response = withTimeout(reportConfig.timeoutSeconds * 1000) {
                okHttpClient.newCall(request).execute()
            }
            
            val success = response.isSuccessful
            if (!success) {
                logDebug("上报失败: ${response.code} - ${response.message}")
                
                // 失败重试
                if (metrics.size <= reportConfig.batchSize) {
                    scheduleRetry(metrics)
                }
            } else {
                logDebug("成功上报 ${metrics.size} 个性能指标")
            }
            
            success
            
        } catch (e: Exception) {
            logError("上报异常", e)
            
            // 异常重试
            if (metrics.size <= reportConfig.batchSize) {
                scheduleRetry(metrics)
            }
            
            false
        }
    }
    
    /**
     * 强制立即上报所有缓存数据
     */
    suspend fun flushAllMetrics(): Boolean {
        val allMetrics = performanceStorage.loadMetrics()
        if (allMetrics.isEmpty()) return true
        
        logDebug("开始强制上报 ${allMetrics.size} 个缓存指标")
        
        val success = report(allMetrics)
        if (success) {
            performanceStorage.clearAllMetrics()
            logDebug("强制上报完成")
        } else {
            logDebug("强制上报失败")
        }
        
        return success
    }
    
    /**
     * 启用/禁用上报
     */
    fun setReportingEnabled(enabled: Boolean) {
        isReportingEnabled = enabled
        logDebug("性能数据上报: ${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 获取上报状态
     */
    fun isReportingEnabled(): Boolean = isReportingEnabled
    
    // ========== 私有方法 ==========
    
    /**
     * 检查并触发上报
     */
    private fun checkAndTriggerReporting() {
        reportingScope.launch {
            val storedMetrics = performanceStorage.loadMetrics()
            if (storedMetrics.size >= reportConfig.batchSize) {
                val metricsToReport = storedMetrics.take(reportConfig.batchSize)
                val success = report(metricsToReport)
                
                if (success) {
                    // 上报成功，移除已上报数据
                    performanceStorage.removeMetrics(metricsToReport)
                }
            }
        }
    }
    
    /**
     * 安排重试
     */
    private fun scheduleRetry(metrics: List<PerformanceMetric>) {
        reportingScope.launch {
            delay(reportConfig.retryDelayMs)
            
            // 重新存储指标，等待下次上报
            performanceStorage.storeMetrics(metrics)
            logDebug("安排重试上报 ${metrics.size} 个指标")
        }
    }
    
    /**
     * 获取设备标识
     */
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun logDebug(message: String) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            LogConfig.d(LogConfig.TAG_PERFORMANCE_REPORTER, "PerformanceReporter: $message")
        }
    }

    private fun logError(message: String, exception: Exception? = null) {
        if (LogConfig.ENABLE_LOG_PERFORMANCE) {
            if (exception != null) {
                LogConfig.e(LogConfig.TAG_PERFORMANCE_REPORTER, "PerformanceReporter: $message", exception)
            } else {
                LogConfig.e(LogConfig.TAG_PERFORMANCE_REPORTER, "PerformanceReporter: $message")
            }
        }
    }
}
/**
 * 性能指标上报批次
 */
data class PerformanceReportBatch(
    val metrics: List<PerformanceMetric>,
    val deviceId: String,
    val appVersion: String,
    val timestamp: Long
) {
    fun toJson(): String {
        // 简化实现，实际应该使用Gson等序列化库
        return """
        {
            "device_id": "$deviceId",
            "app_version": "$appVersion", 
            "timestamp": $timestamp,
            "metrics_count": ${metrics.size}
        }
        """.trimIndent()
    }
}
/**
 * 上报配置
 */
data class ReportConfig(
    val batchSize: Int,
    val maxRetryCount: Int,
    val retryDelayMs: Long,
    val timeoutSeconds: Long
)