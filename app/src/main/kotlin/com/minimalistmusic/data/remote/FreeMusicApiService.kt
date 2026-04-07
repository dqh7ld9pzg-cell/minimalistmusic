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

package com.minimalistmusic.data.remote

import com.minimalistmusic.data.remote.dto.FreeMusicSongDetail
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
/**
 * 免费音乐API服务 (injahow API)
 * API地址: https://api.injahow.cn/
 * 用于发现页面的歌单功能
 */
interface FreeMusicApiService {
    /**
     * 获取歌曲播放URL）
     *
     * 后续可以考虑将该接口替换为网易
     * http://music.163.com/song/media/outer/url?id=123456
     *
     * API行为变更 (2025-11-13):
     * - API不再返回JSON，而是直接302重定向到MP3文件
     * - 我们使用 @Streaming 注解，并通过 Response 对象获取最终URL
     * - 不再使用 FreeMusicUrlResponse，改为返回 Response<ResponseBody>
     *
     * @param server 平台
     * @param type url
     * @param id 歌曲ID
     *
     * @return 歌曲播放地址，初期使用时返回的是json格式，里面包含视频播放url，11.13返回格式为音频数据，地址被直接重定向
     */
    @Streaming
    @GET("meting/")
    suspend fun getSongUrl(
        @Query("server") server: String = "tencent",
        @Query("type") type: String = "url",
        @Query("id") id: String
    ): retrofit2.Response<okhttp3.ResponseBody>
    /**
     * 获取歌单详情
     * @param server 平台
     * @param type playlist为请求歌单
     * @param id 歌单ID
     *
     * @return 歌单id关联的歌曲列表信息
     *
     *
     */
    @GET("meting/")
    suspend fun getPlaylist(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "playlist",
        @Query("id") id: String
    ): List<FreeMusicSongDetail>
    /**
     * 获取歌词
     * @param type lrc为请求歌词
     * @param id 歌曲id
     */
    @GET("meting/")
    suspend fun getLyric(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "lrc",
        @Query("id") id: Long
    ): String
}
