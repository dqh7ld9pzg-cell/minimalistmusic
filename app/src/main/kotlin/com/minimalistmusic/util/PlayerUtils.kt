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

package com.minimalistmusic.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.text.intl.Locale

/**
 * 播放器工具函数集合
 */
/**
 * 格式化时间（毫秒 -> mm:ss）
 *
 * @param millis 毫秒数
 * @return 格式化后的时间字符串，如 "03:45"（固定两位数格式）
 */
fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(locale = java.util.Locale.CHINA, format = "%02d:%02d", minutes, secs)
}
/**
 * 震动反馈工具函数
 * 在进入拖动模式时触发，提供触觉反馈
 *
 * @param context Android Context
 * @param durationMillis 震动时长（毫秒），默认50ms
 */
fun performHapticFeedback(context: Context, durationMillis: Long = 50) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    if (vibrator?.hasVibrator() == true) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                durationMillis,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }
}
