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

package com.minimalistmusic.presentation.error

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局错误消息通道
 *
 * 架构设计：
 * - Application级别单例，管理所有ViewModel的错误消息
 * - 自动去重：3秒内相同错误只发送一次，避免错误轰炸
 * - 自动排队：多个错误按顺序发送，不会重叠
 * - 零侵入：ViewModel只需调用emit()，UI层在根部统一监听
 *
 * 优势：
 * - ViewModel无需暴露errorMessage StateFlow
 * - Screen无需重复监听多个ViewModel
 * - 错误处理完全自动化，符合DRY原则
 *
 * 使用示例：
 * ```kotlin
 * // BaseViewModel.kt
 * protected fun showError(message: String) {
 *     viewModelScope.launch {
 *         GlobalErrorChannel.emit(message)
 *     }
 * }
 *
 * // App根部
 * @Composable
 * fun MinimalistMusicApp() {
 *     GlobalErrorHandler()  // 全局配置一次即可
 *     NavHost(...) { ... }
 * }
 * ```
 */
object GlobalErrorChannel {
    /**
     * 错误消息流
     *
     * 配置说明：
     * - replay=0：不保留历史消息（错误是一次性事件）
     * - extraBufferCapacity=10：缓冲10条消息，防止丢失
     * - onBufferOverflow=DROP_OLDEST：缓冲满时丢弃最旧消息
     */
    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * 只读错误流，供UI层订阅
     */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /**
     * 去重缓存
     *
     * 设计说明：
     * - Key: 错误消息内容
     * - Value: 上次发送时间戳
     * - 3秒内相同错误只发送一次
     */
    private val recentErrors = mutableMapOf<String, Long>()

    /**
     * 去重时间窗口（毫秒）
     */
    private const val THROTTLE_MS = 3000L

    /**
     * 发送错误消息
     *
     * 自动去重逻辑：
     * - 空白消息：直接忽略
     * - 3秒内重复消息：跳过
     * - 新消息或3秒后的重复消息：正常发送
     *
     * 自动清理：
     * - 每次发送时清理6秒前的缓存记录
     * - 避免内存泄漏
     *
     * @param message 错误消息内容
     */
    suspend fun emit(message: String) {
        // 忽略空白消息
        if (message.isBlank()) return

        val now = System.currentTimeMillis()
        val lastTime = recentErrors[message]

        // 去重检查：3秒内相同错误跳过
        if (lastTime != null && now - lastTime < THROTTLE_MS) {
            return
        }

        // 记录发送时间
        recentErrors[message] = now

        // 发送错误消息
        _errors.emit(message)

        // 清理过期缓存（6秒前的记录）
        recentErrors.entries.removeIf { now - it.value > THROTTLE_MS * 2 }
    }
}
