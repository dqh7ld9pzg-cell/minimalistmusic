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

package com.minimalistmusic.data.repository

import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.data.remote.NeteaseSearchApiService
import com.minimalistmusic.domain.model.PagedData
import com.minimalistmusic.domain.model.SearchResult
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.SearchRepository
import com.minimalistmusic.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
/**
 * 统一搜索仓库
 * 使用网易云音乐官方API实现搜索功能
 *
 * 重构说明（2025-11-16）:
 * - 使用 NeteaseSearchApiService（网易云音乐官方API）
 * - 基于开源项目 https://github.com/Binaryify/NeteaseCloudMusicApi
 * - 可靠、稳定、安全的音乐搜索服务
 * - 支持歌曲、艺术家搜索
 */
@Singleton
class NeteaseSearchRepositoryImpl @Inject constructor(
    private val neteaseSearchApiService: NeteaseSearchApiService
) : SearchRepository {
    /**
     * 搜索歌曲（使用网易云音乐API）
     *
     * 实现说明（2025-11-17）:
     * - 使用 NeteaseSearchApiService.search()
     * - API: POST https://music.163.com/api/search/get
     * - 参数: s=关键词, type=1(单曲), limit, offset
     * - 利用API返回的songCount字段准确判断是否还有更多数据
     *
     * @param keyword 搜索关键词
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 包含分页信息的搜索结果
     */
    private suspend fun searchSongsFromNetease(
        keyword: String,
        limit: Int = 20,
        offset: Int = 0
    ): Result<PagedData<Song>> {
        return try {
            val response = neteaseSearchApiService.search(
                keyword = keyword,
                type = 1, // 1 = 单曲
                limit = limit,
                offset = offset
            )
            if (response.code != 200 || response.resultDTO == null) {
                return Result.Error(Exception("搜索失败：code=${response.code}"))
            }
            val songs = response.resultDTO.songs?.mapNotNull { songDTO ->
                try {
                    // 构建封面图片URL
                    // 修复 (2025-11-17): 使用injahow API代理获取封面图片
                    val albumArt = songDTO.album.picUrl ?: run {
                        // 如果picUrl为空，使用picId通过injahow API获取
                        val picId = songDTO.album.picId
                        if (picId != null && picId != 0L) {
                            "https://api.injahow.cn/meting/?server=netease&type=pic&id=$picId"
                        } else {
                            ""
                        }
                    }
                    Song(
                        id = songDTO.id,
                        title = songDTO.name,
                        artist = songDTO.artists.joinToString("/") { it.name },
                        album = songDTO.album.name,
                        duration = songDTO.duration.coerceAtLeast(0),
                        // 播放URL需要单独获取，这里设置为null（修复 2025-11-21）
                        // 之前用空字符串""会导致ExoPlayer报ERROR_CODE_IO_FILE_NOT_FOUND错误
                        path = null,
                        albumArt = albumArt,
                        isLocal = false,
                        isFavorite = false
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            // 优先使用API返回的hasMore字段，如果没有则根据songCount计算
            val totalCount = response.resultDTO.songCount ?: 0
            val hasMore = response.resultDTO.hasMore ?: ((offset + songs.size) < totalCount)
            if (songs.isNotEmpty()) {
                Result.Success(
                    PagedData(
                        songsList = songs,
                        hasMore = hasMore,
                        total = totalCount
                    )
                )
            } else {
                Result.Error(Exception("未找到相关歌曲"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    /**
     * 搜索歌手
     *
     * 实现说明（2025-11-17）:
     * - 使用网易云音乐API搜索艺术家
     * - API: POST https://music.163.com/api/search/get
     * - type=100 表示搜索歌手
     */
    override suspend fun searchArtists(keyword: String): Result<List<SearchResult.ArtistResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = neteaseSearchApiService.search(
                    keyword = keyword,
                    type = 100, // 100 = 歌手
                    limit = 20,
                    offset = 0
                )
                if (response.code != 200 || response.resultDTO == null) {
                    return@withContext Result.Error(Exception("搜索失败：code=${response.code}"))
                }
                val artists = response.resultDTO.artistsDTO?.mapNotNull { artistDTO ->
                    try {
                        // 数据验证（2025-11-21）：确保必需字段有效
                        if (artistDTO.id == null || artistDTO.id == 0L) {
                            LogConfig.w(LogConfig.TAG_PLAYER_DATA_REMOTE, "UnifiedSearchRepositoryImpl searchArtists 跳过无效歌手：ID为null或0")
                            return@mapNotNull null
                        }
                        if (artistDTO.name.isNullOrBlank()) {
                            LogConfig.w(LogConfig.TAG_PLAYER_DATA_REMOTE, "UnifiedSearchRepositoryImpl searchArtists 跳过无效歌手：name为空，id=${artistDTO.id}")
                            return@mapNotNull null
                        }
                        // 记录成功解析的歌手（仅在调试模式）
                        LogConfig.d(LogConfig.TAG_PLAYER_DATA_REMOTE, "UnifiedSearchRepositoryImpl searchArtists 解析歌手成功：id=${artistDTO.id}, name=${artistDTO.name}")
                        SearchResult.ArtistResult(
                            id = artistDTO.id,
                            name = artistDTO.name,
                            avatar = artistDTO.picUrl ?: "",  // 确保非null
                            songCount = artistDTO.albumSize ?: 0,
                            albumSize = artistDTO.albumSize ?: 0,
                            musicSize = artistDTO.mvSize ?: 0,
                            alias = artistDTO.alias ?: artistDTO.alia ?: emptyList()
                        )
                    } catch (e: Exception) {
                        LogConfig.e(LogConfig.TAG_PLAYER_DATA_REMOTE, "UnifiedSearchRepositoryImpl searchArtists 解析歌手数据失败：${e.message}")
                        null
                    }
                } ?: emptyList()
                // 记录最终结果
                LogConfig.d(LogConfig.TAG_PLAYER_DATA_REMOTE, "UnifiedSearchRepositoryImpl searchArtists 共解析 ${artists.size} 位歌手")
                if (artists.isNotEmpty()) {
                    Result.Success(artists)
                } else {
                    Result.Error(Exception("未找到相关歌手"))
                }
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
    /**
     * 分页搜索歌曲（2025-11-17）
     *
     * @param keyword 搜索关键词
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 包含分页信息的搜索结果
     */
    override suspend fun searchSongsPaged(
        keyword: String,
        limit: Int,
        offset: Int
    ): Result<PagedData<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                searchSongsFromNetease(keyword, limit, offset)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
    /**
     * 分页获取歌手的歌曲（2025-11-17）
     *
     * @param artistName 歌手名称
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 包含分页信息的歌曲列表
     */
    override suspend fun searchArtistSongsPaged(
        artistName: String,
        limit: Int,
        offset: Int
    ): Result<PagedData<Song>> {
        return withContext(Dispatchers.IO) {
            try {
                searchSongsFromNetease(artistName, limit, offset)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
}
