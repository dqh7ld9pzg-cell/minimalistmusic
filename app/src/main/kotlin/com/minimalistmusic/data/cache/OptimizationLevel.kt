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

package com.minimalistmusic.data.cache

/**
 * 缓存优化级别
 *
 * 用于控制缓存策略，支持不同网络环境下的优化配置
 *
 * 使用场景:
 * - 在设置中选择优化策略
 * - 根据网络类型自动切换策略
 * - A/B测试不同优化方案
 */
enum class OptimizationLevel(
    val displayName: String,
    val description: String
) {
    /**
     * 标准模式
     * - ExoPlayer默认配置
     * - 稳定可靠，适合所有网络环境
     * - 推荐用户：追求稳定性，或者移动网络流量有限的用户
     */
    DEFAULT(
        "标准模式",
        "稳定可靠，适合日常使用"
    ),

    /**
     * 高速模式
     * - 激进缓冲策略：10分钟缓冲区 + 100MB目标字节数
     * - 内存优化：64KB内存块，减少内存分配开销
     * - 磁盘IO优化：2MB写入缓冲区（减少400次→4次write调用）
     * - 预期效果：6分钟歌曲可在60秒内完整缓存（速度提升6倍）
     * - 推荐用户：WiFi环境下追求快速缓存的用户
     */
    OPTIMIZED(
        "高速模式",
        "快速缓存，WiFi环境推荐"
    );

    /**
     * 获取策略标签（用于UI展示）
     */
    fun getTags(): List<String> {
        return when (this) {
            DEFAULT -> listOf("稳定", "基准")
            OPTIMIZED -> listOf("高速缓存", "WiFi推荐")
        }
    }
}
