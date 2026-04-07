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

package com.minimalistmusic.presentation.viewmodel

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.domain.cache.CacheStateManager
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.presentation.error.GlobalErrorChannel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
/**
 * ViewModel的基类，封装了统一的异常处理、协程启动和缓存状态管理
 *
 * 架构增强 (2025-11-14):
 * - 新增缓存状态管理支持
 * - 提供统一的isSongCached()方法供所有子类使用
 * - 避免每个ViewModel都重复实现缓存状态查询逻辑
 *
 * 错误提示功能 (2025-11-15):
 * - 新增统一的错误消息提示机制
 * - 使用SharedFlow发送一次性错误事件
 * - UI层可以收集errorMessage并显示Toast/Snackbar
 *
 * 注意：
 * - 此类不添加@HiltViewModel注解（抽象类不需要）
 * - 抽象类的构造函数不能使用@Inject注解（Dagger/Hilt限制）
 * - Application和CacheStateManager由子类通过@Inject构造函数注入，然后传递给BaseViewModel
 * - 继承AndroidViewModel以支持需要Application的子类（如HomeViewModel、PlayerViewModel）
 */
@OptIn(UnstableApi::class)
abstract class BaseViewModel
    (
    application: Application, // 新增：支持需要Application的子类
    protected val cacheStateManager: CacheStateManager, // 修复 (2025-11-15): 改为protected，允许子类访问
) : AndroidViewModel(application) {
    /**
     * 架构重构：错误消息已迁移到GlobalErrorChannel
     *
     * 旧方案问题：
     * - 每个ViewModel独立的errorMessage Flow
     * - UI层需要监听多个ViewModel的错误
     * - 多个错误同时出现时Toast重叠，用户体验差
     * - 每个Screen重复监听代码，违反DRY原则
     *
     * 新方案（GlobalErrorChannel）：
     * - 全局统一的错误通道，自动去重和排队
     * - ViewModel只需调用showError()，无需暴露errorMessage
     * - UI层在应用根部统一监听（GlobalErrorHandler）
     * - 所有Screen自动支持错误提示，零额外代码
     *
     * 迁移指南：
     * - ViewModel层：无需改动，继续使用showError()即可
     * - UI层：移除所有LaunchedEffect { errorMessage.collect } 代码
     * - 应用根部：添加GlobalErrorHandler()组件（一次配置）
     */
    // 1. 创建一个 CoroutineExceptionHandler
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // 当任何在 safeLaunch 中未被捕获的异常发生时，这里会被调用
        handleException(throwable)
    }
    /**
     * 安全地启动一个协程，自动处理异常
     * @param block 要在协程中执行的代码块
     */
    protected fun safeLaunch(block: suspend CoroutineScope.() -> Unit) {
        // 2. 将 exceptionHandler 添加到协程作用域的上下文中
        //viewModelScope.launch(exceptionHandler, block = block)
        viewModelScope.launch(exceptionHandler){
            try {
                block()
            } catch (thr: Throwable) {
                handleException(thr)
            }
        }
    }
    /**
     * 子类可以重写此方法来处理特定于ViewModel的异常
     * 例如，更新一个 _errorMessage StateFlow
     * @param throwable 捕获到的异常
     */
    protected open fun handleException(throwable: Throwable) {
        // 默认实现，可以在这里进行全局的日志记录
        LogConfig.e(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "BaseViewModel handleException : ${throwable.message}"
        )
        // 默认显示异常消息
        showError(throwable.message ?: "发生未知错误")
    }
    /**
     * 显示错误消息
     *
     * 统一的错误提示入口，所有ViewModel都可以调用此方法显示错误。
     *
     * 架构重构：
     * - 错误消息发送到GlobalErrorChannel（全局单例）
     * - 自动去重：3秒内相同错误只显示一次
     * - 自动排队：多个错误按顺序显示，不会重叠
     * - UI层无需监听：在应用根部统一处理（GlobalErrorHandler）
     *
     * 使用示例：
     * ```kotlin
     * if (result is Result.Error) {
     *     showError("网络请求失败: ${result.exception.message}")
     * }
     * ```
     *
     * @param message 错误消息文本
     */
    protected fun showError(message: String) {
        viewModelScope.launch {
            GlobalErrorChannel.emit(message)
        }
    }
    /**
     * 检查歌曲是否已缓存 (2025-11-14)
     *
     * 统一的缓存状态查询入口，所有ViewModel都可以调用此方法。
     *
     * 查询策略：
     * - 本地歌曲：直接返回true（无需缓存）
     * - 在线歌曲：从CacheStateManager查询状态
     *   - 如果已有缓存状态，立即返回
     *   - 如果状态未知，触发异步查询并返回false（StateFlow更新后UI会自动刷新）
     *
     * @param song 要检查的歌曲
     * @return true表示已缓存（或为本地歌曲），false表示未缓存或状态未知
     */
    fun isSongCached(song: Song): Boolean {
        return cacheStateManager.isSongCached(song.id, song.isLocal)
    }
    /**
     * 重构说明 (2025-12-03): updateCacheState() 方法已移除
     *
     * 移除理由：
     * - 依赖的 CacheStateManager.setCacheState() 已被移除
     * - 违反 Single Source of Truth 原则
     * - 目前代码中无任何调用，移除不影响功能
     *
     * 替代方案：
     * - 删除缓存：在 Repository 层调用数据库删除操作
     *   musicDatabase.cachedSongDao().deleteCachedSong(songId)
     *   → Room Flow 会自动更新 cacheStateMap → UI 自动响应
     *
     * - 查询状态：直接使用 isSongCached(song)
     *   → 从 cacheStateMap 读取，数据库是唯一真实来源
     *
     * 架构优势：
     * - 真正的 Single Source of Truth
     * - 状态同步自动化，无需手动干预
     * - 避免状态不一致问题
     */
    /**
     * 获取缓存状态StateFlow（供UI层收集）
     *
     * UI可以通过collectAsState()响应缓存状态变化：
     * ```kotlin
     * val cacheStates by viewModel.cacheStateMap.collectAsState()
     * val isCached = cacheStates[song.id] ?: false
     * ```
     */
    open val cacheStateMap = cacheStateManager.cacheStateMap
    /**
     * 获取缓存进度StateFlow（供UI层收集）(2025-12-04新增)
     *
     * UI可以通过collectAsState()响应缓存进度变化：
     * ```kotlin
     * val cacheProgressMap by viewModel.cacheProgressMap.collectAsState()
     * val progress = cacheProgressMap[song.id]  // CacheProgress?
     * ```
     *
     * 架构说明：
     * - 数据来源：TransferListener实时监听网络下载
     * - 更新频率：100ms防抖，避免过度UI更新
     * - 用途：在播放器界面显示缓存进度条
     */
    open val cacheProgressMap = cacheStateManager.cacheProgressMap
}