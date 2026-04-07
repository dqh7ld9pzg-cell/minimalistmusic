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

package com.minimalistmusic.data.remote.dto

/**
 * 免费音乐API数据传输对象(DTO)
 *
 * 重构说明 (2025-11-11):
 * - 从 FreeMusicApiService.kt 中抽取所有DTO到独立文件
 * - 目的: 遵循关注点分离原则,ApiService只定义接口,DTO独立管理
 * - 原文件位置: FreeMusicApiService.kt (lines 88-157)
 */
// ============ 主API (injahow) 数据模型 ============
/**
 * 免费音乐搜索响应
 */
data class FreeMusicSearchResponse(
    val data: List<FreeMusicSearchItem>?
)
/**
 * 免费音乐搜索项
 */
data class FreeMusicSearchItem(
    val id: String,
    val name: String,
    val artist: List<String>,
    val album: String,
    val pic_id: String?,
    val url_id: String?,
    val lyric_id: String?
)
/**
 * 免费音乐URL响应
 */
data class FreeMusicUrlResponse(
    val url: String?,
    val size: Long?,
    val br: Int?
)
/**
 * 免费音乐歌曲详情
 *
 * 用于歌单详情、歌曲详情等接口的响应
 */
data class FreeMusicSongDetail(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String?,
    val url: String?,
    val lrc: String?
)
// ============ 备用API (music-api) 数据模型 ============
/**
 * 备用API搜索响应
 *
 * 备用API地址: https://github.com/xingqiu-io/music-api
 * 用于主API不可用时的降级方案
 */
data class BackupSearchResponse(
    val code: Int,
    val data: List<BackupSong>?
)
/**
 * 备用API歌曲数据
 */
data class BackupSong(
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val pic: String
)
/**
 * 备用API URL响应
 */
data class BackupUrlResponse(
    val code: Int,
    val url: String?
)
