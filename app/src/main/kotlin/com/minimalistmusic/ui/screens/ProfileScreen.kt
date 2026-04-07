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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.theme.Spacing
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.ProfileViewModel

/**
 * 个人中心页面
 *
 * 重构 (2025-11-13):
 * - 移除：我喜欢的音乐、播放历史、本地音乐入口（不需要单独入口）
 * - 整合：设置页面的所有功能（账号信息、云端同步、存储管理、退出登录）
 * - 简化：用户信息 + 设置功能一体化
 *
 * 功能：
 * - 用户信息展示（已登录/游客）
 * - 云端同步设置（我喜欢、播放历史）
 * - 存储管理（缓存大小、清空缓存）
 * - 退出登录
 * - 关于信息
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = hiltViewModel(), // 复用 ProfileViewModel
    homeViewModel: com.minimalistmusic.presentation.viewmodel.HomeViewModel = hiltViewModel(),// 获取本地音乐数量 (2025-11-14)
    cachedMusicViewModel: CachedMusicViewModel = hiltViewModel()
) {
    // 状态收集
    val userPhone by viewModel.userPhone.collectAsStateWithLifecycle()
    val syncFavorites by viewModel.syncFavorites.collectAsStateWithLifecycle()
    val syncHistory by viewModel.syncHistory.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val isLoadingCache by viewModel.isLoadingCache.collectAsStateWithLifecycle()
    val cacheSizeMB by viewModel.cacheSizeMB.collectAsStateWithLifecycle()
    val fastCacheOnWiFi by viewModel.fastCacheOnWiFi.collectAsStateWithLifecycle() // WiFi快速缓存设置 (2025-11-19)
    val fastCacheOnMobile by viewModel.fastCacheOnMobile.collectAsStateWithLifecycle() // 4/5G快速缓存设置 (2025-11-19)
    val maxCachedSongs by viewModel.maxCachedSongs.collectAsStateWithLifecycle() // 最大缓存歌曲数 (2025-11-21)
    val cacheEnabled by viewModel.cacheEnabled.collectAsStateWithLifecycle() // 缓存开关状态 (2025-11-23)
    val debugModeEnabled by viewModel.debugModeEnabled.collectAsStateWithLifecycle() // 调试模式状态 (2025-11-30)
    val cacheEventLogEnabled by viewModel.cacheEventLogEnabled.collectAsStateWithLifecycle() // 缓存事件日志状态 (2025-11-30)
    // 音乐数量状态 (2025-11-14)
    val cachedSongCount by cachedMusicViewModel.cachedSongCount.collectAsStateWithLifecycle()
    val localSongs by homeViewModel.localSongs.collectAsStateWithLifecycle()
    // 对话框状态
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }  // 设置对话框 (2025-11-15)
    var showClearCacheDialog by remember { mutableStateOf(false) }  // 清空缓存确认对话框 (2025-11-25)
    // 调试模式触发计数 (2025-11-30)
    var debugClickCount by remember { mutableStateOf(0) }
    // 最大缓存歌曲数输入 (2025-11-21)
    var maxCachedSongsInput by remember { mutableStateOf(maxCachedSongs.toString()) }
    // 同步最大缓存歌曲数到输入框
    LaunchedEffect(maxCachedSongs) {
        maxCachedSongsInput = maxCachedSongs.toString()
    }
    // 缓存歌曲数量状态管理
    val cachedSongs by cachedMusicViewModel.cachedSongs.collectAsStateWithLifecycle()
    // 判断是否登录
    val isLoggedIn = !userPhone.isNullOrEmpty()
    // Toast上下文 (2025-11-20)
    val context = LocalContext.current
    // 焦点管理器 (2025-11-20): 用于保存缓存上限后清除输入框焦点
    val focusManager = LocalFocusManager.current
    // 监听ProfileScreen可见时刷新音乐数量 (移除 2025-11-15)
    // 原因：cachedSongCount 现在是响应式的，从 CacheStateManager 自动同步，无需手动刷新
    // 架构优化：单一数据源（SSOT）原则，避免重复刷新逻辑
    // 监听登录成功标志，刷新用户信息 (2025-11-14修复)
    // 修复原因：登录成功后ProfileScreen没有刷新，导致仍显示"游客"
    val loginSuccess =
        navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("login_success", false)
            ?.collectAsStateWithLifecycle()
    LaunchedEffect(loginSuccess?.value) {
        if (loginSuccess?.value == true) {
            // 刷新用户信息
            viewModel.refreshUserInfo()
            // 重置标志
            navController.currentBackStackEntry?.savedStateHandle?.set("login_success", false)
        }
    }
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = { Text("我的", fontWeight = FontWeight.Bold) },
                // 右上角设置图标 (2025-11-15)
                // - 未登录：点击跳转登录页面
                // - 已登录：点击显示设置对话框（云端同步开关）
                actions = {
                    IconButton(
                        onClick = {
                            if (isLoggedIn) {
                                showSettingsDialog = true
                            } else {
                                navController.navigate("login")
                            }
                        }) {
                        Icon(
                            Icons.Filled.Settings, contentDescription = "设置"
                        )
                    }
                })
        }) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+20dp垂直 (2025-11-28)
            verticalArrangement = Arrangement.spacedBy(Spacing.Content.extraLarge)  // MD3规范：20dp模块间距
        ) {
            // ========== 用户信息卡片（优化 2025-11-29）==========
            // MD3设计优化：使用更淡的背景色，与首页/发现页保持清新一致
            // - surfaceVariant + 降低透明度：比其他页面更淡，减轻视觉压力
            // - tonalElevation 0.5dp：更轻盈的色调提升
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoggedIn) {
                            navController.navigate("login")
                        },
                    shape = RoundedCornerShape(12.dp),  // 12dp圆角，与其他页面一致
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),  // 更淡的背景色（50%透明度）
                    tonalElevation = 0.5.dp  // 更轻盈的色调提升
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),  // 增加内边距，提升呼吸感
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 头像（增大尺寸，更醒目）
                        Surface(
                            modifier = Modifier.size(72.dp),  // 从60dp增大到72dp
                            shape = MaterialTheme.shapes.large,  // 使用large圆角
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),  // 图标也相应增大
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))  // 增加间距
                        // 用户信息
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isLoggedIn) userPhone ?: "用户" else "游客",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp  // 稍微增大字号
                            )
                            Text(
                                text = if (isLoggedIn) "已登录" else "点击登录，享受更多功能",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isLoggedIn) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.6f
                                )
                                else MaterialTheme.colorScheme.primary  // 未登录时使用主题色引导
                            )
                        }
                    }
                }
            }
            // ========== 功能设置分组标题（优化 2025-11-23）==========
            item {
                Text(
                    text = "功能设置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            // ========== 缓存功能卡片（优化 2025-11-29）==========
            // MD3设计优化：使用更淡的背景色，与首页/发现页保持清新一致
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),  // 更淡的背景色
                    tonalElevation = 0.5.dp  // 更轻盈的色调提升
                ) {
                    // MD3优化 (2025-11-24): 移除Column的spacedBy，使用精确间距控制
                    // 原因：spacedBy会在所有子元素间自动添加间距，导致无法精确控制各部分的视觉层次
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)  // 与头像区域padding对齐
                    ) {
                        // 缓存开关行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // 缓存图标
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (cacheEnabled) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.CloudDone,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = if (cacheEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "智能缓存",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (cacheEnabled) "已启用" else "已关闭",
                                        style = MaterialTheme.typography.bodySmall,  // MD3优化 (2025-11-24): 从bodyMedium改为bodySmall，辅助状态文字应该更小
                                        color = if (cacheEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Switch(
                                checked = cacheEnabled,
                                onCheckedChange = { viewModel.toggleCache(it) })
                        }
                        // 缓存开启时显示详细信息
                        if (cacheEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))  // MD3: 分割线前间距
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))  // MD3: 分割线后间距
                            // 缓存统计信息
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // MD3优化 (2025-11-24): 添加垂直间距4.dp，标签与数值之间更清晰
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "已缓存歌曲",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "$cachedSongCount 首 / $maxCachedSongs 首",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                // MD3优化 (2025-11-24): 添加垂直间距4.dp，标签与数值之间更清晰
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "占用空间",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = if (isLoadingCache) "计算中..." else cacheSize,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))  // MD3: 统计与价值描述间距
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)  // ← 中性灰图标
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "再次播放不消耗流量·支持断网播放",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
//                            // 核心价值说明
//                            Text(
//                                text = "再次播放不消耗流量·支持断网播放",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
//                                modifier = Modifier.fillMaxWidth()
//                            )
                            // 缓存加速设置（新增 2025-12-01：移到主页面，符合MD3就近原则）
                            if (cacheEnabled) {
                                Spacer(modifier = Modifier.height(16.dp))  // MD3: Section间距（统计信息与加速设置之间）
                                // 缓存加速标题（MD3 设计规范：次要标题突出显示）
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(16.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(50)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))  // MD3优化 (2025-11-24): 从10.dp改为12.dp，符合8dp基准
                                    Text(
                                        text = "网络加速缓存",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // 快速缓存策略说明
                                Text(
                                    text = "快速缓存策略",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                // WiFi快速缓存开关
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "WiFi 高速缓存",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "WiFi环境下使用高速模式",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Switch(
                                        checked = fastCacheOnWiFi,
                                        onCheckedChange = { viewModel.toggleFastCacheOnWiFi(it) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // 移动网络快速缓存开关
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "移动网络高速缓存",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "移动网络下使用高速模式",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Switch(
                                        checked = fastCacheOnMobile,
                                        onCheckedChange = { viewModel.toggleFastCacheOnMobile(it) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))  // MD3: Section间距（优化策略与操作按钮之间）
                            // 操作按钮行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var showEditDialog by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("调整上限")
                                }
                                OutlinedButton(
                                    onClick = {
                                        showClearCacheDialog = true  // 显示确认对话框 (2025-11-25)
                                    },
                                    enabled = !isLoadingCache && cacheSize != "0 MB" && cacheSize != "0 B",
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("清空缓存")
                                }
                                // 编辑缓存上限对话框
                                if (showEditDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showEditDialog = false },
                                        title = { Text("调整缓存上限") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = maxCachedSongsInput,
                                                    onValueChange = { input ->
                                                        if (input.all { it.isDigit() } || input.isEmpty()) {
                                                            maxCachedSongsInput = input
                                                        }
                                                    },
                                                    label = { Text("最大缓存歌曲数") },
                                                    placeholder = { Text("${viewModel.getDefaultMaxCachedSongs()}") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    isError = run {
                                                        val count =
                                                            maxCachedSongsInput.toIntOrNull()
                                                        count != null && (count < UserPreferencesDataStore.MIN_CACHED_SONGS || count > viewModel.getMaxAllowedCachedSongs())
                                                    },
                                                    supportingText = {
                                                        val count =
                                                            maxCachedSongsInput.toIntOrNull()
                                                        if (count != null && (count < UserPreferencesDataStore.MIN_CACHED_SONGS || count > viewModel.getMaxAllowedCachedSongs())) {
                                                            Text(
                                                                "请输入 ${UserPreferencesDataStore.MIN_CACHED_SONGS}-${viewModel.getMaxAllowedCachedSongs()} 之间的数值",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    })
                                                Text(
                                                    text = "默认设定缓存 ${viewModel.getDefaultMaxCachedSongs()} 首歌曲",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "超限自动清理最久未播放的歌曲（白名单除外）",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "修改后立即生效",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                val count = maxCachedSongsInput.toIntOrNull()
                                                val maxAllowed =
                                                    viewModel.getMaxAllowedCachedSongs()
                                                if (count != null && count in UserPreferencesDataStore.MIN_CACHED_SONGS..maxAllowed) {
                                                    viewModel.updateMaxCachedSongs(count)
                                                    focusManager.clearFocus()
                                                    showEditDialog = false
                                                    Toast.makeText(
                                                        context,
                                                        "缓存上限已设置为 $count 首",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }, enabled = run {
                                                val count = maxCachedSongsInput.toIntOrNull()
                                                val maxAllowed =
                                                    viewModel.getMaxAllowedCachedSongs()
                                                count != null && count in UserPreferencesDataStore.MIN_CACHED_SONGS..maxAllowed && count != maxCachedSongs
                                            }) {
                                                Text("保存")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = {
                                                showEditDialog = false
                                                maxCachedSongsInput = maxCachedSongs.toString()
                                            }) {
                                                Text("取消")
                                            }
                                        })
                                }
                                // 清空缓存确认对话框 (2025-11-25)
                                if (showClearCacheDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showClearCacheDialog = false },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "警告",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        title = { Text("清空缓存") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = "确认清空所有缓存的音乐文件？",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = "当前缓存: $cacheSize",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "清空后下次播放将重新缓存",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    viewModel.clearCache()
                                                    showClearCacheDialog = false
                                                    Toast.makeText(
                                                        context,
                                                        "正在清空缓存...",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }) {
                                                Text("确认清空")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showClearCacheDialog = false }) {
                                                Text("取消")
                                            }
                                        })
                                }
                            }
//                            Spacer(modifier = Modifier.height(12.dp))  // MD3: 按钮与说明间距
//
//                            // 缓存说明
//                            Text(
//                                text = "边播边存不额外消耗流量，再次播放完全不消耗流量",
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
//                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))  // MD3: 开关与说明间距
                            // 缓存关闭时显示说明
                            // MD3优化 (2025-11-24): 说明文字与"智能缓存"文字左边缘对齐，而非与图标左边缘对齐
                            // 对齐原理：图标48.dp + 间距16.dp = 64.dp偏移
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.width(64.dp))  // 图标(48dp) + 间距(16dp)
                                Text(
                                    text = "常听歌曲免流量重播，支持离线播放",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            // ========== 已缓存音乐 ==========
            //重复展示入口，违背极简原则
//            item {
//                Surface(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { navController.navigate("cached_music") },
//                    shape = MaterialTheme.shapes.medium,
//                    tonalElevation = 1.dp
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(16.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Icon(
//                            Icons.Filled.CloudDone,
//                            contentDescription = null,
//                            tint = MaterialTheme.colorScheme.primary,
//                            modifier = Modifier.size(24.dp)
//                        )
//                        Spacer(modifier = Modifier.width(12.dp))
//                        Column(modifier = Modifier.weight(1f)) {
//                            Text(
//                                text = "已缓存音乐",
//                                style = MaterialTheme.typography.bodyLarge
//                            )
//                            Text(
//                                text = "$cachedSongCount 首",
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                            )
//                        }
//                    }
//                }
//            }
            // ========== 本地音乐 ==========
            // 缓存、本地过多概念可能让用户产生困惑，这里考虑的不是特别清楚要不要展示本地音乐，先隐藏该功能
//            item {
//                Surface(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { navController.navigate("local_songs") },
//                    shape = MaterialTheme.shapes.medium,
//                    tonalElevation = 1.dp
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(16.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Icon(
//                            Icons.Filled.MusicNote,
//                            contentDescription = null,
//                            tint = MaterialTheme.colorScheme.primary,
//                            modifier = Modifier.size(24.dp)
//                        )
//                        Spacer(modifier = Modifier.width(12.dp))
//                        Column(modifier = Modifier.weight(1f)) {
//                            Text(
//                                text = "本地音乐",
//                                style = MaterialTheme.typography.bodyLarge
//                            )
//                            Text(
//                                text = "${localSongs.size} 首",
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
//                            )
//                        }
//                    }
//                }
//            }
            // ========== 其他分组标题（新增 2025-11-20）==========
            item {
                Text(
                    text = "其他",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            // ========== 关于（优化 2025-11-29）==========
            // MD3设计优化：使用更淡的背景色，与首页/发现页保持清新一致
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),  // 更淡的背景色
                    tonalElevation = 0.5.dp  // 更轻盈的色调提升
                ) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .padding(20.dp),  // 增加内边距
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)  // 统一间距
                    ) {
                        // 关于图标
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "关于",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            // ========== 退出登录按钮（优化 2025-11-20）==========
            // MD3设计：增加间距，使用FilledTonalButton提升视觉层级
            if (isLoggedIn) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))  // 与上方内容增加间距
                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),  // 固定高度，符合MD3触摸目标
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "退出登录",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
    // ========== 对话框 ==========
    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？退出后云端同步功能将停止工作。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            })
    }
    // 关于对话框 (2025-11-30优化: 添加7次点击触发调试模式)
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = {
                showAboutDialog = false
                debugClickCount = 0  // 关闭对话框时重置计数
            },
            title = { Text("关于 极简音乐") },
            text = {
                Column(
                    modifier = Modifier
                        .clickable(
                            indication = null,  // 移除点击效果
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            debugClickCount++
                            if (debugClickCount >= 7) {
                                // 连续点击7次后,只能开启调试模式,不能关闭
                                if (!debugModeEnabled) {
                                    // 调试模式未开启,开启它
                                    viewModel.toggleDebugMode(true)
                                    Toast.makeText(
                                        context,
                                        "调试模式已开启",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // 调试模式已开启,只提示
                                    Toast.makeText(
                                        context,
                                        "调试模式已开启",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                debugClickCount = 0  // 重置计数
                            }
                        }
                ) {
                    Text("版本: 1.2.19")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "极简，是一种态度；聆听，是一种沉浸。",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "© 2025 极简科技 | 让音乐回归纯粹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAboutDialog = false
                    debugClickCount = 0  // 关闭对话框时重置计数
                }) {
                    Text("确定")
                }
            })
    }
    // 设置对话框（优化 2025-11-23）
    // 功能：云端同步开关、缓存高级设置
    if (showSettingsDialog) {
        // 滚动状态 (2025-12-04优化：内容过多时支持滚动)
        val settingsScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("高级设置") },
            text = {
                // 使用 Box 包裹以便更好地控制滚动区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)  // 增加最大高度到 600dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(settingsScrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    // ========== 云端同步设置 ==========
                    Text(
                        text = "云端同步",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    // 同步"我喜欢"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "同步我喜欢",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "自动同步收藏的音乐到云端",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = syncFavorites,
                            onCheckedChange = { viewModel.toggleSyncFavorites(it) })
                    }
                    // 同步"播放历史"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "同步播放历史",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "自动同步播放记录到云端",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = syncHistory,
                            onCheckedChange = { viewModel.toggleSyncHistory(it) })
                    }

                    // ========== 调试设置 (2025-11-30新增) ==========
                    // 只在调试模式开启时显示
                    if (debugModeEnabled) {
                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "调试设置",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,  // 使用error颜色强调这是调试功能
                            fontWeight = FontWeight.SemiBold
                        )

                        // 调试模式总开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "调试模式",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "关闭后所有调试开关将隐藏",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = debugModeEnabled,
                                onCheckedChange = { viewModel.toggleDebugMode(it) })
                        }

                        // LRU缓存事件日志开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "缓存事件日志",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "LRU缓存管理器的事件日志（添加/删除/淘汰）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = cacheEventLogEnabled,
                                onCheckedChange = { viewModel.toggleCacheEventLog(it) })
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 性能跟踪开关
                        val enablePerformanceTracking by viewModel.enableCachePerformanceTracking.collectAsStateWithLifecycle()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "缓存性能跟踪",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "记录每首歌曲的缓存耗时、速度等性能指标",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = enablePerformanceTracking,
                                onCheckedChange = { viewModel.togglePerformanceTracking(it) }
                            )
                        }

                        // 详细日志开关（仅在性能跟踪启用时显示）
                        if (enablePerformanceTracking) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val enableDetailedLogging by viewModel.enableCacheDetailedLogging.collectAsStateWithLifecycle()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "详细性能报告",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "输出完整性能报告（歌曲名、URL、文件大小、速度等）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Switch(
                                    checked = enableDetailedLogging,
                                    onCheckedChange = { viewModel.toggleDetailedLogging(it) }
                                )
                            }
                        }
                    }
                    }  // 结束 Column
                }  // 结束 Box
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("完成")
                }
            })
    }
}
