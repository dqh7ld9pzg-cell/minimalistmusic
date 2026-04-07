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

import com.minimalistmusic.domain.model.PagedData
import com.minimalistmusic.domain.model.SearchResult
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.util.Result
/**
 * 为所有搜索相关的操作定义的统一领域层接口.
 *
 * 2025-11-17 更新：
 * - 添加分页搜索支持，使用PagedData返回类型包含hasMore信息
 */
interface SearchRepository {
    /**
     * 搜索歌手
     *
     * @param keyword 搜索关键词
     *
     * @return 歌手列表(默认返回最多20条歌手数据)
     */
    suspend fun searchArtists(keyword: String): Result<List<SearchResult.ArtistResult>>
    // --- 分页搜索 (2025-11-17) ---
    /**
     * 分页搜索歌曲
     *
     * @param keyword 搜索关键词
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 包含分页信息的搜索结果
     */
    suspend fun searchSongsPaged(keyword: String, limit: Int, offset: Int): Result<PagedData<Song>>
    /**
     * 分页获取歌手的歌曲
     *
     * @param artistName 歌手名称
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 包含分页信息的歌曲列表
     */
    suspend fun searchArtistSongsPaged(artistName: String, limit: Int, offset: Int): Result<PagedData<Song>>
}