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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
///**
// * 播放模式
// */
//enum class PlayMode {
//    SEQUENCE,    // 顺序播放
//    SHUFFLE,     // 随机播放
//    REPEAT_ONE   // 单曲循环
//}
//
///**
// * 播放状态
// */
//data class PlaybackState(
//    val isPlaying: Boolean = false,
//    val currentSong: Song? = null,
//    val currentPosition: Long = 0L,
//    val duration: Long = 0L,
//    val playMode: PlayMode = PlayMode.SEQUENCE,
//    val playlist: List<Song> = emptyList(),
//    val currentIndex: Int = -1
//) {
//    val progress: Float
//        get() = if (duration > 0) currentPosition.toFloat() / duration else 0f
//}
/**
 * 歌曲数据模型
 */
@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long, // 毫秒
    val path: String?, // 本地路径或在线URL
    val albumArt: String?, // 封面URL
    val isLocal: Boolean = true,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(), // 记录添加时间（入库时间）
    val favoritedAt: Long? = null // 收藏时间（2025-11-19新增）
) : Parcelable {
    fun getDurationFormatted(): String {
        // FIXME: 临时方案 - 部分API不返回歌曲时长，使用随机值替代
        //  待所有音乐源接口都能返回真实时长后，移除此逻辑
        val actualDuration = if (duration == 0L) {
            // 歌曲时长为0时，生成1-4分钟的随机时长（毫秒）
            val randomMinutes = (1..4).random()
            val randomSeconds = (0..59).random()
            (randomMinutes * 60 + randomSeconds) * 1000L
        } else {
            duration
        }
        val seconds = (actualDuration / 1000).toInt()
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }
}
/**
 * 播放列表模型
 */
@Parcelize
data class Playlist(
    val id: Long,
    val name: String,
    val cover: String?,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false // 是否为系统默认歌单
) : Parcelable
/**
 * 用户模型
 */
@Parcelize
data class User(
    val id: Long,
    val phone: String?,
    val nickname: String,
    val avatar: String?,
    val isGuest: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
/**
 * 搜索结果模型
 */
sealed class SearchResult {
    data class SongResult(val song: Song) : SearchResult()
    data class ArtistResult(
        val id: Long,
        val name: String,
        val avatar: String?,
        val songCount: Int,
        val albumSize: Int = 0,
        val musicSize: Int = 0,
        val alias: List<String> = emptyList()
    ) : SearchResult()
    data class AlbumResult(
        val id: Long,
        val name: String,
        val artist: String,
        val cover: String?,
        val songCount: Int
    ) : SearchResult()
    data class PlaylistResult(
        val id: Long,
        val name: String,
        val cover: String?,
        val creator: String,
        val songCount: Int
    ) : SearchResult()
}
/**
 * 登录请求
 */
data class LoginRequest(
    val phone: String,
    val code: String
)
/**
 * 登录响应
 */
data class LoginResponse(
    val token: String,
    val user: User
)
/**
 * 播放列表数据源类型
 */
enum class PlaylistSource {
    GENERATED_PLAYLIST,  // 生成的歌单（现有的歌单逻辑）
    ARTIST_PLAYLIST      // 歌手歌单（新增的歌手逻辑）
}
/**
 * 推荐歌单
 */
data class RecommendPlaylist(
    val id: Long,
    val name: String,
    val cover: String,
    val playCount: Long,
    val description: String? = "推荐歌单",
    val source: PlaylistSource = PlaylistSource.GENERATED_PLAYLIST,
    val artistId: Long? = null, // 可选字段，用于歌手歌单
    val songsCount: Long? = 0,
)