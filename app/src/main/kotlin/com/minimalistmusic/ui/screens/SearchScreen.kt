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

package com.minimalistmusic.ui.screens

/**
 * 搜索模块
 *
 * 提供音乐和艺术家的搜索功能，支持搜索历史管理和多标签页切换。
 *
 * ## 核心功能
 *
 * ### 1. 搜索功能
 * - 实时搜索（输入时自动触发）
 * - 支持歌曲搜索（歌名、艺术家）
 * - 支持艺术家搜索
 * - 搜索历史记录（最多10条）
 *
 * ### 2. 标签页系统
 * - **歌曲** tab：显示搜索到的歌曲列表
 * - **艺术家** tab：显示搜索到的艺术家列表
 * - 支持左右滑动切换（TabRow）
 *
 * ### 3. 搜索历史
 * - 自动保存搜索关键词
 * - 点击历史记录重新搜索
 * - 单条删除（左滑）
 * - 清空全部历史
 *
 * ### 4. 艺术家详情
 * - 点击艺术家查看其歌曲列表
 * - 显示艺术家头像（圆形）
 * - 支持播放艺术家的歌曲
 *
 * ## UI结构
 * ```
 * TopAppBar
 * └── 搜索框 (带清空按钮)
 *
 * 未搜索状态
 * ├── 搜索历史 (最多10条)
 * └── 清空历史按钮
 *
 * 搜索结果状态
 * ├── TabRow (歌曲 | 艺术家)
 * └── 内容区域
 *     ├── 歌曲列表 (使用SongListItem)
 *     └── 艺术家列表 (头像 + 名称)
 *
 * 艺术家歌曲列表（ModalBottomSheet）
 * └── 该艺术家的所有歌曲
 * ```
 *
 * ## 状态管理
 * - [searchQuery]: 搜索关键词
 * - [songResults]: 歌曲搜索结果
 * - [artistResults]: 艺术家搜索结果
 * - [searchHistory]: 搜索历史记录
 * - [isSearching]: 搜索加载状态
 * - [selectedTab]: 当前选中的标签页
 * - [selectedArtist]: 当前查看的艺术家
 *
 * ## 交互流程
 * 1. 输入搜索关键词 → 触发搜索 → 显示结果
 * 2. 点击历史记录 → 填充搜索框 → 自动搜索
 * 3. 点击歌曲 → 播放歌曲 → 导航到播放器
 * 4. 点击艺术家 → 显示艺术家歌曲抽屉
 * 5. 点击收藏 → 触发同步提醒（未登录时）
 *
 * ## 性能优化
 * - 搜索防抖（避免频繁请求）
 * - LazyColumn列表复用
 * - 图片异步加载和缓存
 * - 状态收集使用collectAsState
 *
 * ## 组件复用
 * - 使用通用SongListItem组件（P0重构）
 * - 解耦ViewModel依赖
 *
 * @since 2025-11-11
 */
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.minimalistmusic.domain.model.SearchResult
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.components.ListEndIndicator
import com.minimalistmusic.ui.components.SongListItem
import com.minimalistmusic.ui.components.SyncReminderDialogHost
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import com.minimalistmusic.presentation.viewmodel.SearchTab
import com.minimalistmusic.presentation.viewmodel.SearchViewModel
import com.minimalistmusic.ui.components.EmptyStateView
import com.minimalistmusic.ui.theme.Spacing
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: SearchViewModel = hiltViewModel(),
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    initialArtistName: String? = null,
    isPlayerOverlayVisible: Boolean = false,  // 修复 (2025-11-18): 传递播放器浮层状态
) {
    // UiState 模式 (2025-12-03): 统一状态管理
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    // P0重构：收集收藏歌曲ID列表
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    // 缓存状态管理 (2025-11-14):
    // 收集全局缓存状态Map，当缓存状态变化时自动重组UI
    val cacheStates by viewModel.cacheStateMap.collectAsStateWithLifecycle()
    // 播放状态（2025-11-20：用于显示序号和播放动画）
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    // 搜索模式状态 (2025-11-18)
    // 修复 (2025-11-18): 重新定义搜索模式的判断逻辑
    // - 搜索模式：用户正在输入关键词，显示搜索历史
    // - 结果模式：用户查看搜索结果（歌曲列表/歌手列表/歌手详情）
    //
    // 判断标准：基于是否有搜索结果，而不是基于 searchQuery 是否为空
    // - 有搜索结果 → 结果模式
    // - 无搜索结果 → 搜索模式
    val hasSearchResults = uiState.songResults.isNotEmpty() || uiState.artistResults.isNotEmpty()
    var isSearchMode by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // 监听搜索结果变化，自动切换模式
    LaunchedEffect(hasSearchResults) {
        if (hasSearchResults) {
            isSearchMode = false  // 有结果 → 进入结果模式
        }
    }
    // 监听搜索框清空，返回搜索模式
    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isEmpty()) {
            isSearchMode = true
        }
    }
    // 处理初始歌手名称搜索（2025-11-17）
    // 优化：立即设置临时歌手状态，避免闪现搜索界面
    LaunchedEffect(initialArtistName) {
        initialArtistName?.let { artistName ->
            // 立即设置临时歌手，让界面直接显示歌手详情页（loading状态）
            viewModel.setTemporaryArtist(artistName)
            // 然后异步搜索真实数据
            viewModel.searchArtistByName(artistName)
        }
    }

    // 架构重构：错误处理已迁移到GlobalErrorChannel
    // 应用根部的GlobalErrorHandler会自动处理所有ViewModel的错误
    // 此处无需添加任何错误监听代码

    // 系统返回键处理 (2025-11-18)
    // 修复 (2025-11-18): BackHandler只处理SearchScreen内部状态转换
    //
    // 关键判断（第三次修复 - 最终方案）：
    // 问题演进：
    // 1. 第一次：enabled = true → 总是拦截 → 需要3次返回
    // 2. 第二次：enabled = hasInternalState（基于searchQuery） → 需要2次返回
    // 3. 第三次：enabled = !isSearchMode（基于搜索结果） → 仍需2次返回
    //
    // 根本原因：SearchScreen 无法区分"用户在SearchScreen按返回键"还是"用户在PlayerScreen按返回键"
    //
    // 最终解决方案：
    // - 从 MainScreen 传递 isPlayerOverlayVisible 状态
    // - 当播放器浮层打开时，SearchScreen 不拦截返回键
    // - 当播放器浮层关闭时，根据内部状态决定是否拦截
    val hasInternalState = when {
        isPlayerOverlayVisible -> false                        // 播放器打开 → 不拦截
        uiState.selectedArtist != null -> true                 // 歌手详情页 → 拦截
        !isSearchMode -> true                                  // 结果模式 → 拦截
        else -> false                                           // 搜索模式 → 不拦截
    }
    BackHandler(enabled = hasInternalState) {
        when {
            uiState.selectedArtist != null -> viewModel.backToSearch()  // 歌手详情 → 搜索结果
            !isSearchMode -> isSearchMode = true                        // 结果模式 → 搜索模式
        }
    }
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = {
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("搜索歌曲、歌手") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    // 点击搜索框进入搜索模式
                                    if (!isSearchMode) {
                                        isSearchMode = true
                                    }
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    // 隐藏键盘
                                    focusManager.clearFocus()
                                    // 执行搜索
                                    viewModel.search(uiState.searchQuery)
                                    isSearchMode = false
                                }
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // 修复 (2025-11-18): 与 BackHandler 逻辑保持一致
                        when {
                            uiState.selectedArtist != null -> {
                                // 在歌手详情页：返回搜索结果
                                viewModel.backToSearch()
                            }
                            !isSearchMode -> {
                                // 在结果模式：返回搜索模式
                                isSearchMode = true
                            }
                            else -> {
                                // 在搜索模式：返回上一页
                                navController.navigateUp()
                            }
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 搜索结果页只显示清空按钮，不显示搜索图标
                    // 用户可以直接点击搜索框重新进入搜索模式
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.updateSearchQuery("")
                            isSearchMode = true
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSearchMode) {
                // 搜索模式：显示搜索历史和输入提示
                SearchHistoryContent(
                    history = searchHistory,
                    currentInput = uiState.searchQuery,
                    onHistoryClick = { keyword ->
                        // 隐藏键盘
                        focusManager.clearFocus()
                        // 更新搜索词并执行搜索
                        viewModel.updateSearchQuery(keyword)
                        viewModel.search(keyword)
                        isSearchMode = false
                    },
                    onClearHistory = { viewModel.clearSearchHistory() },
                    onDeleteHistory = { keyword ->
                        // 新增 (2025-11-21): 删除单条历史记录
                        viewModel.deleteSearchHistory(keyword)
                    }
                )
            } else {
                // 结果模式：显示搜索结果
                Column {
                    // 标签页 (修复 2025-11-20，优化 2025-11-21)
                    // MD3优化 (2025-11-21):
                    // - 指示器宽度调整为40%（更平衡）
                    // - 添加1dp轻微圆角（更柔和）
                    // - 选中Tab文字加粗（增强层级）
                    // - 支持左右滑动切换Tab（HorizontalPager）
                    // PagerState用于管理滑动状态
                    val pagerState = rememberPagerState(
                        initialPage = uiState.currentTab.ordinal,
                        pageCount = { 2 }
                    )
                    // 监听PagerState变化，同步到ViewModel
                    LaunchedEffect(pagerState.currentPage) {
                        val newTab = SearchTab.values()[pagerState.currentPage]
                        if (newTab != uiState.currentTab) {
                            viewModel.switchTab(newTab)
                        }
                    }
                    // 监听ViewModel的Tab变化，同步到PagerState
                    LaunchedEffect(uiState.currentTab) {
                        if (pagerState.currentPage != uiState.currentTab.ordinal) {
                            pagerState.animateScrollToPage(uiState.currentTab.ordinal)
                        }
                    }
                    TabRow(
                        selectedTabIndex = uiState.currentTab.ordinal,
                        containerColor = Color.Transparent,
                        divider = {},  // MD3规范：移除TabRow下方的分割线
                        indicator = { tabPositions ->
                            if (uiState.currentTab.ordinal < tabPositions.size) {
                                val selectedTabPosition = tabPositions[uiState.currentTab.ordinal]
                                Box(
                                    modifier = Modifier
                                        .tabIndicatorOffset(selectedTabPosition)
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .padding(horizontal = selectedTabPosition.width * 0.3f) // 指示器宽度为40%，居中
                                        .clip(RoundedCornerShape(1.dp))  // 轻微圆角，更柔和
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = uiState.currentTab == SearchTab.SONGS,
                            onClick = { viewModel.switchTab(SearchTab.SONGS) },
                            text = {
                                Text(
                                    "歌曲",
                                    fontWeight = if (uiState.currentTab == SearchTab.SONGS)
                                        FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = uiState.currentTab == SearchTab.ARTISTS,
                            onClick = { viewModel.switchTab(SearchTab.ARTISTS) },
                            text = {
                                Text(
                                    "歌手",
                                    fontWeight = if (uiState.currentTab == SearchTab.ARTISTS)
                                        FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                    // 内容区域 - 使用HorizontalPager支持滑动切换
                    if (uiState.isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> {
                                    // 歌曲Tab
                                    SongResultsView(
                                        songs = uiState.songResults,
                                        favoriteSongIds = favoriteSongIds,
                                        onSongClick = { index ->
                                            // 修复 (2025-11-21): 避免重复点击正在播放的歌曲
                                            val song = uiState.songResults.getOrNull(index)
                                            if (song != null && !playerViewModel.canPlaySong(song.id)) {
                                                return@SongResultsView
                                            }
                                            playerViewModel.playSongs(uiState.songResults, index)
//                                            navController.navigate("player") {
//                                                launchSingleTop = true
//                                            }
                                        },
                                        onFavoriteClick = { song ->
                                            accountSyncViewModel.toggleFavorite(song)
                                        },
                                        cacheStates = cacheStates,
                                        hasMore = uiState.hasMoreSongs,
                                        isLoadingMore = uiState.isLoadingMoreSongs,
                                        onLoadMore = { viewModel.loadMoreSongs() },
                                        currentPlayingSongId = playbackState.currentSong?.id,
                                        isActuallyPlaying = playbackState.isPlaying,
                                        onRefresh = {
                                            // 重新搜索当前关键词（2025-11-24：带防抖）
                                            if (uiState.searchQuery.isNotEmpty()) {
                                                viewModel.search(uiState.searchQuery)
                                            }
                                        }
                                    )
                                }
                                1 -> {
                                    // 歌手Tab
                                    ArtistResultsView(
                                        artists = uiState.artistResults,
                                        onArtistClick = { artist ->
                                            val encodedArtistName =
                                                java.net.URLEncoder.encode(artist.name, "UTF-8")
                                            navController.navigate("artist_detail/$encodedArtistName")
                                        },
                                        onRefresh = {
                                            // 重新搜索当前关键词（2025-11-24：带防抖）
                                            if (uiState.searchQuery.isNotEmpty()) {
                                                viewModel.search(uiState.searchQuery)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // 监听搜索查询变化，自动搜索
    // 修复 (2025-11-17): 使用LaunchedEffect(key)确保只在searchQuery实际变化时触发
    // 避免从播放页面返回时重复搜索,这里逻辑修改为用户主动点击搜索或关键词触发搜索
//    LaunchedEffect(searchQuery) {
//        if (searchQuery.isNotEmpty()) {
//            kotlinx.coroutines.delay(500) // 防抖
//            // ViewModel中会进行去重检查
//            viewModel.search(searchQuery)
//        }
//    }
    // 2025-11-17: 移除自动搜索逻辑
    // 用户需要点击搜索按钮或点击搜索历史才触发搜索
    // 提升用户控制感，避免不必要的网络请求
    // 同步提醒对话框 - 使用简化版本
    SyncReminderDialogHost(
        accountSyncViewModel = accountSyncViewModel,
        navController = navController
    )
}
/**
 * 搜索历史内容区域
 *
 * 2025-11-17 更新：
 * - 添加 currentInput 参数，显示当前输入提示
 * - 当用户输入时，第一条显示与输入同步的提示条目
 * - 提升搜索体验，明确告知用户当前输入
 *
 * 2025-11-21 更新：
 * - 添加单条删除功能，每个历史记录右侧显示删除图标
 * - 符合MD3设计原则：ListItem + IconButton + Close图标
 * - 支持精确控制，用户可删除单条记录
 *
 * @param onDeleteHistory 删除单条历史记录的回调
 */
@Composable
fun SearchHistoryContent(
    history: List<String>,
    currentInput: String,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteHistory: (String) -> Unit = {},  // 新增：删除单条历史记录
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        // 显示当前输入提示（如果有输入）
        if (currentInput.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 当前输入提示条目
            // MD3优化 (2025-11-21): 添加圆角，与搜索历史条目保持一致
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),  // 8dp圆角，与历史条目一致
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),  // 轻微主题色背景，区分当前输入
                tonalElevation = 0.dp
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = currentInput,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable { onHistoryClick(currentInput) }
                )
            }
            // 分隔线
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        // 搜索历史列表
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearHistory) {
                    Text("清空")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // MD3优化 (2025-11-21): 添加圆角和间距
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)  // 条目之间8dp间距
            ) {
                history.forEach { keyword ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),  // 8dp圆角，柔和视觉
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),  // 轻微背景色区分
                        tonalElevation = 0.dp
                    ) {
                        ListItem(
                            headlineContent = { Text(keyword) },
                            leadingContent = {
                                Icon(Icons.Filled.History, contentDescription = null)
                            },
                            trailingContent = {
                                // 新增 (2025-11-21): 删除按钮
                                // MD3设计：使用 IconButton 确保足够的点击区域，Close图标表示删除
                                IconButton(
                                    onClick = { onDeleteHistory(keyword) },
                                    modifier = Modifier.size(40.dp)  // 稍小一点，不喧宾夺主
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,  // 低调配色
                                        modifier = Modifier.size(20.dp)  // 图标稍小
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onHistoryClick(keyword) }
                        )
                    }
                }
            }
        } else if (currentInput.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "搜索歌曲或歌手",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
/**
 * P0重构（2025-11-11）：添加favoriteSongIds参数，解耦ViewModel依赖
 * 分页支持（2025-11-17）：添加分页加载支持
 * 索引修复（2025-11-18）：onSongClick 改为传递索引而不是 Song 对象
 * 优化（2025-11-20）：添加序号显示和播放状态动画，与歌单详情页保持一致
 * 优化（2025-11-24）：空状态支持点击刷新，使用EmptyStateView组件
 */
@Composable
fun SongResultsView(
    songs: List<Song>,
    favoriteSongIds: Set<Long>,
    onSongClick: (Int) -> Unit,  // 修复：改为传递索引
    onFavoriteClick: (Song) -> Unit,
    cacheStates: Map<Long, Boolean>,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    currentPlayingSongId: Long? = null,  // 新增：当前播放歌曲ID
    isActuallyPlaying: Boolean = false,   // 新增：是否真正在播放
    onRefresh: (() -> Unit)? = null,  // 新增（2025-11-24）：刷新回调
) {
    if (songs.isEmpty()) {
        // 优化 (2025-11-24): 使用EmptyStateView组件，支持点击刷新
        EmptyStateView(
            icon = Icons.Filled.QueueMusic,
            title = "暂无搜索结果",
            subtitle = if (onRefresh != null) "点击任意位置刷新" else "",
            onRefresh = onRefresh
        )
    } else {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            // 2025-11-24: 添加快速滑动条
            LazyColumnScrollbar(
                state = listState,
                settings = ScrollbarSettings(
                    thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    thumbSelectedColor = MaterialTheme.colorScheme.primary,
                    thumbMinLength = 0.1f,  // 修复 (2025-11-25): 使用thumbMinLength代替thumbMaxLength，固定滑动条最小长度
                    selectionMode = ScrollbarSelectionMode.Thumb
                )
            ) {
            LazyColumn(
                state = listState,
                contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+垂直内边距 (2025-11-28)
                verticalArrangement = Arrangement.spacedBy(Spacing.Content.extraSmall)  // MD3规范：4dp历史项间距
            ) {
                // 修复 (2025-11-18): 使用 items(size) { index } 代替 items(list) { item }
                // 原因：避免在回调中使用 indexOf/indexOfFirst 造成性能问题
                // 优化 (2025-11-20): 添加序号显示和播放状态动画
                items(songs.size) { index ->
                    val song = songs[index]
                    val isCurrentPlaying = currentPlayingSongId == song.id
                    // P0重构：使用通用SongListItem组件，解耦ViewModel依赖
                    // 2025-11-14更新：添加响应式缓存状态支持
                    // 2025-11-20更新：添加序号显示和播放状态动画
                    SongListItem(
                        song = song,
                        isFavorite = favoriteSongIds.contains(song.id),
                        isCached = cacheStates[song.id] ?: song.isLocal,
                        onClick = { onSongClick(index) },  // 修复：传递索引
                        onFavoriteClick = { onFavoriteClick(song) },
                        showIndex = true,               // 显示序号
                        index = index + 1,              // 序号从1开始
                        isPlaying = isCurrentPlaying,   // 播放状态
                        isActuallyPlaying = isActuallyPlaying && isCurrentPlaying  // 播放动画状态
                    )
                }
                // 加载更多指示器（2025-11-17）
                if (hasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                TextButton(onClick = onLoadMore) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
                // 底线提示（2025-11-20：使用 ListEndIndicator 统一风格）
                if (!hasMore && songs.isNotEmpty()) {
                    item {
                        ListEndIndicator(
                            itemCount = songs.size,
                            titleText = "搜索结果",
                            onScrollToTop = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        )
                    }
                }
            }
            }  // LazyColumnScrollbar结束
        }
    }
}
// P0重构说明（2025-11-11）：
// SearchSongItem 已移至 components/SongListItem.kt
// - 消除了与 HomeScreen 和 RecommendPlaylistDetailScreen 的代码重复
// - 解耦了 ViewModel 依赖，提升了可测试性和可复用性
/**
 * 优化（2025-11-24）：空状态支持点击刷新，使用EmptyStateView组件
 */
@Composable
fun ArtistResultsView(
    artists: List<SearchResult.ArtistResult>,
    onArtistClick: (SearchResult.ArtistResult) -> Unit,
    onRefresh: (() -> Unit)? = null,  // 新增（2025-11-24）：刷新回调
) {
    if (artists.isEmpty()) {
        // 优化 (2025-11-24): 使用EmptyStateView组件，支持点击刷新
        EmptyStateView(
            icon = Icons.Filled.Person,
            title = "暂无搜索结果",
            subtitle = if (onRefresh != null) "点击任意位置刷新" else "",
            onRefresh = onRefresh
        )
    } else {
        // 修复 (2025-11-21): 添加 Box 容器，指定 Alignment.TopStart
        // 确保 LazyColumn 从顶部开始显示，避免在 HorizontalPager 中被居中对齐
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+垂直内边距
                verticalArrangement = Arrangement.spacedBy(Spacing.Content.small)  // MD3规范：8dp艺术家项间距
            ) {
                items(artists) { artist ->
                    ArtistItem(
                        artist = artist,
                        onClick = { onArtistClick(artist) }
                    )
                }
            }
        }
    }
}
@Composable
fun ArtistItem(
    artist: SearchResult.ArtistResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像 - 2025-11-17: 使用紫色风格的人头占位符
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        ) {
            if (!artist.avatar.isNullOrEmpty()) {
                SubcomposeAsyncImage(
                    model = artist.avatar,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val state = painter.state
                    when {
                        state is coil.compose.AsyncImagePainter.State.Loading -> {
                            // 加载中：显示人头占位符
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = "歌手",
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        state is coil.compose.AsyncImagePainter.State.Error ||
                                state is coil.compose.AsyncImagePainter.State.Empty -> {
                            // 加载失败：显示人头占位符
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = "歌手",
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        else -> {
                            // 加载成功：显示图片，使用Crop缩放
                            SubcomposeAsyncImageContent(
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            } else {
                // 无头像：显示人头占位符
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "歌手",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 歌手信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${artist.songCount} 张专辑",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}
