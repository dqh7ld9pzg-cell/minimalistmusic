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

package com.minimalistmusic.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.minimalistmusic.presentation.error.GlobalErrorHandler
import com.minimalistmusic.ui.components.EnhancedPlaylistBottomSheet
import com.minimalistmusic.ui.components.SyncReminderDialogHost
import com.minimalistmusic.ui.screens.CachedMusicScreen
import com.minimalistmusic.ui.screens.DetailScreen
import com.minimalistmusic.ui.screens.DiscoverScreen
import com.minimalistmusic.ui.screens.FavoriteSongsScreen
import com.minimalistmusic.ui.screens.HomeScreen
import com.minimalistmusic.ui.screens.LoginScreen
import com.minimalistmusic.ui.screens.MyFavoritePlaylistsScreen
import com.minimalistmusic.ui.screens.PlayerScreen
import com.minimalistmusic.ui.screens.ProfileScreen
import com.minimalistmusic.ui.screens.SearchScreen
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.HomeViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel

/**
 * 主界面
 *
 * 导航策略 (2025-11-18):
 * - 一级页面（home, discover, profile）显示底部导航栏
 * - 二级页面（详情、搜索等）隐藏底部导航栏，仅保留迷你播放器
 * - 符合Material Design 3规范和主流应用习惯（微信、淘宝等）
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MainScreen() {
    // 全局错误处理器：统一处理所有ViewModel的错误消息
    // 自动去重、排队、Toast显示，无需在各Screen重复监听
    GlobalErrorHandler()

    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val accountSyncViewModel: AccountSyncViewModel = hiltViewModel()
    /**
     *  修复 (2025-11-23): 使用 Activity 级别的 ViewModelStoreOwner 创建共享 ViewModel
     *  问题：每个 @Composable 调用 hiltViewModel() 都会创建新的 ViewModel 实例
     *  导致：
     *    - MiniPlayer 被渲染2次（一级页面和二级页面各1次）→ 2个 PlayerViewModel 实例
     *    - HomeScreen、DiscoverScreen 等各创建1个实例
     *    - 同一首歌触发多次歌词加载、多次缓存监听
     *    解决：使用 Activity 作为 ViewModelStoreOwner，整个 Activity 共享同一个 ViewModel
     */
    val sharedPlayerViewModel: PlayerViewModel =
        hiltViewModel(viewModelStoreOwner = context as ComponentActivity)
    /**
     *   优化 (2025-11-23): 共享 HomeViewModel 和 CachedMusicViewModel，完全消除页面进入闪烁
     *   原理：
     *   - Activity 创建时就实例化这些 ViewModel
     *   - ViewModel 的 init {} 立即执行，开始加载数据（Eagerly）
     *   - 用户进入 favorites 或 cached_music 页面时，数据已加载完毕
     *   - 完全消除 empty → data 的闪烁
     */
    val sharedHomeViewModel: HomeViewModel = hiltViewModel(viewModelStoreOwner = context)
    val sharedCachedMusicViewModel: CachedMusicViewModel =
        hiltViewModel(viewModelStoreOwner = context)
    /**
     *  定义一级页面（显示底部导航栏的页面）
     *      优化 (2025-11-23): 将 "player" 也视为一级页面，避免路由切换时底部栏结构变化
     *      问题分析：
     *      - 从一级页面（home/discover/profile）点击迷你播放器
     *      - 路由切换：home → player → home（通过 popBackStack）
     *      - showBottomBar 变化：true → false → true
     *      - bottomBar 结构变化：Column(MiniPlayer+Nav) → MiniPlayer → Column(MiniPlayer+Nav)
     *      - 导致底部导航栏消失/重现，页面上下跳动
     *      解决方案：
     *      - 将 player 也算作一级页面
     *      - showBottomBar 保持：true → true → true
     *      - bottomBar 结构不变，只有 AnimatedVisibility 控制 MiniPlayer 显隐
     *      - 页面稳定，无跳动
     */
    val topLevelRoutes = setOf("home", "discover", "profile")
    val showBottomBar = currentRoute in topLevelRoutes
    // 播放器浮层状态管理 (2025-11-18)
    var showPlayerOverlay by remember { mutableStateOf(false) }
    // 发现页刷新事件 (2025-11-19)
    // 使用时间戳作为触发器，每次双击时更新时间戳，DiscoverScreen监听变化来刷新
    var discoverRefreshTrigger by remember { mutableLongStateOf(0L) }
    // 监听路由变化，拦截 player 路由
    LaunchedEffect(currentRoute) {
        when {
            currentRoute == "player" -> {
                // 显示播放器浮层
                showPlayerOverlay = true
                // 延迟后返回到之前的路由（通过 popBackStack 而不是 navigate）
                kotlinx.coroutines.delay(50)
                // 直接 pop player 路由，返回上一个页面
                navController.popBackStack()
            }
            currentRoute != null && currentRoute != "player" -> {
                // 不再需要记录路由，因为使用 popBackStack
            }
        }
    }
    // 关闭播放器的函数
    val closePlayer: () -> Unit = {
        showPlayerOverlay = false
    }
    // 主页面：包括首页内容（聆听足迹、我喜欢&已缓存卡片入口）、迷你播放器、底部导航栏
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        /**
         * 内容区域（所有页面，包括作为占位的 player）
         * 沉浸式模式 (2025-11-21)
         * Scaffold 会自动处理 WindowInsets，内容会延伸到系统栏区域
         * bottomBar 会自动添加底部导航栏 inset
         */
        Scaffold(
            bottomBar = {
                /**
                 *                 修复 (2025-11-29): 实现 QQ 音乐风格的底部栏动画 + 智能占位符优化
                 *                  设计目标:
                 *                  - 一级页面 → 二级页面: MiniPlayer + BottomNav 整体向下移动 56dp
                 *                  - 二级页面 → 一级页面: MiniPlayer + BottomNav 整体向上移动 56dp
                 *                  - 播放器页面: MiniPlayer 隐藏 (无动画)
                 *                  - 二级页面稳定后: 移除占位符,让内容完全可见
                 *
                 *                  实现方案:
                 *                  1. 使用 graphicsLayer.translationY 代替 offset (不影响布局,只影响渲染)
                 *                  2. 动画进行中: bottomBar 保持 120dp 高度 (MiniPlayer 64dp + Spacer 56dp)
                 *                  3. 动画完成后: bottomBar 变为 64dp 高度 (MiniPlayer 64dp, 移除 Spacer)
                 *                  4. 通过 translationY 向下平移,BottomNav 移出屏幕,MiniPlayer 保持可见
                 *
                 *                  智能占位符策略:
                 *                  - 进入二级页面时: 使用 Spacer 占位 → 触发动画 → 动画完成后移除 Spacer
                 *                  - 返回一级页面时: 先恢复 Spacer → 触发动画 → 保持 Spacer
                 *                  - 优势: 既避免页面跳动,又让内容完全可见
                 *
                 *                  动画原理:
                 *                  - 动画中: Scaffold padding = 120dp (MiniPlayer + Spacer)
                 *                  - 动画后: Scaffold padding = 64dp (仅 MiniPlayer,内容区向下扩展 56dp)
                 *
                 *                  计算整个 Column 的垂直平移量
                 *                  优化 (2025-11-29): 智能占位符策略
                 *                  - 动画进行时：使用占位符保持高度，避免页面跳动
                 *                  - 动画完成后：移除占位符，同时重置 translationY，让内容完全可见
                 */
                var showSpacer by remember { mutableStateOf(true) }  // 控制占位符显示
                // 关键修复：占位符隐藏后，translationY 必须变为 0，否则 MiniPlayer 会向下滑动
                val targetTranslationY = when {
                    showBottomBar -> 0.dp  // 一级页面：无偏移
                    showSpacer -> 56.dp    // 二级页面有占位符：向下偏移 56dp
                    else -> 0.dp           // 二级页面无占位符：无偏移（让 MiniPlayer 回到正常位置）
                }
                val bottomBarTranslationY by animateDpAsState(
                    targetValue = targetTranslationY,
                    animationSpec = tween(durationMillis = 200),
                    label = "bottomBarTranslationY",
                    finishedListener = {
                        // 动画完成后，如果是二级页面，隐藏占位符
                        if (!showBottomBar) {
                            showSpacer = false
                            // 占位符隐藏后，targetTranslationY 会自动变为 0dp
                            // animateDpAsState 会触发新的动画，让 MiniPlayer 平滑回到正常位置
                        }
                    })
                // 监听路由变化，返回一级页面时恢复占位符
                LaunchedEffect(showBottomBar) {
                    if (showBottomBar) {
                        showSpacer = true  // 返回一级页面时恢复占位符
                    }
                }
                Column(
                    modifier = Modifier.graphicsLayer {
                        translationY = bottomBarTranslationY.toPx()  // 使用 graphicsLayer,不影响布局
                    }) {
                    // 迷你播放器 - 始终显示 (仅在播放器页面时隐藏,无动画)
                    // 优化 (2025-11-29): 始终显示 MiniPlayer,解决占位符问题
                    // - 无歌曲时: 显示默认状态 (slogan + 默认封面)
                    // - 有歌曲时: 显示歌曲信息
                    if (!showPlayerOverlay) {
                        MiniPlayer(
                            viewModel = sharedPlayerViewModel,
                            cachedMusicViewModel = sharedCachedMusicViewModel,
                            onClick = {
                                navController.navigate("player") {
                                    launchSingleTop = true
                                }
                            })
                    }
                    // 底部导航栏 - 智能占位符策略
                    // 修复 (2025-11-29): 播放器浮层打开时隐藏底部导航栏，避免闪现
                    if (showBottomBar && !showPlayerOverlay) {
                        BottomNavigationBar(
                            navController = navController, onDiscoverDoubleClick = {
                                discoverRefreshTrigger = System.currentTimeMillis()
                            })
                    } else {
                        // 二级页面 或 播放器浮层打开时: 智能占位符
                        // - 动画进行中: 显示占位符保持高度 (避免 Scaffold padding 动画)
                        // - 动画完成后: 隐藏占位符让内容完全可见
                        if (showSpacer) {
                            Spacer(modifier = Modifier.height(56.dp))
                        }
                    }
                }
            }) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues),
                // Material3 1.3.1 修复：完全禁用所有导航动画
                // 新版本引入了预测性返回动画，需要同时禁用 enter/exit/popEnter/popExit
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }) {
                composable("home") {
                    HomeScreen(
                        navController = navController,
                        playerViewModel = sharedPlayerViewModel,
                        homeViewModel = sharedHomeViewModel,  // 优化 (2025-11-23): 使用共享 ViewModel
                        cachedMusicViewModel = sharedCachedMusicViewModel  // 修复 (2025-11-25): 传入共享实例
                    )
                }
                composable("discover") {
                    DiscoverScreen(
                        navController = navController,
                        refreshTrigger = discoverRefreshTrigger,
                        playerViewModel = sharedPlayerViewModel
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        navController = navController,
                        cachedMusicViewModel = sharedCachedMusicViewModel  // 修复 (2025-11-25): 传入共享实例
                    )
                }
                // 播放器路由：空占位，真正的播放器在外层浮层显示
                // 此路由的作用是拦截 navigate("player") 调用，触发浮层显示
                composable("player") {
                    // 空占位 - LaunchedEffect 会拦截此路由并显示浮层
                }
                composable(
                    route = "search?artistName={artistName}", arguments = listOf(
                        navArgument("artistName") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                ) { backStackEntry ->
                    val artistName = backStackEntry.arguments?.getString("artistName")
                    SearchScreen(
                        navController = navController,
                        initialArtistName = artistName,
                        isPlayerOverlayVisible = showPlayerOverlay,  // 修复 (2025-11-18): 传递播放器浮层状态
                        playerViewModel = sharedPlayerViewModel
                    )
                }
                composable("cached_music") {
                    // 2025-11-14：已缓存音乐页面
                    CachedMusicScreen(
                        navController = navController,
                        playerViewModel = sharedPlayerViewModel,
                        viewModel = sharedCachedMusicViewModel  // 优化 (2025-11-23): 使用共享 ViewModel，消除闪烁
                    )
                }
                composable("favorites") {
                    // 使用screens包中的新版FavoriteSongsScreen（网易云风格）
                    FavoriteSongsScreen(
                        navController = navController,
                        playerViewModel = sharedPlayerViewModel,
                        homeViewModel = sharedHomeViewModel,  // 优化 (2025-11-23): 使用共享 ViewModel，消除闪烁
                        cachedMusicViewModel = sharedCachedMusicViewModel  // 修复 (2025-11-25): 传入共享实例
                    )
                }
                composable(route = "favorite_playlists") {
                    // 收藏歌单页面
                    MyFavoritePlaylistsScreen(
                        navController = navController,
                        homeViewModel = sharedHomeViewModel  // 优化 (2025-11-23): 使用共享 ViewModel
                    )
                }
                composable(
                    route = "playlist_detail/{playlistId}/{playlistName}/{source}/{cover}/{playCount}/{description}",
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.LongType },
                        navArgument("playlistName") { type = NavType.StringType },
                        navArgument("source") {
                            type = NavType.StringType
                            defaultValue = "playlist"
                        },
                        navArgument("cover") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("playCount") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                        navArgument("description") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        })
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0
                    val playlistName = backStackEntry.arguments?.getString("playlistName") ?: ""
                    val sourceStr = backStackEntry.arguments?.getString("source") ?: "playlist"
                    val cover = backStackEntry.arguments?.getString("cover") ?: ""
                    val playCount = backStackEntry.arguments?.getLong("playCount") ?: 0L
                    val description = backStackEntry.arguments?.getString("description")
                    val source = if (sourceStr == "artist") {
                        com.minimalistmusic.domain.model.PlaylistSource.ARTIST_PLAYLIST
                    } else {
                        com.minimalistmusic.domain.model.PlaylistSource.GENERATED_PLAYLIST
                    }
                    // 重构 (2025-11-27): 使用统一的 DetailScreen
                    DetailScreen(
                        playlistId = playlistId,
                        playlistName = playlistName,
                        source = source,
                        cover = cover,
                        playCount = playCount,
                        description = description,
                        navController = navController,
                        playerViewModel = sharedPlayerViewModel,
                        cachedMusicViewModel = sharedCachedMusicViewModel
                    )
                }
                composable("login") {
                    LoginScreen(navController)
                }
                composable(
                    route = "artist_detail/{artistName}", arguments = listOf(
                        navArgument("artistName") {
                            type = NavType.StringType
                        })
                ) { backStackEntry ->
                    val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                    // 重构 (2025-11-27): 使用统一的 DetailScreen
                    DetailScreen(
                        playlistId = 0L,  // 歌手详情不需要 ID
                        playlistName = artistName,
                        source = com.minimalistmusic.domain.model.PlaylistSource.ARTIST_PLAYLIST,
                        cover = "",
                        playCount = 0L,
                        description = null,
                        navController = navController,
                        playerViewModel = sharedPlayerViewModel,
                        cachedMusicViewModel = sharedCachedMusicViewModel
                    )
                }
                // Settings路由已移除 (2025-11-13) 设置功能已整合到ProfileScreen
            }
        }
        // ========== 播放器浮层 (2025-11-18) ==========
        // 播放器作为浮层渲染在主内容之上,支持拖拽时看到下层页面
        if (showPlayerOverlay) {
            PlayerScreen(
                navController = navController,
                onDismiss = closePlayer,
                viewModel = sharedPlayerViewModel
            )
        }
    }
    // ========== 同步提醒对话框 ==========
    SyncReminderDialogHost(
        accountSyncViewModel = accountSyncViewModel,
        navController = navController,
        onClosePlayer = closePlayer  // 修复 (2025-11-20): 传递关闭播放器浮层的回调
    )
}
/**
 * 迷你播放器
 *
 * 优化 (2025-11-29): 始终显示,支持默认状态
 * - 无歌曲: 单行居中显示 slogan "让音乐回归纯粹" (不可点击跳转)
 * - 有歌曲: 显示歌曲信息,带淡入淡出过渡动画 (可点击跳转到播放器页面)
 * - 过渡效果: 使用 Crossfade 实现默认状态 ↔ 歌曲状态的丝滑切换
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    cachedMusicViewModel: CachedMusicViewModel,  // 修复 (2025-11-25): 添加参数,避免重复实例化
    onClick: () -> Unit,
    ) {
    val playbackState by viewModel.playbackState.collectAsState()
    val currentSong = playbackState.currentSong
    var showPlaylist by remember { mutableStateOf(false) }
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsState()
    // 优化 (2025-11-29): CrossFade 淡入淡出动画,实现丝滑过渡
    // 当 currentSong 从 null → Song 或 Song → 其他 Song 时,内容平滑切换
    Crossfade(
        targetState = currentSong,
        animationSpec = tween(durationMillis = 300),
        label = "miniPlayerCrossfade"
    ) { song ->
        // MD3优化 (2025-11-24): 点击区域覆盖整个迷你播放器，符合MD3触摸目标规范
        // 优化 (2025-11-29): 默认状态下不跳转到播放器页面
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = {
                    if (song != null) {  // 只有加载歌曲后才允许跳转
                        onClick()
                    }
                }), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (song != null) {
                    // ========== 有歌曲: 显示歌曲信息 ==========
                    val isFavorite = favoriteSongIds.contains(song.id)
                    // 封面
                    SubcomposeAsyncImage(
                        model = song.albumArt,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        val state = painter.state
                        if (state is coil.compose.AsyncImagePainter.State.Success) {
                            // 加载成功：显示图片
                            SubcomposeAsyncImageContent()
                        } else {
                            // 加载中/失败/无封面：显示占位符
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // 歌曲信息
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 播放按钮
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停"
                        )
                    }
                    // 爱心按钮（2025-11-25：添加爱心收藏动画）
                    var previousMiniPlayerFavorite by remember(song.id) { mutableStateOf(isFavorite) }
                    var shouldAnimateMiniPlayerFavorite by remember(song.id) { mutableStateOf(false) }
                    LaunchedEffect(isFavorite) {
                        val justFavorite = !previousMiniPlayerFavorite && isFavorite
                        shouldAnimateMiniPlayerFavorite = justFavorite
                        previousMiniPlayerFavorite = isFavorite
                        if (justFavorite) {
                            kotlinx.coroutines.delay(400)
                            shouldAnimateMiniPlayerFavorite = false
                        }
                    }
                    val miniPlayerHeartScale by animateFloatAsState(
                        targetValue = if (shouldAnimateMiniPlayerFavorite) 1.5f else 1.0f,
                        animationSpec = if (shouldAnimateMiniPlayerFavorite) {
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        } else {
                            spring(stiffness = Spring.StiffnessHigh)
                        },
                        label = "miniPlayerHeartScale"
                    )
                    IconButton(
                        onClick = {
                            accountSyncViewModel.toggleFavorite(song)
                        }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                            tint = if (isFavorite) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                scaleX = miniPlayerHeartScale
                                scaleY = miniPlayerHeartScale
                            })
                    }
                    // 播放列表按钮
                    IconButton(onClick = { showPlaylist = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放列表")
                    }
                } else {
                    // ========== 无歌曲: 显示默认状态 (2025-11-29优化: 单行居中) ==========
                    // 默认封面
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Slogan: 单行居中显示
                    Box(
                        modifier = Modifier.weight(1f), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "让音乐回归纯粹",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                    }
                    // 占位按钮 (保持布局一致)
                    Spacer(modifier = Modifier.width(40.dp))
                    Spacer(modifier = Modifier.width(40.dp))
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }
        }
    }
    // 播放列表底部抽屉
    if (showPlaylist) {
        EnhancedPlaylistBottomSheet(
            playlist = playbackState.playlist,
            currentIndex = playbackState.currentIndex,
            currentPlayMode = playbackState.playMode,
            onDismiss = { showPlaylist = false },
            onSongClick = { index ->
                viewModel.seekToSong(index)
            },
            onPlayModeToggle = {
                viewModel.togglePlayMode()
            },
            isPlaying = playbackState.isPlaying,  // 传递实际播放状态
            currentSongId = playbackState.currentSong?.id,  // 修复 (2025-11-21): 传递当前歌曲ID用于准确判断和滚动
            cachedMusicViewModel = cachedMusicViewModel  // 修复 (2025-11-25): 传入共享实例,避免重复实例化
        )
    }
}
/**
 * 底部导航栏 - 完全自定义实现
 *
 * Bug修复 (2025-11-15):
 * Material 3 NavigationBarItem 内置椭圆形指示器无法完全移除
 * 采用完全自定义的导航栏，彻底避免黑色椭圆形背景
 *
 * @param navController 导航控制器
 * @param onDiscoverDoubleClick 双击发现Tab时的回调（用于刷新歌单）
 */
@Composable
fun BottomNavigationBar(
    navController: NavHostController, onDiscoverDoubleClick: () -> Unit = {},
) {
    val items = listOf(
        BottomNavItem("home", "首页", Icons.Outlined.Home, Icons.Filled.Home),
        BottomNavItem("discover", "发现", Icons.Outlined.Explore, Icons.Filled.Explore),
        BottomNavItem("profile", "我的", Icons.Outlined.Person, Icons.Filled.Person)
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // 双击检测状态 (2025-11-19)
    var lastDiscoverClickTime by remember { mutableLongStateOf(0L) }
    val doubleClickThreshold = 300L  // 300ms内的两次点击视为双击
    // 自定义导航栏容器 - 与微信导航栏尺寸完全一致
    // 参考微信规格：
    // - 总高度: 56dp (标准 Android 底部导航栏)
    // - 图标尺寸: 24dp
    // - 文字大小: 10sp
    // - 图标文字间距: 2dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),  // 微信导航栏高度：56dp
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                // 自定义导航项 - 完全没有背景
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                // 双击发现Tab检测 (2025-11-19)
                                if (item.route == "discover" && currentRoute == "discover") {
                                    if (currentTime - lastDiscoverClickTime < doubleClickThreshold) {
                                        // 双击：触发刷新回调
                                        onDiscoverDoubleClick()
                                        lastDiscoverClickTime = 0L  // 重置，避免连续触发
                                    } else {
                                        lastDiscoverClickTime = currentTime
                                    }
                                    return@clickable  // 已经在发现页，不需要导航
                                }
                                // 正常导航
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }, indication = null,  // 移除点击涟漪效果
                            interactionSource = remember { MutableInteractionSource() })
                        .padding(top = 8.dp, bottom = 8.dp),  // 上下padding保持与微信一致
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 图标 - 与微信相同尺寸
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary  // 选中：紫色
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)  // 未选中：灰色半透明
                        },
                        modifier = Modifier.size(24.dp)  // 微信图标尺寸：24dp
                    )
                    Spacer(modifier = Modifier.height(2.dp))  // 微信图标文字间距：2dp
                    // 文字标签 - 与微信相同字体大小
                    Text(
                        text = item.label, fontSize = 10.sp,  // 微信文字大小：10sp
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary  // 选中：紫色
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)  // 未选中：灰色半透明
                        }, maxLines = 1
                    )
                }
            }
        }
    }
}
data class BottomNavItem(
    val route: String,
    val label: String,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)
