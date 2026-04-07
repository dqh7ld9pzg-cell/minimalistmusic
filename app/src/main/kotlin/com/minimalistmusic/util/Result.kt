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

package com.minimalistmusic.util

/**
 * 数据结果封装
 *
 * 用于统一处理数据层返回的结果，包括成功、错误和加载状态
 */
sealed class Result<out T> {
    /**
     * 成功状态
     * @param data 返回的数据
     */
    data class Success<T>(val data: T) : Result<T>()
    /**
     * 错误状态
     * @param exception 异常信息
     */
    data class Error(val exception: Exception) : Result<Nothing>()
    /**
     * 加载中状态
     */
    object Loading : Result<Nothing>()
}
