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

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * 全局错误处理器
 *
 * 架构设计：
 * - 应用级别组件，在NavHost根部调用一次即可
 * - 自动监听GlobalErrorChannel的所有错误消息
 * - 队列化显示：多个错误按顺序显示，不会重叠
 * - Toast显示：每条消息显示2.5秒，然后显示下一条
 *
 * 使用场景：
 * - 在应用的根Composable中调用一次
 * - 全应用所有Screen自动支持错误提示
 * - 无需在每个Screen重复添加监听代码
 *
 * 使用示例：
 * ```kotlin
 * @Composable
 * fun MinimalistMusicApp() {
 *     GlobalErrorHandler()  // ← 全局配置，只需一次
 *
 *     NavHost(...) {
 *         composable("search") { SearchScreen(...) }
 *         composable("home") { HomeScreen(...) }
 *         // 所有Screen自动支持错误提示
 *     }
 * }
 * ```
 *
 * 工作原理：
 * 1. LaunchedEffect(1) - 收集GlobalErrorChannel的错误消息
 * 2. 发送到本地Channel队列
 * 3. LaunchedEffect(2) - 从队列依次取出消息
 * 4. 显示Toast，等待2.5秒后显示下一条
 *
 * 优势：
 * - 错误按顺序显示，不会重叠或遮挡
 * - 用户体验友好
 * - 完全自动化，零维护成本
 */
@Composable
fun GlobalErrorHandler() {
    val context = LocalContext.current

    // 本地错误队列（无限容量）
    // 用于缓冲多个错误消息，确保按顺序显示
    val errorQueue = remember { Channel<String>(Channel.UNLIMITED) }

    // LaunchedEffect(1): 收集全局错误消息到队列
    LaunchedEffect(Unit) {
        GlobalErrorChannel.errors.collect { message ->
            errorQueue.send(message)
        }
    }

    // LaunchedEffect(2): 从队列依次显示错误
    LaunchedEffect(Unit) {
        for (message in errorQueue) {
            // 显示Toast
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

            // 等待Toast消失（LENGTH_SHORT约2秒 + 缓冲500ms）
            delay(2500)
        }
    }
}
