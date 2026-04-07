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

import okhttp3.Interceptor
import okhttp3.Response
/**
 * 网易云音乐API请求头拦截器
 *
 * 功能说明:
 * - 为所有网易云音乐API请求添加必要的请求头
 * - User-Agent: 模拟浏览器请求，避免API限制
 * - Referer: 标识请求来源，网易云API要求
 *
 * 使用位置:
 * - AppModule.provideNeteaseSearchRetrofit() - 配置网易云音乐API的OkHttpClient
 *
 * 创建时间: 2025-11-17
 */
class NeteaseHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        return chain.proceed(newRequest)
    }
}
