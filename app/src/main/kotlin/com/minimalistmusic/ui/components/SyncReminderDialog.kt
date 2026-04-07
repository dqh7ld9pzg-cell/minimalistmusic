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

package com.minimalistmusic.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.LoginViewModel
/**
 * 同步提醒对话框管理器 - 完整版，包含 pendingSong 处理逻辑
 *
 * 使用示例：
 * ```
 * SyncReminderDialogHost(
 *     accountSyncViewModel = accountSyncViewModel,
 *     navController = navController,
 *     onClosePlayer = { showPlayerOverlay = false }  // 可选：关闭播放器浮层
 * )
 * ```
 *
 * 功能：
 * 1. 显示同步提醒对话框
 * 2. 监听登录成功，自动刷新登录状态
 * 3. 处理登录后的 pendingSong（添加到喜欢并同步）
 *
 * 修复 (2025-11-20):
 * - 新增 onClosePlayer 参数，用于关闭播放器浮层
 * - 点击"去登录"时先关闭播放器，再导航到登录页面
 */
@Composable
fun SyncReminderDialogHost(
    accountSyncViewModel: AccountSyncViewModel,
    navController: NavHostController,
    loginViewModel: LoginViewModel = hiltViewModel(),
    onClosePlayer: (() -> Unit)? = null  // 新增 (2025-11-20): 关闭播放器浮层回调
) {
    val showSyncReminder by accountSyncViewModel.showSyncReminder.collectAsState()
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsState()
    val pendingSong by accountSyncViewModel.pendingSong.collectAsState()
    // 监听登录成功标志，刷新登录状态
    val loginSuccess = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("login_success", false)
        ?.collectAsState()
    LaunchedEffect(loginSuccess?.value) {
        if (loginSuccess?.value == true) {
            loginViewModel.loadUserInfo()
            navController.currentBackStackEntry?.savedStateHandle?.set("login_success", false)
        }
    }
    LaunchedEffect(isLoggedIn, pendingSong) {
        // 当用户登录成功且有 pending 歌曲时，处理该歌曲
        if (isLoggedIn && pendingSong != null) {
            accountSyncViewModel.processPendingSongAfterLogin()
        }
    }
    if (showSyncReminder) {
        SyncReminderDialog(
            onDismiss = { accountSyncViewModel.dismissSyncReminder() },
            onLogin = {
                // 优化 (2025-11-20): 简化逻辑，移除dontShowAgain参数
                accountSyncViewModel.navigateToLogin()
                // 先关闭播放器浮层，再导航到登录页面
                onClosePlayer?.invoke()
                navController.navigate("login")
            }
        )
    }
}
/**
 * 同步提醒对话框 - 底层组件
 *
 * 优化 (2025-11-20): 简化对话框
 * - 移除复选框和"仅保存本地"按钮
 * - 只显示"去登录"和"取消"按钮
 * - 爱心操作已在弹出前执行，这里只是提醒用户登录可同步
 */
@Composable
private fun SyncReminderDialog(
    onDismiss: () -> Unit,
    onLogin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同步到云端") },
        text = {
            Column {
                Text("已添加到我喜欢!")
                Spacer(modifier = Modifier.height(8.dp))
                Text("您还未登录，喜欢的音乐仅保存在本地。")
                Spacer(modifier = Modifier.height(8.dp))
                Text("登录后可将'我喜欢'同步至云端，换设备也能听!")
            }
        },
        confirmButton = {
            TextButton(onClick = onLogin) {
                Text("去登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}