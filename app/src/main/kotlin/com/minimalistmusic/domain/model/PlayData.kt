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
 * 播放状态
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentSong: Song? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playMode: PlayMode = PlayMode.SEQUENCE,
    val playlist: List<Song> = emptyList(),
    val currentIndex: Int = -1
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration else 0f
}
/**
 * 播放模式
 */
enum class PlayMode {
    SEQUENCE,    // 顺序播放
    SHUFFLE,     // 随机播放
    REPEAT_ONE   // 单曲循环
}
