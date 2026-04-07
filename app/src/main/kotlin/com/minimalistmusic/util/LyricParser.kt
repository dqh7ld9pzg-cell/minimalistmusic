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

import java.io.File

/**
 * 歌词行数据
 */
data class LyricLine(
    val time: Long, // 毫秒
    val text: String
)
/**
 * LRC歌词解析器
 */
object LyricParser {
    /**
     * 解析LRC格式歌词
     * 格式: [00:12.50]歌词文本
     */
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) {
            return emptyList()
        }
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        lrcContent.lines().forEach { line ->
            val matchResult = regex.find(line.trim())
            if (matchResult != null) {
                val (minutes, seconds, milliseconds, text) = matchResult.destructured
                // 计算时间（毫秒）
                val time = minutes.toLong() * 60 * 1000 +
                        seconds.toLong() * 1000 +
                        // 处理2位或3位毫秒
                        if (milliseconds.length == 2) {
                            milliseconds.toLong() * 10
                        } else {
                            milliseconds.toLong()
                        }
                if (text.isNotBlank()) {
                    lines.add(LyricLine(time, text.trim()))
                }
            }
        }
        // 按时间排序
        return lines.sortedBy { it.time }
    }
    /**
     * 获取当前时间应该显示的歌词索引
     */
    fun getCurrentLyricIndex(lyrics: List<LyricLine>, currentPosition: Long): Int {
        if (lyrics.isEmpty()) return -1
        for (i in lyrics.indices.reversed()) {
            if (currentPosition >= lyrics[i].time) {
                return i
            }
        }
        return -1
    }
    /**
     * 从本地音频文件提取LRC歌词
     */
    fun extractLyricFromLocalFile(audioPath: String): String? {
        // 尝试查找同名.lrc文件
        val lrcPath = audioPath.substringBeforeLast(".") + ".lrc"
        return try {
            File(lrcPath).takeIf { it.exists() }?.readText()
        } catch (e: Exception) {
            null
        }
    }
}