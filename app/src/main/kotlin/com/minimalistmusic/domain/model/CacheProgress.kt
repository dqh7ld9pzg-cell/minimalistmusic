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

package com.minimalistmusic.domain.model

/**
 * 缓存进度数据模型
 *
 * 架构重构：基于 TransferListener 的实时进度监听
 * - 用途：在 UI 层展示缓存进度条
 * - 数据源：TransferListener.onBytesTransferred 回调
 * - 更新频率：实时（可配置防抖间隔）
 *
 * @param songId 歌曲ID
 * @param url 缓存URL（base URL）
 * @param bytesDownloaded 已下载字节数
 * @param totalBytes 总字节数（-1表示未知）
 * @param progressPercent 进度百分比（0-100）
 * @param isComplete 是否完成
 */
data class CacheProgress(
    val songId: Long,
    val url: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val isComplete: Boolean = false
) {
    companion object {
        /**
         * 创建初始进度（0%）
         */
        fun initial(songId: Long, url: String, totalBytes: Long = -1L) = CacheProgress(
            songId = songId,
            url = url,
            bytesDownloaded = 0L,
            totalBytes = totalBytes,
            progressPercent = 0,
            isComplete = false
        )

        /**
         * 创建完成状态（100%）
         */
        fun completed(songId: Long, url: String, totalBytes: Long) = CacheProgress(
            songId = songId,
            url = url,
            bytesDownloaded = totalBytes,
            totalBytes = totalBytes,
            progressPercent = 100,
            isComplete = true
        )

        /**
         * 计算进度百分比
         */
        fun calculateProgress(downloaded: Long, total: Long): Int {
            return if (total > 0) {
                ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            } else {
                0
            }
        }
    }

    /**
     * 格式化显示（用于日志）
     */
    fun formatForLog(): String {
        return "CacheProgress(songId=$songId, $progressPercent%, " +
                "${bytesDownloaded.formatBytes()}/${totalBytes.formatBytes()})"
    }

    private fun Long.formatBytes(): String {
        return when {
            this < 0 -> "Unknown"
            this < 1024 -> "${this}B"
            this < 1024 * 1024 -> "${this / 1024}KB"
            else -> "${this / 1024 / 1024}MB"
        }
    }
}
