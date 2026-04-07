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
 * 登录页面
 *
 * 提供手机号验证码登录功能，是用户账号系统的入口。
 *
 * ## 核心功能
 *
 * ### 1. 手机号验证码登录
 * - 输入手机号（支持+86区号）
 * - 发送验证码（60秒倒计时）
 * - 输入验证码完成登录
 * - 首次登录自动注册
 *
 * ### 2. 登录流程
 * ```
 * 1. 输入手机号
 * 2. 点击"发送验证码" → 倒计时60秒
 * 3. 输入收到的验证码
 * 4. 点击"登录" → 验证通过
 * 5. 自动返回上一页面
 * 6. 触发账号同步（我喜欢、播放历史）
 * ```
 *
 * ### 3. 状态管理
 * - [phone]: 手机号
 * - [code]: 验证码
 * - [isCodeSent]: 验证码是否已发送
 * - [isLoading]: 加载状态
 * - [errorMessage]: 错误信息
 *
 * ### 4. 表单验证
 * - 手机号格式验证（11位数字）
 * - 验证码格式验证（6位数字）
 * - 发送验证码冷却时间（60秒）
 *
 * ## UI结构
 * ```
 * TopAppBar (返回按钮)
 *
 * Logo图标 (64dp)
 *
 * 表单区域
 * ├── 手机号输入框
 * ├── 验证码输入框
 * ├── 发送验证码按钮（带倒计时）
 * └── 登录按钮
 *
 * 提示信息
 * └── "登录即代表同意用户协议和隐私政策"
 * ```
 *
 * ## 交互细节
 * - 手机号输入框：数字键盘，最多11位
 * - 验证码输入框：数字键盘，最多6位
 * - 发送验证码按钮：倒计时期间禁用
 * - 登录按钮：表单未填完整时禁用
 * - 错误提示：红色文字显示，3秒后自动消失
 *
 * ## 登录成功后
 * 1. 保存用户Token到本地
 * 2. 更新登录状态
 * 3. 同步云端数据（我喜欢、播放历史）
 * 4. 导航返回上一页面
 * 5. 上一页面刷新用户信息
 *
 * @param navController 导航控制器
 * @param viewModel 登录ViewModel，处理登录逻辑
 *
 * @since 2025-11-11
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.minimalistmusic.presentation.viewmodel.LoginViewModel
import com.minimalistmusic.ui.components.ImmersiveTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isCodeSent by viewModel.isCodeSent.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = { Text("手机号登录") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            // Logo或标题
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "欢迎使用 极简音乐",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 登录介绍文本 (2025-11-19优化：补充缓存加速信息)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "使用手机号登录，即可同步您的音乐数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "登录后可开启快速缓存功能，加速歌曲缓存",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            // 手机号输入
            OutlinedTextField(
                value = phone,
                onValueChange = { viewModel.updatePhone(it) },
                label = { Text("手机号") },
                placeholder = { Text("请输入11位手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 验证码输入
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { viewModel.updateCode(it) },
                    label = { Text("验证码") },
                    placeholder = { Text("请输入验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
                Button(
                    onClick = { viewModel.sendVerificationCode() },
                    enabled = !isLoading && phone.length == 11 && !isCodeSent,
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 100.dp)
                ) {
                    Text(if (isCodeSent) "已发送" else "获取验证码")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 提示信息
            if (isCodeSent) {
                Text(
                    text = "验证码已发送（简化版：任意6位数字即可）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            // 错误信息
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 登录按钮
            Button(
                onClick = {
                    viewModel.login(
                        onSuccess = {
                            // 设置登录成功标志，通知 HomeScreen 刷新状态
                            navController.previousBackStackEntry?.savedStateHandle?.set("login_success", true)
                            navController.navigateUp()
                        }
                    )
                },
                enabled = !isLoading && phone.length == 11 && code.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "登录",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // 使用说明
            Text(
                text = "登录即表示同意服务协议和隐私政策\n简化版：验证码为任意6位数字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
