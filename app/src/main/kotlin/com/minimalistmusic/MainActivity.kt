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

package com.minimalistmusic

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.local.MusicDatabase
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.performance.monitor.StartupPerformanceMonitor
import com.minimalistmusic.performance.monitor.UIPerformanceMonitor
import com.minimalistmusic.ui.MainScreen
import com.minimalistmusic.ui.theme.MinimalistMusicTheme
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase
    @Inject
    lateinit var musicLocalRepository: MusicLocalRepository
    @Inject
    lateinit var startupMonitor: StartupPerformanceMonitor
    @Inject
    lateinit var uiMonitor: UIPerformanceMonitor

    // 权限请求启动器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            LogConfig.d(LogConfig.TAG_PLAYER_UI,"MainActivity 请求权限全部被授予 ")
        } else {
            LogConfig.d(LogConfig.TAG_PLAYER_UI,"MainActivity 未全部授予请求的权限")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须先调用 super.onCreate() 完成依赖注入
        super.onCreate(savedInstanceState)
        // 启用沉浸式模式 (2025-11-21)
        // - 内容延伸到状态栏和导航栏区域
        // - 提供更大的显示空间和更好的视觉体验
        enableEdgeToEdge()
        setupSystemBars()
        // 记录Activity创建时间
        startupMonitor.onActivityCreate(this)
        // 监控关键启动阶段
        startupMonitor.startPhase("activity_create")
        // 请求必要权限
        requestPermissionsIfNeeded()
        setContent {
            MinimalistMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
        startupMonitor.endPhase("activity_create")
        // 启动UI性能监控
        uiMonitor.startMonitoring(this)
        // 设置内容绘制完成监听
        setupContentDrawListener()
        // 初始化界面
        initializeUI()
    }
    override fun onResume() {
        super.onResume()
        startupMonitor.onActivityResume(this)
    }
    override fun onDestroy() {
        super.onDestroy()
        uiMonitor.stopMonitoring(this)
    }
    /**
     * 设置系统栏样式 (2025-11-21)
     *
     * 沉浸式模式配置：
     * - 状态栏：透明背景，深色图标（适配浅色主题）
     * - 导航栏：透明背景，深色图标
     * - 兼容性：Android 6.0+ 支持浅色系统栏图标
     *
     * 参考：
     * - 网易云音乐：透明状态栏 + 深色图标
     * - QQ音乐：透明状态栏 + 深色图标
     */
    private fun setupSystemBars() {
        // WindowCompat 提供了兼容性更好的系统栏控制API
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 设置系统栏图标为深色（适配浅色主题）
        // Android 6.0+ 支持浅色状态栏（深色图标）
        // Android 8.0+ 支持浅色导航栏（深色图标）
        windowInsetsController.isAppearanceLightStatusBars = true  // 状态栏深色图标
        windowInsetsController.isAppearanceLightNavigationBars = true  // 导航栏深色图标（Android 8.0+）
        // 设置状态栏和导航栏背景为透明
        // enableEdgeToEdge() 已经处理了基本的透明设置
        // 这里确保兼容性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 支持强制手势导航栏对比色
            window.isNavigationBarContrastEnforced = false
        }
    }
    private fun requestPermissionsIfNeeded() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
        } else {
            // 权限已经授予，可以做一些事情
        }
    }
    private fun setupContentDrawListener() {
        // 监听首屏绘制完成
        window.decorView.viewTreeObserver.addOnPreDrawListener {
            startupMonitor.onFirstContentDraw()
            true
        }
    }
    private fun initializeUI() {
        startupMonitor.startPhase("ui_initialization")
        // 模拟UI初始化工作
        initializeViews()
        loadData()
        setupListeners()
        startupMonitor.endPhase("ui_initialization")
        startupMonitor.onBusinessDataLoaded()
    }
    private fun initializeViews() {
        // 初始化视图
    }
    private fun loadData() {
        // 加载数据
    }
    private fun setupListeners() {
        // 设置监听器
    }
}
