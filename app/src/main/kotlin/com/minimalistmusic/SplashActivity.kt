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

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
/**
 * 自定义启动页Activity
 *
 * 设计理念 (2025-11-15):
 * - 添加欢迎文字："欢迎使用 极简音乐"
 * - 图标添加圆角，避免90度直角过于生硬
 * - 白色背景，简洁大方
 *
 * 启动优化 (2025-11-29) - 最佳实践方案:
 *
 * ## 设计决策
 * 经过测试,Android 12+ Splash Screen API 双层策略存在严重问题:
 *  系统 Splash 无法自定义圆角和文字
 *  系统 Splash → 自定义 Splash 切换时出现标题栏
 *  图标尺寸和位置与自定义 UI 不一致
 *  用户体验差,视觉不连贯
 *
 * 因此采用纯自定义方案:
 *  完全控制 UI 样式 (圆角、文字、布局)
 *  视觉体验一致
 *  接受 200-300ms 初始化延迟 (行业标准)
 *
 * ## 启动流程
 * ```
 * 0ms      用户点击图标
 *          ↓
 * 0-200ms  Application 初始化 + Compose 渲染
 *          (白色背景,无黑屏)
 *          ↓
 * 200ms    SplashActivity 显示 (Logo + 文字 + 圆角)
 *          ↓
 * 627ms    fade 过渡到 MainActivity
 * ```
 *
 * ## 实现细节
 * 1. windowBackground: 白色背景消除黑屏
 * 2. Compose UI: 自定义圆角 Logo + 欢迎文字
 * 3. Activity 过渡: fade_in/fade_out 平滑切换
 * 4. 无动画: 移除淡入动画,避免渲染延迟导致动画失效
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen()
        }
    }
    @OptIn(UnstableApi::class)
    @Composable
    private fun SplashScreen() {
        // 最佳实践 (2025-11-29): 纯自定义 Splash,完全控制样式
        // 策略:
        // 1. windowBackground 白色背景消除黑屏
        // 2. Compose UI 渲染 Logo(圆角) + 文字
        // 3. 停留 800ms 后跳转到 MainActivity
        // 4. Activity 切换使用 fade_in/fade_out 过渡
        //
        // 优势:
        // - 完全自定义 UI (圆角、文字、布局)
        // - 视觉体验一致
        // - 代码简洁,易于维护
        // - 符合 Material Design 规范
        LaunchedEffect(Unit) {
            delay(400)  // 增加停留时间,让用户看清欢迎信息
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            // 替换废弃的 overridePendingTransition (2025-11-29)
            // Android 13+ 使用新的 ActivityOptions API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeCustomAnimation(
                    this@SplashActivity,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            finish()
        }
        // Compose UI: 完整自定义设计
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),  // 白色背景
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo 图标 (带圆角)
                Image(
                    painter = painterResource(id = R.mipmap.ic_minimalist_logo),
                    contentDescription = "应用图标",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
                Spacer(modifier = Modifier.height(24.dp))
                // 欢迎文字
                Text(
                    text = "欢迎使用 极简音乐",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Copyright 信息
                Text(
                    text = "© 2025 极简科技 | 让音乐回归纯粹",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
