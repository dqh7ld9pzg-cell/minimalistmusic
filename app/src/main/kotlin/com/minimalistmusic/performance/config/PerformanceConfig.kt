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

package com.minimalistmusic.performance.config

import com.minimalistmusic.performance.metric.MetricType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
/**
 * 性能监控配置管理
 * 
 * 设计原则：
 * - 灵活的配置系统，支持动态调整
 * - 分级采样控制，平衡性能和数据量
 * - 关键阈值配置，及时发现问题
 * - 模块化开关，按需启用监控
 * 
 * 实际应用：
 * - 生产环境降低采样率，减少性能影响
 * - 开发环境开启详细监控，便于调试
 * - A/B测试不同配置的效果
 */
data class PerformanceConfig(
    // 基础配置
    val sampleRate: Double = 0.1,                    // 采样率 10%
    val batchSize: Int = 50,                         // 批量上报大小
    val flushInterval: Duration = 30.seconds, // 上报间隔
    val enabledMetrics: Set<MetricType> = setOf(     // 启用监控类型
        MetricType.NETWORK,
        MetricType.STARTUP, 
        MetricType.MEMORY,
        MetricType.UI_RENDER
    ),
    
    // 网络监控配置
    val networkConfig: NetworkConfig = NetworkConfig(),
    
    // 内存监控配置
    val memoryConfig: MemoryConfig = MemoryConfig(),
    
    // 启动监控配置
    val startupConfig: StartupConfig = StartupConfig(),
    
    // UI监控配置
    val uiConfig: UIConfig = UIConfig()
) {
    companion object {
        /**
         * 生产环境配置 - 低采样，关键监控
         */
        fun production(): PerformanceConfig = PerformanceConfig(
            sampleRate = 0.05,  // 5%采样率
            enabledMetrics = setOf(
                MetricType.NETWORK,
                MetricType.STARTUP,
                MetricType.MEMORY,
                MetricType.ERROR
            )
        )
        
        /**
         * 开发环境配置 - 高采样，详细监控
         */
        fun development(): PerformanceConfig = PerformanceConfig(
            sampleRate = 1.0,   // 100%采样率
            enabledMetrics = MetricType.values().toSet()
        )
    }
}
/**
 * 网络监控配置
 * 
 * 监控目标：
 * - 接口成功率：确保核心功能可用
 * - 请求耗时：优化用户体验
 * - 流量消耗：控制用户成本
 * - 错误分析：快速定位问题
 */
data class NetworkConfig(
    val slowRequestThresholdMs: Long = 3000,         // 慢请求阈值
    val timeoutThresholdMs: Long = 30000,            // 超时阈值
    val successRateThreshold: Double = 0.95,         // 成功率阈值
    val monitorRetry: Boolean = true,                // 监控重试机制
    val trackRedirects: Boolean = true               // 跟踪重定向
)
/**
 * 内存监控配置
 * 
 * 监控目标：
 * - 内存泄漏检测：防止OOM崩溃
 * - 内存抖动监控：避免GC卡顿
 * - 大对象追踪：优化内存分配
 * - 堆趋势分析：预测内存风险
 */
data class MemoryConfig(
    val samplingInterval: Duration = 30.seconds, // 采样间隔
    val gcWatchThreshold: Int = 3,                    // GC频率阈值
    val memoryLeakThreshold: Double = 0.85,          // 内存泄漏阈值
    val heapGrowthThreshold: Double = 1.5,           // 堆增长阈值
    val largeObjectThreshold: Long = 2 * 1024 * 1024 // 大对象阈值(2MB)
)
/**
 * 启动监控配置
 * 
 * 监控目标：
 * - 冷启动时间：应用首次启动体验
 * - 温启动时间：后台唤醒速度
 * - 首屏渲染：用户可交互时间
 * - 关键路径：启动阶段性能瓶颈
 */
data class StartupConfig(
    val coldStartThresholdMs: Long = 5000,           // 冷启动阈值
    val warmStartThresholdMs: Long = 2000,           // 温启动阈值
    val firstPaintThresholdMs: Long = 1000,          // 首屏渲染阈值
    val trackStartupPhases: Boolean = true           // 跟踪启动阶段
)
/**
 * UI监控配置
 * 
 * 监控目标：
 * - 帧率稳定性：保证流畅体验
 * - 渲染耗时：优化布局性能
 * - 卡顿检测：及时发现问题
 * - 过度绘制：减少GPU负载
 */
data class UIConfig(
    val jankThresholdMs: Double = 16.67,             // 卡顿阈值
    val lowFpsThreshold: Double = 50.0,              // 低帧率阈值
    val highJankRateThreshold: Double = 5.0,         // 高卡顿率阈值
    val monitorLayoutComplexity: Boolean = true      // 监控布局复杂度
)