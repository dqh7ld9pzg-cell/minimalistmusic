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

import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import okio.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络异常统一定义
 *
 * 用于区分不同类型的网络错误，提供更友好的错误提示
 */
sealed class NetworkException(message: String) : IOException(message) {
    /**
     * API 服务端错误
     * @param code HTTP状态码
     * @param message 错误信息
     */
    data class ApiError(val code: Int, override val message: String) : NetworkException(message) {
        fun getUserMessage(): String = when (code) {
            400 -> "请求参数错误"
            401 -> "未授权，请重新登录"
            403 -> "无权访问该资源"
            404 -> "请求的资源不存在"
            500 -> "服务器内部错误，请稍后重试"
            502, 503 -> "服务暂时不可用，请稍后重试"
            else -> message
        }
    }
    /**
     * 网络连接错误
     */
    data class NetworkError(override val message: String) : NetworkException(message) {
        fun getUserMessage(): String = "网络连接失败，请检查网络设置"
    }
    /**
     * 数据解析错误
     */
    data class ParseError(override val message: String) : NetworkException(message) {
        fun getUserMessage(): String = "数据解析失败"
    }
    /**
     * 无网络连接
     */
    object NoInternetException : NetworkException("无网络连接") {
        fun getUserMessage(): String = "当前无网络连接，请检查网络设置"
    }
    /**
     * 请求超时
     */
    object TimeoutException : NetworkException("请求超时") {
        fun getUserMessage(): String = "请求超时，请稍后重试"
    }
    /**
     * 未知错误
     */
    data class UnknownError(override val message: String) : NetworkException(message) {
        fun getUserMessage(): String = "未知错误: $message"
    }
}
/**
 * 扩展函数：将通用异常转换为 NetworkException
 */
fun Throwable.toNetworkException(): NetworkException {
    return when (this) {
        is NetworkException -> this
        is UnknownHostException -> NetworkException.NoInternetException
        is SocketTimeoutException -> NetworkException.TimeoutException
        is ConnectException -> NetworkException.NetworkError("连接失败")
        is JsonSyntaxException,
        is JsonParseException -> NetworkException.ParseError(this.message ?: "JSON解析错误")
        else -> NetworkException.UnknownError(this.message ?: "未知错误")
    }
}
/**
 * 扩展函数：获取用户友好的错误信息
 */
fun Throwable.getUserFriendlyMessage(): String {
    return when (val exception = this.toNetworkException()) {
        is NetworkException.ApiError -> exception.getUserMessage()
        is NetworkException.NetworkError -> exception.getUserMessage()
        is NetworkException.ParseError -> exception.getUserMessage()
        is NetworkException.NoInternetException -> exception.getUserMessage()
        is NetworkException.TimeoutException -> exception.getUserMessage()
        is NetworkException.UnknownError -> exception.getUserMessage()
    }
}
