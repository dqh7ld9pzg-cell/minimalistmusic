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

import com.google.gson.annotations.SerializedName
/**
 * 网易云音乐API数据传输对象(DTO)
 *
 * 重构说明:
 * - 2025-11-12: 删除未使用的搜索相关DTO（搜索功能已移至 UnifiedSearchRepositoryImpl）
 * - 2025-11-12: 从 NeteaseSearchApiService.kt 迁移DTO
 * - 2025-11-11: 从 NeteaseMusicApiService.kt 中抽取所有DTO到独立文件
 * - 目的: 遵循关注点分离原则,ApiService只定义接口,DTO独立管理
 */
/**
 * 网易云音乐搜索响应
 *
 * 使用位置:
 * - NeteaseSearchApiService.search() - 搜索歌曲和艺术家
 * - UnifiedSearchRepositoryImpl - 实现搜索功能
 *
 * 搜索请求与响应样例:
 *
 * - 请求头:Content-Type: application/x-www-form-urlencoded //表示请求体是表单数据，以application/x-www-form-urlencoded格式编码。
 *   Content-Length: 45 //请求体长度为45字节。
 *   User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 //客户端标识，这里模拟Chrome浏览器。
 *   Referer: https://music.163.com/ //表示请求来自网易云音乐官网。
 *   Accept: \*\/\* // 客户端接受任何类型的响应。
 *   Accept-Language: zh-CN,zh;q=0.9,en;q=0.8 //优先接受中文，其次是英文。权重分别为0.9, 0.8
 *
 * - 搜索歌手的歌曲相关信息,浏览器访问: https://music.163.com/api/search/get/?s=%E6%A2%81%E9%9D%99%E8%8C%B9&type=1&limit=20 (type = 1 代表搜索歌曲)
 * - 请求体:
 *   s=%E6%A2%81%E9%9D%99%E8%8C%B9&type=1&limit=20
 *
 *   s=朴树：搜索关键词为“朴树”。
 *   type=1：搜索类型为单曲。
 *   limit=20：每页返回20条结果。
 *   offset=0：从第0条结果开始（即第一页）。
 *。
 *   响应体:
 *   顶层字段
 *   code: 200，表示成功。
 *   result: 包含搜索结果的对象。
 *   trp: 包含追踪规则，可能与搜索结果的排序或算法有关。
 *。
 *   完整结果样例:
 *
 *   {
 *     "code": 200, // 状态码：200=请求成功
 *  *  "trp": { // 追踪和推荐信息
 *  *         "rules": [ // 搜索结果排序规则数组
 *  *             "search_tab_song::254141::searchAlg$isGlobalQuality$1" // 格式：搜索类型::歌曲ID::搜索算法$质量标识$权重
 *  *         ]
 *  *     }
 *     "result": { // 搜索结果主体
 *         "hasMore": true, // 是否有更多结果可加载
 *  *      "songCount": 293 // 搜索到的歌曲总数量
 *         "songs": [ // 歌曲列表数组
 *             {
 *                 "album": { // 专辑信息
 *                     "publishTime": 1160064000000, // 专辑发布时间戳（毫秒）
 *                     "size": 12, // 专辑包含的歌曲数量
 *                     "artist": { // 专辑默认艺人信息
 *                         "img1v1Url": "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg", // 艺人头像URL
 *                         "musicSize": 0, // 音乐数量（0表示未统计）
 *                         "albumSize": 0, // 专辑数量（0表示未统计）
 *                         "img1v1": 0, // 头像版本标识
 *                         "name": "", // 艺人姓名（空表示使用歌曲艺人信息）
 *                         "alias": [], // 艺人别名数组
 *                         "id": 0, // 艺人ID（0表示未指定）
 *                         "picId": 0 // 图片ID
 *                     },
 *                     "copyrightId": 1009, // 专辑版权ID
 *                     "name": "亲亲", // 专辑名称
 *                     "id": 25388, // 专辑唯一ID
 *                     "picId": 109951168144462962, // 专辑封面图片ID
 *                     "mark": 0, // 专辑标记位
 *                     "status": 1 // 专辑状态：1=正常
 *                 },
 *                 "fee": 1, // 收费类型：1=收费歌曲
 *                 "duration": 243160, // 歌曲时长（毫秒）
 *                 "rtype": 0, // 资源类型
 *                 "ftype": 0, // 文件类型
 *                 "artists": [ // 歌曲艺人列表
 *                     {
 *                         "img1v1Url": "https://p2.music.126.net/6y-UleORITEDbvrOLV0Q8A==/5639395138885805.jpg", // 艺人头像URL
 *                         "musicSize": 0, // 音乐数量
 *                         "albumSize": 0, // 专辑数量
 *                         "img1v1": 0, // 头像版本标识
 *                         "name": "梁静茹", // 艺人姓名
 *                         "alias": [], // 艺人别名
 *                         "id": 8325, // 艺人唯一ID
 *                         "picId": 0 // 图片ID
 *                     }
 *                 ],
 *                 "copyrightId": 1009, // 歌曲版权ID
 *                 "mvid": 14590968, // MV ID（0=无MV，>0=有MV）
 *                 "name": "暖暖", // 歌曲名称
 *                 "alias": [], // 歌曲别名数组
 *                 "id": 254141, // 歌曲唯一ID
 *                 "mark": 1125917086720512, // 歌曲标记位（位掩码，表示音质、版权等属性）
 *                 "status": 0 // 歌曲状态：0=正常
 *             }
 *         ],
 *     },
 * }
 *
 *
 *
 *   完整数据样例: https://music.163.com/api/search/get/?s=%E6%A2%81%E9%9D%99%E8%8C%B9&type=100&limit=1,(type = 100 代表搜索歌手)
 *   {
 *     "result": { // 搜索结果主体
 *         "hasMore": true,           // 是否有更多结果可加载
 *         "artistCount": 2,          // 搜索到的艺术家总数量
 *         "hlWords": [               // 高亮关键词数组（用于前端搜索词高亮显示）
 *             "梁静茹"
 *         ],
 *         "artists": [               // 艺术家列表数组
 *             {
 *                 "id": 8325,                                // 艺术家唯一ID
 *                 "name": "梁静茹",                          // 艺术家姓名
 *                 "picUrl": "https://p2.music.126.net/g_32ea9zMstphGkRjwgC1g==/109951164077995938.jpg",  // 艺术家图片URL
 *                 "alias": [                                 // 艺术家别名数组
 *                     "Fish Leong",
 *                     "Jasmine Leong"
 *                 ],
 *                 "albumSize": 35,      // 专辑数量
 *                 "musicSize": 467,     // 歌曲数量
 *                 "picId": 109951164077995938,                // 图片ID
 *                 "fansGroup": null,    // 粉丝群信息（null表示无）
 *                 "recommendText": "",  // 推荐文案
 *                 "appendRecText": "",  // 附加推荐文案
 *                 "fansSize": null,     // 粉丝数量（null可能表示隐私保护或未获取）
 *                 "img1v1Url": "https://p2.music.126.net/RsBGuqdnEgMSSZuohnwg7w==/109951164078000677.jpg", // 1:1比例头像URL
 *                 "img1v1": 109951164078000677,               // 1:1头像图片ID
 *                 "mvSize": 147,        // MV数量
 *                 "followed": false,    // 当前用户是否已关注该艺术家
 *                 "alg": "alg_search_precision_artist_tab_basic",  // 搜索算法标识（用于结果排序和优化）
 *                 "alia": [             // 别名数组（与alias字段内容相同）
 *                     "Fish Leong",
 *                     "Jasmine Leong"
 *                 ],
 *                 "trans": null         // 翻译信息（null表示无翻译需要）
 *             }
 *         ],
 *         "searchQcReminder": null     // 搜索质量提醒（null表示无特殊提醒）
 *     },
 *     "code": 200         // 状态码：200=请求成功
 * }
 *
 */
// ============ 搜索相关 DTO ============
data class NeteaseSearchResponseDTO(
    val code: Int,
    @SerializedName("result")
    val resultDTO: NeteaseResultDTO?
)
/**
 * 搜索结果数据
 *
 * 更新（2025-11-17）:
 * - 添加hasMore字段（如果API返回了此字段）
 */
data class NeteaseResultDTO(
    val songs: List<SongDTO>?,
    val songCount: Int?,
    @SerializedName("artists")
    val artistsDTO: List<ArtistDTO>?,
    val artistCount: Int?,
    val hasMore: Boolean?, // API可能返回的分页字段
)
// ============ 歌曲相关 DTO ============
/**
 * 歌曲DTO
 * @property fee 0:免费 1:VIP 4:购买专辑 8:非会员可免费播放低音质
 */
data class SongDTO(
    val id: Long,
    val name: String,
    val artists: List<ArtistDTO>,
    val album: AlbumDTO,
    val duration: Long,
    val mvid: Long?,
    val fee: Int,
)
// ============ 艺术家相关 DTO ============
/**
 * 艺术家DTO
 *
 * 更新（2025-11-17）:
 * - 添加 mvSize: MV数量
 * - 添加 alias: 歌手别名列表（用于显示）
 * - 添加 alia: 歌手别名列表（备选字段）
 */
data class ArtistDTO(
    val id: Long,
    val name: String,
    val picUrl: String?,
    val albumSize: Int?,
    val mvSize: Int? = null,
    val alias: List<String>? = null,
    val alia: List<String>? = null
)
// ============ 歌曲URL相关 DTO ============
/**
 * 网易云歌曲URL响应
 *
 * 使用位置:
 * - NeteaseSearchApiService.getSongUrl() - 获取歌曲播放URL
 * - FreeMusicRepositoryImpl.getSongPlayUrl() - 调用上述接口
 */
data class NeteaseSongUrlResponseDTO(
    val code: Int,
    val data: List<NeteaseSongUrlDataDTO>?
)
/**
 * 网易云歌曲URL数据
 */
data class NeteaseSongUrlDataDTO(
    val id: Long,
    val url: String?,
    val br: Int?,
    val size: Long?,
    val type: String?
)
// ============ 歌词相关 DTO ============
/**
 * 歌词数据
 * @property lrc 原文歌词
 * @property tlyric 翻译歌词
 */
data class LyricData(
    val lrc: LrcDTO?,
    val tlyric: LrcDTO?
)
/**
 * 歌词内容DTO
 */
data class LrcDTO(
    val lyric: String?
)
// ============ 专辑相关 DTO ============
/**
 * 专辑DTO
 */
data class AlbumDTO(
    val id: Long,
    val name: String,
    val picUrl: String?,
    val picId: Long?,
    val artist: ArtistDTO?
)
// ============ 新歌相关 DTO ============
/**
 * 歌手热门歌曲数据
 */
data class ArtistTopSongsData(
    val code: Int,
    val songs: List<SongDTO>
)
/**
 * 热门歌手DTO
 */
data class TopArtistsData(
    val artists: List<TopArtistDTO>? // 可能为null，API可能返回错误
)
/**
 * 热门歌手信息DTO
 */
data class TopArtistDTO(
    val id: Long,
    val name: String,
    val picUrl: String?, // 歌手头像
    val img1v1Url: String?, // 备选头像地址
    val albumSize: Int = 0, // 专辑数量，可用于估算歌曲数量
    val musicSize: Int = 0 // 音乐数量（如果有）
)
