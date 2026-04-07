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
import android.content.SharedPreferences
import com.minimalistmusic.performance.metric.PerformanceMetric
import javax.inject.Inject
import javax.inject.Singleton
/**
 * 性能数据存储管理器
 * 
 * 存储策略：
 * - 本地缓存：网络不可用时保存数据
 * - 自动清理：防止存储空间无限增长
 * - 数据备份：重要指标持久化存储
 * - 内存缓存：高频访问数据内存缓存
 */
@Singleton
class PerformanceStorage @Inject constructor(
    private val context: Context
) {
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("performance_metrics", Context.MODE_PRIVATE)
    }
    
    // 内存缓存，提高读取性能
    private val memoryCache = mutableListOf<PerformanceMetric>()
    private val maxMemoryCacheSize = 100
    
    /**
     * 存储性能指标
     */
    fun storeMetrics(metrics: List<PerformanceMetric>) {
        if (metrics.isEmpty()) return
        
        synchronized(this) {
            // 添加到内存缓存
            memoryCache.addAll(metrics)
            
            // 限制内存缓存大小
            if (memoryCache.size > maxMemoryCacheSize) {
                memoryCache.subList(0, memoryCache.size - maxMemoryCacheSize).clear()
            }
            
            // 持久化到SharedPreferences
            val existingMetrics = loadMetricsFromStorage()
            val allMetrics = existingMetrics + metrics
            
            // 限制存储的指标数量，防止无限增长
            val metricsToStore = allMetrics.takeLast(1000)
            
            saveMetricsToStorage(metricsToStore)
        }
    }
    
    /**
     * 加载所有性能指标
     */
    fun loadMetrics(): List<PerformanceMetric> {
        synchronized(this) {
            return if (memoryCache.isNotEmpty()) {
                memoryCache.toList()
            } else {
                loadMetricsFromStorage()
            }
        }
    }
    
    /**
     * 移除指定的性能指标
     */
    fun removeMetrics(metrics: List<PerformanceMetric>) {
        synchronized(this) {
            // 从内存缓存移除
            val metricIds = metrics.map { it.id }.toSet()
            memoryCache.removeAll { it.id in metricIds }
            
            // 从持久化存储移除
            val existingMetrics = loadMetricsFromStorage()
            val remainingMetrics = existingMetrics.filter { it.id !in metricIds }
            saveMetricsToStorage(remainingMetrics)
        }
    }
    
    /**
     * 清空所有性能指标
     */
    fun clearAllMetrics() {
        synchronized(this) {
            memoryCache.clear()
            sharedPreferences.edit().remove("cached_metrics").apply()
        }
    }
    
    /**
     * 获取存储的指标数量
     */
    fun getStoredMetricsCount(): Int {
        return synchronized(this) {
            memoryCache.size + loadMetricsFromStorage().size
        }
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 从持久化存储加载指标
     */
    private fun loadMetricsFromStorage(): List<PerformanceMetric> {
        return try {
            val metricsJson = sharedPreferences.getString("cached_metrics", null) ?: return emptyList()
            
            // 简化实现，实际应该使用Gson等反序列化
            // 这里返回空列表，实际项目需要实现完整的序列化
            emptyList()
            
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 保存指标到持久化存储
     */
    private fun saveMetricsToStorage(metrics: List<PerformanceMetric>) {
        try {
            // 简化实现，实际应该使用Gson等序列化
            val metricsJson = "[]" // 占位实现
            
            sharedPreferences.edit()
                .putString("cached_metrics", metricsJson)
                .apply()
                
        } catch (e: Exception) {
            // 存储失败不影响主流程
        }
    }
}