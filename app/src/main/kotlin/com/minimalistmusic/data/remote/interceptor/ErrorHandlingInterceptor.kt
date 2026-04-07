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

package com.minimalistmusic.data.remote.interceptor

import com.minimalistmusic.util.NetworkException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
/**
 * 错误处理拦截器
 *
 * 统一处理 HTTP 错误，将错误码转换为 NetworkException
 */
class ErrorHandlingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 执行请求
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            // 网络连接异常
            throw when (e) {
                is java.net.UnknownHostException -> NetworkException.NoInternetException
                is java.net.SocketTimeoutException -> NetworkException.TimeoutException
                is java.net.ConnectException -> NetworkException.NetworkError("连接失败: ${e.message}")
                else -> NetworkException.UnknownError(e.message ?: "请求失败")
            }
        }
        // 检查 HTTP 状态码
        // Bug修复 (2025-11-15): 允许3xx重定向响应通过
        // 原因：FreeMusicAPI等接口使用302重定向返回真实URL，需要Repository层手动处理
        if (!response.isSuccessful && response.code !in 300..399) {
            val code = response.code
            val errorBody = response.body?.string() ?: "未知错误"
            throw NetworkException.ApiError(
                code = code,
                message = parseErrorMessage(code, errorBody)
            )
        }
        return response
    }
    /**
     * 解析错误信息
     * @param code HTTP状态码
     * @param _errorBody 错误响应体（保留供未来JSON解析使用）
     */
    private fun parseErrorMessage(code: Int, @Suppress("UNUSED_PARAMETER") _errorBody: String): String {
        // 尝试从响应体中提取错误信息
        return try {
            // 如果后端返回 JSON 格式的错误，可以在这里解析
            // 例如: {"code": 400, "message": "参数错误"}
            // 目前简单返回 HTTP 状态码对应的消息
            when (code) {
                400 -> "请求参数错误"
                401 -> "未授权，请重新登录"
                403 -> "无权访问该资源"
                404 -> "请求的资源不存在"
                500 -> "服务器内部错误"
                502, 503 -> "服务暂时不可用"
                else -> "请求失败 (HTTP $code)"
            }
        } catch (e: Exception) {
            "请求失败 (HTTP $code)"
        }
    }
}
