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

package com.minimalistmusic.presentation.viewmodel

import com.minimalistmusic.domain.model.PlaylistSource
import com.minimalistmusic.domain.model.RecommendPlaylist
import com.minimalistmusic.domain.model.SearchResult
import com.minimalistmusic.domain.model.Song

/**
 * 搜索页 UI 状态（UiState 模式）
 *
 * 架构优势：
 * 1. **状态集中管理**：所有 UI 状态集中在一个对象中
 * 2. **类型安全**：避免状态不一致（如 isSearching=true 但 results 为空）
 * 3. **易于测试**：单一状态对象，易于验证
 * 4. **简化 UI 订阅**：UI 只需订阅一个 StateFlow
 * 5. **不可变性**：data class 的 copy() 确保状态更新的不可变性
 *
 * 使用示例：
 * ```kotlin
 * // ViewModel 中
 * private val _uiState = MutableStateFlow(SearchUiState())
 * val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
 *
 * fun search(query: String) {
 *     _uiState.update { it.copy(searchQuery = query, isSearching = true) }
 *     // ... 执行搜索
 *     _uiState.update { it.copy(songResults = results, isSearching = false) }
 * }
 * ```
 *
 * @since 2025-12-03
 */
data class SearchUiState(
    // 搜索输入
    val searchQuery: String = "",

    // 搜索结果
    val songResults: List<Song> = emptyList(),
    val artistResults: List<SearchResult.ArtistResult> = emptyList(),

    // 加载状态
    val isSearching: Boolean = false,
    val isLoadingMoreSongs: Boolean = false,
    val isLoadingMoreArtistSongs: Boolean = false,

    // 当前 Tab
    val currentTab: SearchTab = SearchTab.SONGS,

    // 歌曲搜索分页
    val songsCount: Int = 0,
    val hasMoreSongs: Boolean = true,

    // 歌手详情分页
    val hasMoreArtistSongs: Boolean = true,

    // 详情页状态（统一管理歌手详情和歌单详情）
    val detailType: DetailType? = null,
    val selectedArtist: SearchResult.ArtistResult? = null,
    val playlistDetail: RecommendPlaylist? = null
) {
    /**
     * 是否在详情页模式
     */
    val isInDetailMode: Boolean
        get() = detailType != null

    /**
     * 是否在歌手详情页
     */
    val isInArtistDetail: Boolean
        get() = detailType == DetailType.ARTIST

    /**
     * 是否在歌单详情页
     */
    val isInPlaylistDetail: Boolean
        get() = detailType == DetailType.PLAYLIST

    /**
     * 当前详情页标题
     */
    val detailTitle: String
        get() = when (detailType) {
            DetailType.ARTIST -> selectedArtist?.name ?: ""
            DetailType.PLAYLIST -> playlistDetail?.name ?: ""
            null -> ""
        }

    /**
     * 当前详情页封面
     */
    val detailCover: String?
        get() = when (detailType) {
            DetailType.ARTIST -> selectedArtist?.avatar
            DetailType.PLAYLIST -> playlistDetail?.cover
            null -> null
        }

    /**
     * 是否支持加载更多
     */
    val canLoadMore: Boolean
        get() = when {
            isInArtistDetail -> hasMoreArtistSongs && !isLoadingMoreArtistSongs
            isInDetailMode && currentTab == SearchTab.SONGS -> hasMoreSongs && !isLoadingMoreSongs
            else -> false
        }
}
