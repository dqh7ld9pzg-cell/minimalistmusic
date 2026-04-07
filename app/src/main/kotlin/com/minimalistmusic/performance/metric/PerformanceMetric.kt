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

package com.minimalistmusic.performance.metric

import java.util.UUID
/**
 * 性能指标数据模型
 * 
 * 设计目标：
 * - 统一所有监控指标的数据结构
 * - 支持灵活的元数据扩展
 * - 便于序列化和存储
 * - 包含完整的上下文信息
 * 
 * 实际应用：
 * - 网络请求耗时、成功率监控
 * - 内存使用趋势分析
 * - 启动时间优化追踪
 * - UI卡顿问题定位
 */
data class PerformanceMetric(
    val id: String = UUID.randomUUID().toString(),
    val type: MetricType,
    val name: String,
    val value: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * 创建网络性能指标
         * 用于监控API请求成功率、耗时、流量等
         */
        fun network(
            name: String,
            value: Double,
            metadata: Map<String, String> = emptyMap()
        ) = PerformanceMetric(
            type = MetricType.NETWORK,
            name = name,
            value = value,
            metadata = metadata
        )
        
        /**
         * 创建启动性能指标
         * 用于优化应用启动速度和首屏渲染时间
         */
        fun startup(
            name: String,
            value: Double,
            metadata: Map<String, String> = emptyMap()
        ) = PerformanceMetric(
            type = MetricType.STARTUP,
            name = name,
            value = value,
            metadata = metadata
        )
        
        /**
         * 创建内存性能指标
         * 用于检测内存泄漏、优化内存使用
         */
        fun memory(
            name: String,
            value: Double,
            metadata: Map<String, String> = emptyMap()
        ) = PerformanceMetric(
            type = MetricType.MEMORY,
            name = name,
            value = value,
            metadata = metadata
        )
        
        /**
         * 创建UI性能指标
         * 用于监控界面流畅度和渲染性能
         */
        fun ui(
            name: String,
            value: Double,
            metadata: Map<String, String> = emptyMap()
        ) = PerformanceMetric(
            type = MetricType.UI_RENDER,
            name = name,
            value = value,
            metadata = metadata
        )
        
        /**
         * 创建错误指标
         * 用于追踪性能问题和异常情况
         */
        fun error(
            type: MetricType,
            name: String,
            value: Double,
            metadata: Map<String, String> = emptyMap()
        ) = PerformanceMetric(
            type = MetricType.ERROR,
            name = name,
            value = value,
            metadata = metadata
        )
    }
}
/**
 * 性能指标类型枚举
 * 
 * 监控目的：
 * - NETWORK: 确保API可用性，优化用户体验
 * - STARTUP: 提升应用第一印象，减少用户流失
 * - MEMORY: 防止OOM崩溃，保证应用稳定性
 * - UI_RENDER: 保证交互流畅，提升用户满意度
 * - ERROR: 快速定位问题，降低故障影响
 */
enum class MetricType {
    NETWORK, STARTUP, MEMORY, UI_RENDER, DATABASE, ERROR
}