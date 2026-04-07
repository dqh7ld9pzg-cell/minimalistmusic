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

package com.minimalistmusic.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
// 极简配色方案
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6650a4), // 紫色
    onPrimary = Color.White,
    secondary = Color(0xFF666666),
    tertiary = Color(0xFF9575CD), // 淡紫色（Purple 300）- 用于缓存进度等次要强调元素
    onTertiary = Color.White,
    secondaryContainer = Color.Transparent, // 导航指示器透明
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5)
)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), // 紫色（深色模式）
    onPrimary = Color.Black,
    secondary = Color(0xFF999999),
    tertiary = Color(0xFFB39DDB), // 淡紫色（Purple 200）- 用于缓存进度等次要强调元素
    onTertiary = Color.Black,
    secondaryContainer = Color.Transparent, // 导航指示器透明
    background = Color.Black,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A)
)
@Composable
fun MinimalistMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
