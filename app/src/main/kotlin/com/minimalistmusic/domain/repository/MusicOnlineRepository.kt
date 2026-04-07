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

package com.minimalistmusic.domain.repository

import com.minimalistmusic.domain.model.RecommendPlaylist
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.util.Result
/**
 * 在线音乐仓库接口
 *
 * 职责：
 * - 获取推荐歌单
 * - 获取歌单详情和歌曲列表
 * - 获取在线歌曲的播放URL
 * - 获取歌词
 *
 * 重构说明（2025-11-11）:
 * - 从 MusicRepository 中拆分出在线音乐服务相关操作
 * - 遵循单一职责原则：只处理在线音乐数据，不涉及本地存储
 * - 搜索功能已移至 SearchRepository
 *
 * 注意：此接口的实现依赖网络API
 */
interface MusicOnlineRepository {
    /**
     * 获取推荐歌单（混合推荐）
     * @return 推荐歌单列表，包含普通歌单和歌手歌单
     */
    suspend fun getRecommendPlaylists(): Result<List<RecommendPlaylist>>
    /**
     * 获取精选歌单列表
     * 实时获取歌手封面地址，确保封面图片有效
     * @return 精选歌单列表
     */
    suspend fun getFeaturedPlaylists(): Result<List<RecommendPlaylist>>
    /**
     * 获取热门歌手列表（Top 200）
     * @param offset 偏移量，用于分页获取
     * @param limit 每页数量
     * @return 热门歌手列表
     */
    suspend fun getTopArtists(offset: Int = 0, limit: Int = 50): Result<List<RecommendPlaylist>>
    /**
     * 获取歌单详情（歌曲列表）
     * @param playlistId 歌单ID
     * @param source 歌单来源类型（普通歌单或歌手歌单）
     * @return 歌单中的歌曲列表
     */
    suspend fun getPlaylistDetail(playlistId: String, source: String = "playlist"): Result<List<Song>>
    /**
     * 获取歌单详情（支持分页）
     * @param playlistId 歌单ID或歌手ID
     * @param pageSize 每页数量
     * @param offset 偏移量
     * @param source 歌单来源类型（普通歌单或歌手歌单）
     * @param artistName 歌手名称（当source为"artist"时必须提供）
     * @return 歌单中的歌曲列表
     */
    suspend fun getPlaylistDetail(playlistId: String, pageSize: Int, offset: Int, source: String = "playlist", artistName: String? = null): Result<List<Song>>
    /**
     * 获取歌手热门歌曲
     * @param artistId 歌手ID
     * @param limit 返回歌曲数量
     * @return 歌手热门歌曲列表
     */
    suspend fun getArtistTopSongs(artistId: Long): Result<List<Song>>
    /**
     * 获取在线歌曲的播放URL
     * @param songId 歌曲ID
     * @return 播放URL
     */
    suspend fun getSongPlayUrl(song: Song): Result<String>
    /**
     * 获取歌词
     * @param songId 歌曲ID
     * @return 歌词文本（LRC格式）
     */
    suspend fun getLyric(songId: Long): Result<String?>
}
