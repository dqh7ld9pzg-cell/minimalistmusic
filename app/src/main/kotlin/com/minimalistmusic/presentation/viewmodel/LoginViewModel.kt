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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.domain.repository.UserRepository
import com.minimalistmusic.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
/**
 * LoginViewModel - 用户登录和验证码管理
 *
 * 架构重构说明：
 * - 移除了 LoginUseCase 和 SendVerificationCodeUseCase
 * - 直接依赖 UserRepository，简化调用链
 * - 保持原有功能不变
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository, // 重构：直接依赖 Repository
    private val userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {
    private val _phone = MutableStateFlow("")
    val phone = _phone.asStateFlow()
    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _isCodeSent = MutableStateFlow(false)
    val isCodeSent = _isCodeSent.asStateFlow()
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()
    private val _userPhone = MutableStateFlow<String?>(null)
    val userPhone = _userPhone.asStateFlow()
    init {
        loadUserInfo()
    }

    fun loadUserInfo() {
        _isLoggedIn.value = userPreferencesDataStore.isLoggedIn.value
        _userPhone.value = userPreferencesDataStore.userPhone.value
    }
    fun updatePhone(value: String) {
        // 只允许输入数字，最多11位
        if (value.length <= 11 && value.all { it.isDigit() }) {
            _phone.value = value
        }
    }
    fun updateCode(value: String) {
        // 只允许输入数字，最多6位
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _code.value = value
        }
    }
    /**
     * 发送验证码
     *
     * 重构：直接调用 userRepository.sendVerificationCode()
     */
    fun sendVerificationCode() {
        if (_phone.value.length != 11) {
            _errorMessage.value = "请输入正确的手机号"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            // 重构：直接调用 Repository 方法
            when (val result = userRepository.sendVerificationCode(_phone.value)) {
                is Result.Success -> {
                    _isCodeSent.value = true
                }
                is Result.Error -> {
                    _errorMessage.value = result.exception.message ?: "发送验证码失败"
                }
                else -> {
                    _errorMessage.value = "发送验证码失败"
                }
            }
            _isLoading.value = false
        }
    }
    /**
     * 登录
     *
     * 重构：直接调用 userRepository.login()
     */
    fun login(onSuccess: () -> Unit) {
        if (_phone.value.length != 11) {
            _errorMessage.value = "请输入正确的手机号"
            return
        }
        if (_code.value.length != 6) {
            _errorMessage.value = "请输入6位验证码"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            // 重构：直接调用 Repository 方法
            when (val result = userRepository.login(_phone.value, _code.value)) {
                is Result.Success -> {
                    val loginData = result.data.data
                    if (loginData != null) {
                        // 保存登录信息
                        userPreferencesDataStore.login(loginData.phone, loginData.token)
                        // 刷新登录状态，确保UI及时更新
                        loadUserInfo()
                        _isLoading.value = false
                        onSuccess()
                    } else {
                        _errorMessage.value = "登录失败，请重试"
                        _isLoading.value = false
                    }
                }
                is Result.Error -> {
                    _errorMessage.value = result.exception.message ?: "登录失败"
                    _isLoading.value = false
                }
                else -> {
                    _errorMessage.value = "登录失败"
                    _isLoading.value = false
                }
            }
        }
    }
}
