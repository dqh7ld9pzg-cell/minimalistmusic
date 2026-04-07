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

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.domain.cache.CacheStateManager
import com.minimalistmusic.domain.model.SearchResult
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.domain.repository.SearchRepository
import com.minimalistmusic.domain.repository.SearchHistoryRepository
import com.minimalistmusic.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
enum class SearchTab {
    SONGS,
    ARTISTS
}
/**
 * 详情页类型（重构 2025-11-27）
 * 统一管理歌单详情和歌手详情
 */
enum class DetailType {
    PLAYLIST,  // 普通歌单
    ARTIST     // 歌手详情
}
/**
 * SearchViewModel - 搜索页面
 *
 * 架构重构说明：
 * - 移除了 6 个 UseCase：GetSearchHistoryUseCase, AddSearchHistoryUseCase, ClearSearchHistoryUseCase,
 *   GetArtistSongsUseCase, SearchArtistsUseCase, SearchSongsUseCase
 * - 直接依赖 SearchRepository 和 SearchHistoryRepository，简化调用链
 * - 保持原有功能不变
 *
 * 缓存状态支持 (2025-11-14):
 * - 继承BaseViewModel，自动获得缓存状态管理能力
 * - 搜索结果列表可显示歌曲的缓存状态
 */
@HiltViewModel
class SearchViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    application: Application, // 修复 (2025-11-14): 注入Application传递给BaseViewModel
    private val searchRepository: SearchRepository, // 重构：直接依赖 Repository
    private val searchHistoryRepository: SearchHistoryRepository, // 重构：直接依赖 Repository
    private val onlineMusicRepository: MusicOnlineRepository, // 重构 (2025-11-27): 支持普通歌单加载
    cacheStateManager: CacheStateManager // 新增 (2025-11-14): 缓存状态管理
) : BaseViewModel(
    application,
    cacheStateManager
) { // 修复 (2025-11-14): 传递application和cacheStateManager给BaseViewModel

    // ========== UiState 模式（2025-12-03）==========
    // 统一的 UI 状态管理，所有 UI 状态集中在一个对象中
    // UI 层通过 uiState.value.xxx 访问状态
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 搜索历史（独立管理，来自 Repository 的 Flow）
    val searchHistory = searchHistoryRepository.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 缓存状态暴露给UI层 (2025-11-14)
    override val cacheStateMap = super.cacheStateMap

    // 最后搜索的关键词，用于避免重复搜索 (2025-11-17)
    private var lastSearchKeyword = ""

    // 分页加载状态 (2025-11-17)
    companion object {
        private const val PAGE_SIZE = 20 // 每页20首歌曲
    }

    // 歌曲搜索分页状态
    private var songCurrentPage = 0

    // 歌手详情页分页状态
    private var artistSongsCurrentPage = 0
    /**
     * 更新搜索关键词
     *
     * UiState 重构 (2025-12-03): 使用 update {} 统一更新状态
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { currentState ->
            if (query.isEmpty()) {
                // 清空所有搜索相关状态
                currentState.copy(
                    searchQuery = query,
                    songResults = emptyList(),
                    artistResults = emptyList(),
                    selectedArtist = null
                )
            } else {
                currentState.copy(searchQuery = query)
            }
        }
    }
    /**
     * 执行搜索
     *
     * UiState 重构 (2025-12-03): 使用 _uiState.update 统一管理状态
     * 优化 (2025-11-17): 添加去重逻辑，避免重复搜索相同关键词
     * 分页更新 (2025-11-17): 重置分页状态，从第一页开始搜索
     * 修复 (2025-11-21): 在执行搜索前清空当前tab的旧数据，避免旧数据残留
     */
    fun search(keyword: String) {
        if (keyword.isBlank()) {
            _uiState.update { it.copy(
                songResults = emptyList(),
                artistResults = emptyList()
            ) }
            return
        }
        // 检查是否为新搜索
        val currentState = _uiState.value
        val isNewSearch = keyword != lastSearchKeyword
        val hasCurrentTabResults = when (currentState.currentTab) {
            SearchTab.SONGS -> currentState.songResults.isNotEmpty()
            SearchTab.ARTISTS -> currentState.artistResults.isNotEmpty()
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "SearchViewModel search: keyword=$keyword, lastKeyword=$lastSearchKeyword, " +
                    "isNewSearch=$isNewSearch, currentTab=${currentState.currentTab}, " +
                    "hasResults=$hasCurrentTabResults"
        )

        // 如果是相同关键词且已有结果，直接返回（去重）
        if (!isNewSearch && hasCurrentTabResults) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "SearchViewModel search: 跳过重复搜索"
            )
            return
        }

        // 清空旧数据
        if (isNewSearch) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "SearchViewModel search: 新搜索，清空所有tab数据"
            )
            _uiState.update { it.copy(
                songResults = emptyList(),
                artistResults = emptyList()
            ) }
        } else {
            // 如果是相同关键词但切换tab，只清空当前tab的旧数据
            LogConfig.d(
                LogConfig.TAG_PLAYER_VIEWMODEL,
                "SearchViewModel search: 相同关键词切换tab，清空当前tab数据"
            )
            when (currentState.currentTab) {
                SearchTab.SONGS -> _uiState.update { it.copy(songResults = emptyList()) }
                SearchTab.ARTISTS -> _uiState.update { it.copy(artistResults = emptyList()) }
            }
        }

        lastSearchKeyword = keyword
        // 重置分页状态
        songCurrentPage = 0
        _uiState.update { it.copy(hasMoreSongs = true) }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            // 保存搜索历史
            searchHistoryRepository.addSearchHistory(keyword)

            when (_uiState.value.currentTab) {
                SearchTab.SONGS -> searchSongs(keyword)
                SearchTab.ARTISTS -> searchArtists(keyword)
            }

            _uiState.update { it.copy(isSearching = false) }
        }
    }
    /**
     * 搜索歌曲（分页）
     *
     * UiState 重构 (2025-12-03): 使用 _uiState.update 更新状态
     * 分页更新 (2025-11-17): 使用分页API，利用API返回的hasMore字段准确判断是否还有更多数据
     */
    private suspend fun searchSongs(keyword: String) {
        when (val result = searchRepository.searchSongsPaged(
            keyword = keyword,
            limit = PAGE_SIZE,
            offset = songCurrentPage * PAGE_SIZE
        )) {
            is Result.Success -> {
                _uiState.update { it.copy(
                    songResults = result.data.songsList,
                    hasMoreSongs = result.data.hasMore,
                    songsCount = result.data.total
                ) }
                if (result.data.songsList.isEmpty()) {
                    showError("未找到相关歌曲")
                }
            }
            is Result.Error -> {
                showError("搜索失败: ${result.exception.message}")
                _uiState.update { it.copy(
                    songResults = emptyList(),
                    hasMoreSongs = false
                ) }
            }
            else -> {}
        }
    }
    /**
     * 加载更多歌曲搜索结果（2025-11-17）
     *
     * UiState 重构 (2025-12-03): 使用 _uiState.update 更新状态
     */
    fun loadMoreSongs() {
        // 防止重复加载
        val currentState = _uiState.value
        if (currentState.isLoadingMoreSongs || !currentState.hasMoreSongs) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreSongs = true) }
            // 加载下一页
            songCurrentPage++

            when (val result = searchRepository.searchSongsPaged(
                keyword = lastSearchKeyword,
                limit = PAGE_SIZE,
                offset = songCurrentPage * PAGE_SIZE
            )) {
                is Result.Success -> {
                    // 追加到现有结果
                    _uiState.update { state ->
                        state.copy(
                            songResults = state.songResults + result.data.songsList,
                            hasMoreSongs = result.data.hasMore,
                            songsCount = result.data.total
                        )
                    }
                }
                is Result.Error -> {
                    showError("加载更多失败: ${result.exception.message}")
                    _uiState.update { it.copy(hasMoreSongs = false) }
                    // 回退页码
                    songCurrentPage--
                }
                else -> {}
            }

            _uiState.update { it.copy(isLoadingMoreSongs = false) }
        }
    }
    /**
     * 搜索歌手
     *
     * UiState 重构 (2025-12-03): 使用 _uiState.update 更新状态
     */
    private suspend fun searchArtists(keyword: String) {
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "SearchViewModel searchArtists: 开始搜索歌手，keyword=$keyword"
        )

        when (val result = searchRepository.searchArtists(keyword)) {
            is Result.Success -> {
                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "SearchViewModel searchArtists: Repository返回 ${result.data.size} 位歌手"
                )
                // 二次过滤：确保所有歌手数据有效
                val validArtists = result.data.filter { artist ->
                    artist.name.isNotBlank() && artist.id != 0L
                }
                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "SearchViewModel searchArtists: 过滤后剩余 ${validArtists.size} 位歌手"
                )

                _uiState.update { it.copy(artistResults = validArtists) }

                LogConfig.d(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "SearchViewModel searchArtists: 已更新artistResults，" +
                            "当前artistResults.size=${_uiState.value.artistResults.size}"
                )
                if (validArtists.isEmpty()) {
                    showError("未找到相关歌手")
                }
            }
            is Result.Error -> {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "SearchViewModel searchArtists: 搜索失败，${result.exception.message}"
                )
                showError("搜索失败: ${result.exception.message}")
                _uiState.update { it.copy(artistResults = emptyList()) }
            }
            else -> {}
        }
    }
    /**
     * 获取歌手的歌曲（分页）
     *
     * 重构：直接调用 searchRepository.getArtistSongsPaged()
     * 分页更新 (2025-11-17): 重置分页状态，从第一页开始加载
     * 优化：直接使用API返回的hasMore字段判断是否还有更多数据
     */
    fun loadArtistSongs(artist: SearchResult.ArtistResult) {
        _uiState.update { it.copy(
            selectedArtist = artist,
            currentTab = SearchTab.SONGS
        ) }
        // 重置分页状态
        artistSongsCurrentPage = 0
        _uiState.update { it.copy(hasMoreArtistSongs = true) }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            when (val result = searchRepository.searchArtistSongsPaged(
                artistName = artist.name,
                limit = PAGE_SIZE,
                offset = artistSongsCurrentPage * PAGE_SIZE
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(
                        songResults = result.data.songsList,
                        hasMoreArtistSongs = result.data.hasMore,
                        songsCount = result.data.total
                    ) }
                }
                is Result.Error -> {
                    showError("加载歌手歌曲失败")
                    _uiState.update { it.copy(hasMoreArtistSongs = false) }
                }
                else -> {}
            }

            _uiState.update { it.copy(isSearching = false) }
        }
    }
    /**
     * 加载更多歌手歌曲（2025-11-17）
     *
     * 优化：直接使用API返回的hasMore字段判断是否还有更多数据
     */
    fun loadMoreArtistSongs() {
        // 防止重复加载
        val currentState = _uiState.value
        if (currentState.isLoadingMoreArtistSongs || !currentState.hasMoreArtistSongs) {
            return
        }
        val artist = currentState.selectedArtist ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreArtistSongs = true) }
            // 加载下一页
            artistSongsCurrentPage++

            when (val result = searchRepository.searchArtistSongsPaged(
                artistName = artist.name,
                limit = PAGE_SIZE,
                offset = artistSongsCurrentPage * PAGE_SIZE
            )) {
                is Result.Success -> {
                    // 追加到现有结果
                    _uiState.update { state ->
                        state.copy(
                            songResults = state.songResults + result.data.songsList,
                            hasMoreArtistSongs = result.data.hasMore,
                            songsCount = result.data.total
                        )
                    }
                }
                is Result.Error -> {
                    showError("加载更多失败: ${result.exception.message}")
                    _uiState.update { it.copy(hasMoreArtistSongs = false) }
                    // 回退页码
                    artistSongsCurrentPage--
                }
                else -> {}
            }

            _uiState.update { it.copy(isLoadingMoreArtistSongs = false) }
        }
    }
    /**
     * 切换标签页
     *
     * 修复 (2025-11-21): 修复tab切换逻辑，确保在切换tab且该tab无对应关键词的搜索结果时执行搜索
     */
    fun switchTab(tab: SearchTab) {
        val currentState = _uiState.value
        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "SearchViewModel switchTab: 切换到${tab}, searchQuery=${currentState.searchQuery}, " +
                    "lastKeyword=$lastSearchKeyword"
        )

        _uiState.update { it.copy(currentTab = tab) }

        // 判断是否需要执行搜索
        val needSearch = when (tab) {
            SearchTab.SONGS -> {
                currentState.searchQuery.isNotBlank() &&
                        (currentState.songResults.isEmpty() || lastSearchKeyword != currentState.searchQuery)
            }
            SearchTab.ARTISTS -> {
                currentState.searchQuery.isNotBlank() &&
                        (currentState.artistResults.isEmpty() || lastSearchKeyword != currentState.searchQuery)
            }
        }

        LogConfig.d(
            LogConfig.TAG_PLAYER_VIEWMODEL,
            "SearchViewModel switchTab: needSearch=$needSearch, " +
                    "songResults=${currentState.songResults.size}, " +
                    "artistResults=${currentState.artistResults.size}"
        )

        if (needSearch) {
            search(currentState.searchQuery)
        }
    }
    /**
     * 清除搜索历史
     *
     * 重构：直接调用 searchHistoryRepository.clearSearchHistory()
     */
    fun clearSearchHistory() {
        viewModelScope.launch {
            // 重构：直接调用 Repository 方法
            searchHistoryRepository.clearSearchHistory()
        }
    }
    /**
     * 删除单条搜索历史
     *
     * 新增 (2025-11-21): 支持用户删除单条历史记录
     * 符合MD3设计原则，提供精确控制
     *
     * @param keyword 要删除的关键词
     */
    fun deleteSearchHistory(keyword: String) {
        viewModelScope.launch {
            searchHistoryRepository.deleteSearchHistory(keyword)
        }
    }
    /**
     * 设置临时歌手状态（2025-11-17）
     *
     * 用于避免界面闪现，在真实数据加载前先显示歌手详情页框架
     */
    fun setTemporaryArtist(artistName: String) {
        val tempArtist = SearchResult.ArtistResult(
            id = 0,
            name = artistName,
            avatar = null,
            songCount = 0
        )
        _uiState.update { it.copy(
            selectedArtist = tempArtist,
            currentTab = SearchTab.SONGS,
            songResults = emptyList(),
            searchQuery = artistName
        ) }
    }
    /**
     * 根据歌手名称搜索并直接进入歌手详情
     *
     * 使用场景：从播放页面点击歌手名称跳转
     *
     * 实现逻辑（2025-11-17）:
     * 1. 执行歌手搜索
     * 2. 如果找到歌手，更新为真实歌手信息并加载歌曲列表
     * 3. 如果没找到，保持临时歌手状态（空歌曲列表）
     *
     * 注意：调用此方法前应先调用setTemporaryArtist()设置临时状态
     */
    fun searchArtistByName(artistName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            when (val result = searchRepository.searchArtists(artistName)) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        _uiState.update { it.copy(artistResults = result.data) }
                        loadArtistSongs(result.data.first())
                    } else {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSearching = false) }
                    showError("搜索歌手失败: ${result.exception.message}")
                }
                else -> {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }
    }
    /**
     * 返回到搜索结果
     */
    fun backToSearch() {
        _uiState.update { it.copy(selectedArtist = null) }
        if (_uiState.value.searchQuery.isNotBlank()) {
            search(_uiState.value.searchQuery)
        }
    }
    // clearError() 方法已移除 (2025-11-15)
    // 原因：SharedFlow不需要手动清除错误消息，每次emit都是新事件
    /**
     * 加载详情（统一入口）
     *
     * 重构 (2025-11-27):
     * - 统一处理歌单详情和歌手详情的加载
     * - 根据 source 自动路由到对应的加载逻辑
     *
     * @param playlistId 歌单ID或歌手ID
     * @param playlistName 歌单名称或歌手名称
     * @param source 来源类型
     * @param cover 封面URL
     * @param playCount 播放次数
     * @param description 描述信息
     */
    fun loadDetail(
        playlistId: Long,
        playlistName: String,
        source: com.minimalistmusic.domain.model.PlaylistSource,
        cover: String = "",
        playCount: Long = 0L,
        description: String? = null
    ) {
        when (source) {
            com.minimalistmusic.domain.model.PlaylistSource.ARTIST_PLAYLIST -> {
                _uiState.update { it.copy(detailType = DetailType.ARTIST) }
                loadArtistDetail(playlistName)
            }
            com.minimalistmusic.domain.model.PlaylistSource.GENERATED_PLAYLIST -> {
                _uiState.update { it.copy(detailType = DetailType.PLAYLIST) }
                loadPlaylistDetail(playlistId, playlistName, cover, playCount, description)
            }
        }
    }
    /**
     * 加载普通歌单详情
     *
     * 重构 (2025-11-27):
     * - 从 PlaylistDetailViewModel 迁移而来
     * - 使用 OnlineMusicRepository.getPlaylistDetail()
     * - 普通歌单一次性加载所有歌曲，不支持分页
     */
    private fun loadPlaylistDetail(
        playlistId: Long,
        playlistName: String,
        cover: String,
        playCount: Long,
        description: String?
    ) {
        val playlistDetail = com.minimalistmusic.domain.model.RecommendPlaylist(
            id = playlistId,
            name = playlistName,
            cover = cover,
            playCount = playCount,
            description = description,
            source = com.minimalistmusic.domain.model.PlaylistSource.GENERATED_PLAYLIST
        )
        _uiState.update { it.copy(playlistDetail = playlistDetail) }

        viewModelScope.launch {
            _uiState.update { it.copy(
                isSearching = true,
                songResults = emptyList(),
                hasMoreSongs = false // 普通歌单不支持分页
            ) }

            when (val result =
                onlineMusicRepository.getPlaylistDetail(playlistId.toString(), "playlist")) {
                is Result.Success -> {
                    val updatedPlaylist = playlistDetail.copy(
                        songsCount = result.data.size.toLong()
                    )
                    _uiState.update { it.copy(
                        songResults = result.data,
                        songsCount = result.data.size,
                        playlistDetail = updatedPlaylist
                    ) }
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "SearchViewModel loadPlaylistDetail: 加载歌单成功，共${result.data.size}首歌曲"
                    )
                }
                is Result.Error -> {
                    showError("加载歌单失败: ${result.exception.message}")
                    LogConfig.e(
                        LogConfig.TAG_PLAYER_VIEWMODEL,
                        "SearchViewModel loadPlaylistDetail: 加载失败，${result.exception.message}"
                    )
                }
                else -> {}
            }

            _uiState.update { it.copy(isSearching = false) }
        }
    }
    /**
     * 加载歌手详情（重命名，原 searchArtistByName + loadArtistSongs）
     *
     * 重构 (2025-11-27):
     * - 整合原有的歌手搜索和歌曲加载逻辑
     * - 设置临时歌手状态并搜索真实信息
     */
    private fun loadArtistDetail(artistName: String) {
        setTemporaryArtist(artistName)

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            when (val result = searchRepository.searchArtists(artistName)) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        _uiState.update { it.copy(artistResults = result.data) }
                        loadArtistSongs(result.data.first())
                    } else {
                        _uiState.update { it.copy(isSearching = false) }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSearching = false) }
                    showError("搜索歌手失败: ${result.exception.message}")
                }
                else -> {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }
    }
    /**
     * 加载更多歌曲（统一入口）
     *
     * 重构 (2025-11-27):
     * - 根据当前详情类型自动路由
     * - 普通歌单不支持加载更多
     * - 歌手详情支持分页加载
     */
    fun loadMoreDetailSongs() {
        when (_uiState.value.detailType) {
            DetailType.PLAYLIST -> {
                // 普通歌单不支持加载更多
                return
            }
            DetailType.ARTIST -> {
                // 歌手详情：调用原有的分页逻辑
                loadMoreArtistSongs()
            }
            null -> return
        }
    }
    /**
     * 返回搜索页
     *
     * 重构 (2025-11-27):
     * - 清除详情状态，返回搜索模式
     */
    fun backToSearchFromDetail() {
        _uiState.update { it.copy(
            detailType = null,
            playlistDetail = null,
            selectedArtist = null,
            songResults = emptyList()
        ) }
    }
}