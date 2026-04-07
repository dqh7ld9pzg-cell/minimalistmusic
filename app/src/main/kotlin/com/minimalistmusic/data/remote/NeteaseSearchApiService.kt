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

package com.minimalistmusic.data.remote

import com.minimalistmusic.data.remote.dto.NeteaseSearchResponseDTO
import com.minimalistmusic.data.remote.dto.NeteaseSongUrlResponseDTO
import retrofit2.http.*
/**
 * 网易云音乐API服务
 *
 * API地址: https://music.163.com/api/
 * 使用网易云音乐官方API接口
 *
 * 职责说明:
 * - 提供直接访问网易云音乐API的能力（高质量音源）
 * - 与 FreeMusicApiService 的区别:
 *   · FreeMusicApiService: 音乐聚合代理服务（api.injahow.cn），支持多平台
 *   · NeteaseSearchApiService: 网易云音乐直接API，提供搜索和音源URL
 *
 * 使用位置:
 * - FreeMusicRepositoryImpl.getSongPlayUrl() - 获取歌曲播放URL
 * - UnifiedSearchRepositoryImpl - 搜索歌曲和艺术家
 *
 * 重构说明:
 * - 2025-11-17: 修改为POST请求，使用网易云音乐官方search/get接口
 * - 2025-11-16: 添加 search() 方法，使用可靠的网易云音乐官方API实现搜索
 * - 2025-11-12: 迁移所有DTO到 com.minimalistmusic.data.remote.dto.NeteaseDTO.kt
 * - 原因: 统一DTO管理，遵循关注点分离原则
 */
interface NeteaseSearchApiService {
    /**
     * 搜索歌曲或艺术家
     *
     * API说明:
     * - 接口: POST https://music.163.com/api/search/get
     * - GET （之前GET请求会返回空结果以为必须用POST,实际上应该是HEADER问题)
     * - 需要表单编码的请求体
     *
     * @param keyword 搜索关键词（参数名为s）
     * @param type 搜索类型 (1=单曲, 100=歌手, 1000=歌单, 1002=用户, 1004=MV, 1006=歌词, 1009=电台, 1014=视频)
     * @param limit 返回数量限制
     * @param offset 偏移量，用于分页
     *
     * 使用位置:
     * - UnifiedSearchRepositoryImpl.searchSongs() - 搜索歌曲
     * - UnifiedSearchRepositoryImpl.searchArtists() - 搜索艺术家
     */
    //@FormUrlEncoded
    @GET("search/get")
    suspend fun search(
        @Query("s") keyword: String,
        @Query("type") type: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): NeteaseSearchResponseDTO
    /**
     * 获取歌曲播放URL
     * @param id 歌曲ID
     * @param br 比特率 (128000, 192000, 320000, 999000)
     *
     * 使用位置:
     * - FreeMusicRepositoryImpl.getSongPlayUrl() - 唯一调用点
     */
    @GET("song/url")
    suspend fun getSongUrl(
        @Query("id") id: Long,
        @Query("br") br: Int = 320000
    ): NeteaseSongUrlResponseDTO
    /**
     * 获取热门歌手/艺人
     * @param limit 返回数量限制
     * @param offset 偏移量，用于分页
     *
     * 用于发现页的Top 200歌手推荐功能
     */
    @GET("top/artists")
    suspend fun getTopArtists(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): com.minimalistmusic.data.remote.dto.TopArtistsData
    /**
     * 获取歌手热门歌曲(默认返回 50首热门歌曲)
     * @param id 歌手ID
     *
     * 用于歌手歌单详情页的初始歌曲列表
     */
    @GET("artist/top/song")
    suspend fun getArtistTopSongs(
        @Query("id") id: Long
    ): com.minimalistmusic.data.remote.dto.ArtistTopSongsData
}
// ============ DTO已迁移 ============
// 所有DTO已迁移至: com.minimalistmusic.data.remote.dto.NeteaseDTO.kt
// 迁移时间: 2025-11-12
// 原因: 遵循关注点分离原则,ApiService只定义接口,DTO独立管理
