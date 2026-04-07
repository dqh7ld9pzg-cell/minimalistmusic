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

package com.minimalistmusic.data.repository

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.cache.CachePerformanceTracker
import com.minimalistmusic.data.cache.KeyValueCacheManager
import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.data.remote.FreeMusicApiService
import com.minimalistmusic.data.remote.NeteaseSearchApiService
import com.minimalistmusic.data.remote.dto.LrcDTO
import com.minimalistmusic.data.remote.dto.LyricData
import com.minimalistmusic.data.repository.mock.PresetData
import com.minimalistmusic.domain.model.PlaylistSource
import com.minimalistmusic.domain.model.RecommendPlaylist
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.util.PlayCountCalculator
import com.minimalistmusic.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/**
 * 免费音乐仓库实现
 *
 * 性能优化 (2025-11-12):
 * - 添加应用层缓存: 推荐歌单缓存6小时，减少不必要的网络请求
 * - 使用 CacheManager 管理缓存生命周期
 *
 * 离线播放增强 (2025-11-15):
 * - 注入 AudioCacheManager 以检查音频文件缓存状态
 * - 断网时优先使用已缓存的音频，无需重新请求URL
 */
@Singleton
class FreeMusicOnlineRepositoryImpl @OptIn(UnstableApi::class) @Inject constructor(
    private val freeMusicApiService: FreeMusicApiService,
    private val neteaseSearchApiService: NeteaseSearchApiService,
    private val keyValueCacheManager: KeyValueCacheManager,
    private val audioCacheManager: AudioCacheManager, // 离线播放支持：检查音频缓存状态
    private val cachedSongDao: CachedSongDao, // 缓存架构重构 (2025-11-24): 直接查询缓存状态
    private val cacheStateManager: com.minimalistmusic.domain.cache.CacheStateManager, // 架构重构：注册缓存尝试
    private val performanceTracker: CachePerformanceTracker, // 性能跟踪 (2025-12-03)
    private val userPreferencesDataStore: UserPreferencesDataStore, // 配置读取 (2025-12-03)
    private val connectivityManager: ConnectivityManager,
) : MusicOnlineRepository {

    /**
     * 获取歌曲播放URL（带降级策略 + 离线播放支持）
     *
     * 策略 (2025-11-18更新 - URL缓存优化):
     * 1. 优先从数据库URL缓存读取（SongUrlCacheManager）
     * 2. 尝试主API (FreeMusicAPI)
     * 3. 尝试备用API (NeteaseSearchAPI) - 官方API，更稳定
     * 4. 如果API失败（断网），检查音频文件是否已缓存（AudioCacheManager）
     * 5. 如果音频已缓存，使用之前的URL（song.path），支持离线播放
     *
     * 离线播放说明：
     * - 断网情况下，如果音频已缓存，优先使用已缓存的URL
     * - 确保"我喜欢"、"聆听足迹"、"已缓存"中的已缓存歌曲可以离线播放
     *
     * @param song 歌曲对象
     * @return 播放URL或错误，
     * - API格式：https://api.injahow.cn/meting/?server=netease&type=url&id=xxx
     * - 使用初期阶段返回json数据格式：{ "url": "http://...", "size": 123, "br": 320000 }
     * - 现阶段返回audio音频流格式，url已被重定向
     *
     */
    @OptIn(UnstableApi::class)
    override suspend fun getSongPlayUrl(song: Song): Result<String> = withContext(Dispatchers.IO) {
        try {
            val songId = song.id

            /**
             * 架构调整 (2025-11-22): 完整缓存的歌曲直接使用数据库URL
             * 策略说明：
             * 1. 已完整缓存的歌曲：ExoPlayer可以使用数据库URL播放本地文件，即使服务器端URL已过期
             * 2. 未完整缓存的歌曲：直接从网络获取新URL，不使用缓存的URL
             *
             * 原因：
             * - 无法准确获取每个URL的实际过期时间
             * - 完整缓存的歌曲本地文件已存在，ExoPlayer使用URL作为key匹配本地缓存
             * - 未完整缓存的歌曲需要网络请求，使用旧URL可能已失效，若URL失效可能存在播放一部分后无法播放，影响用户体验
             *
             */
            val cachedEntity = cachedSongDao.getCachedSong(songId)
            if (cachedEntity != null && cachedEntity.isFullyCached) {
                val fullyCachedUrl = cachedEntity.url
                // 验证本地缓存文件是否真实存在
                if (audioCacheManager.isSongCached(fullyCachedUrl, false)) {
                    LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_REMOTE,
                        "FreeMusicRepositoryImpl 歌曲已完整缓存，ExoPlayer将使用本地缓存播放: songId=$songId"
                    )
                    // 即使URL可能已过期，ExoPlayer也会优先使用完整的本地缓存文件
                    return@withContext Result.Success(fullyCachedUrl)
                } else {
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_REMOTE,
                        "FreeMusicRepositoryImpl 数据库标记已缓存但本地文件不存在"
                    )
                    // 继续执行下面的逻辑重新获取URL
                }
            }
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_REMOTE,
                "FreeMusicRepositoryImpl 非完整缓存，尝试主API: songId=$songId"
            )
            val url = try {
                getSongUrlFromFreeMusicApi(songId.toString())
            } catch (e: Exception) {
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "主API失败，尝试备用API: ${e.message}"
                )
                // 主API失败，尝试备用API
                try {
                    getSongUrlFromNeteaseApi(songId.toString())
                } catch (apiException: Exception) {
                    // API都失败（可能断网），检查音频文件是否已缓存
                    LogConfig.w(
                        LogConfig.TAG_PLAYER_DATA_REMOTE,
                        "FreeMusicRepositoryImpl 所有API失败，检查音频缓存: ${apiException.message}"
                    )
                    throw apiException
                }
            }

            // 架构重构：注册缓存尝试（建立 URL → songId 映射）
            val baseUrl = AudioCacheManager.getBaseUrl(url.toUri())
            cacheStateManager.registerCacheAttempt(songId, baseUrl)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_REMOTE,
                "FreeMusicRepositoryImpl 已注册缓存尝试: songId=$songId, baseUrl=$baseUrl"
            )

            // 开始性能跟踪
            // 只有在线歌曲且性能跟踪启用时才跟踪
            if (!song.isLocal && userPreferencesDataStore.enableCachePerformanceTracking.value) {
                val networkType = detectNetworkType()

                performanceTracker.startTracking(
                    songId = song.id,
                    songTitle = song.title,
                    songArtist = song.artist,
                    songDuration = song.duration,
                    url = url,
                    cacheKey = baseUrl, // 复用上面计算的 baseUrl
                    networkType = networkType,
                )
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl getSongPlayUrl 已开始性能跟踪: ${song.title}"
                )
            }

            Result.Success(url)
        } catch (e: Exception) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_REMOTE,
                "所有API都失败且音频未缓存: songId=${song.id} ,erro msg: ${e.message}"
            )
            Result.Error(e)
        }
    }

    /**
     * 从FreeMusicAPI获取播放URL（主API）
     *
     * 性能优化 (2025-11-15):
     * - 禁用OkHttp自动重定向，手动从Location头获取URL
     * - 原因：API返回302重定向到MP3文件（9MB+）
     * - 之前：OkHttp自动跟随重定向，下载整个MP3文件，耗时6-7秒
     * - 之后：只读取响应头的Location字段，耗时<500ms
     *
     * 工作原理：
     * 1. API: https://api.injahow.cn/meting/?server=netease&type=url&id=xxx
     * 2. 响应: HTTP 302 Found
     * 3. Location: https://m801.music.126.net/.../xxx.mp3
     * 4. 我们直接返回Location的值，不下载MP3文件
     *
     * 扩展性设计 (2025-11-15):
     * - 支持多种响应格式：302重定向、JSON、纯文本
     * - 策略模式：根据响应类型自动选择提取策略
     * - 易于扩展：添加新的响应格式只需添加新的if分支
     *
     * 特点：
     * - 免费但有限流（约100次/天）
     * - 返回302重定向到真实MP3 URL
     */
    private suspend fun getSongUrlFromFreeMusicApi(songId: String): String {
        val response = freeMusicApiService.getSongUrl(
            server = "netease",
            type = "url",
            id = songId
        )
        response.code()
        // 使用 response.raw() 会存在Cannot read raw response body of a converted body.的异常,所以这里使用直接使用reponse去获取header和statusCode等数据
        // val rawResponse = response.raw()
        val statusCode = response.code()
        val headers = response.headers()
        // 策略1: 处理302重定向响应 (当前FreeMusicAPI使用此方式)
        // 性能优化：直接从Location头获取URL，避免下载整个MP3文件（9MB+）
        if (statusCode in 300..399) {
            val location = headers.get("Location")
            // 立即关闭响应，不读取body（重定向响应的body通常很小或为空）
            // rawResponse.close()
            if (location.isNullOrBlank()) {
                throw Exception("FreeMusicAPI返回$statusCode 但Location头为空")
            }
            // 验证URL有效性
            if (!isValidMusicUrl(location)) {
                throw Exception("FreeMusicAPI返回的URL无效: $location")
            }
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_REMOTE,
                "FreeMusicRepositoryImpl 主API成功 (从Location获取): url=$location"
            )
            return location
        }
        // 策略2: 处理JSON响应 (扩展性设计：如果未来API返回JSON格式)
        // 示例响应: {"code": 200, "data": {"url": "https://...mp3"}}
        if (statusCode == 200) {
            val contentType = headers.get("Content-Type") ?: ""
            if (contentType.contains("application/json", ignoreCase = true)) {
                // TODO: 解析JSON响应，提取URL字段
                // 注意：使用rawResponse.body而不是response.body()
                // 原因：response.body()已经被Retrofit转换，无法读取原始body
                // val jsonBody = rawResponse.body?.string()
                // val url = parseJsonUrl(jsonBody)
                // return url
                // rawResponse.close()
                throw Exception("FreeMusicAPI返回JSON格式暂未支持，请实现JSON解析逻辑")
            }
            // 策略3: 处理纯文本响应 (扩展性设计：如果未来API直接返回URL字符串)
            // 示例响应: https://m801.music.126.net/.../xxx.mp3
            if (contentType.contains("text/plain", ignoreCase = true)) {
                // 注意：使用rawResponse.body而不是response.body()
                // 原因：response.body()已经被Retrofit转换，会抛出"Cannot read raw response body of a converted body"错误
                val urlText = response.body()?.string()?.trim()
                if (urlText.isNullOrBlank()) {
                    throw Exception("FreeMusicAPI返回文本为空")
                }
                if (!isValidMusicUrl(urlText)) {
                    throw Exception("FreeMusicAPI返回的URL无效: $urlText")
                }
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 主API成功 (从文本获取): url=$urlText"
                )
                return urlText
            }
        }
        // 未知响应格式
        throw Exception("FreeMusicAPI返回未知格式。状态码: $statusCode, Content-Type: ${headers.get("Content-Type")}")
    }

    /**
     * 从NeteaseSearchAPI获取播放URL（备用API）
     *
     * 特点：
     * - 网易云音乐官方API
     * - 更稳定，无明显限流
     * - 返回JSON格式
     */
    private suspend fun getSongUrlFromNeteaseApi(songId: String): String {
        val response = neteaseSearchApiService.getSongUrl(
            id = songId.toLong(),
            br = 320000
        )
        val url = response.data?.firstOrNull()?.url
        if (url.isNullOrBlank()) {
            throw Exception("播放地址不可用")
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_REMOTE,
            "FreeMusicRepositoryImpl 备用API成功: url=$url"
        )
        return url
    }

    /**
     * 验证URL是否是有效的音乐文件URL
     */
    private fun isValidMusicUrl(url: String): Boolean {
        return url.contains("music.126.net", ignoreCase = true) ||
                url.endsWith(".mp3", ignoreCase = true) ||
                url.endsWith(".m4a", ignoreCase = true) ||
                url.endsWith(".flac", ignoreCase = true)
    }

    /**
     * 获取歌单详情（支持普通歌单和歌手歌单）
     *
     * 说明：
     * - source="playlist": 使用FreeMusicAPI获取普通歌单
     * - source="artist": 调用getArtistTopSongs获取歌手歌曲
     * - FreeMusicAPI 返回的 JSON 中没有直接的 id 字段
     * - 需要从 item.url 参数中提取真实的歌曲 ID
     */
    override suspend fun getPlaylistDetail(playlistId: String, source: String): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            try {
                // 如果是歌手歌单，使用歌手歌曲API,实际上这里不会执行,先保留后续再考虑是否替换原有接口
                if (source == "artist") {
                    return@withContext getArtistTopSongs(artistId = playlistId.toLong())
                }
                // 普通歌单逻辑（原有实现）
                val response = freeMusicApiService.getPlaylist(
                    server = "netease",
                    type = "playlist",
                    id = playlistId
                )
                val songs = response.mapNotNull { item ->
                    Song(
                        // 从 URL 中提取真实的歌曲 ID（FreeMusicAPI 特性）
                        id = item.url!!.extractIdFromUrl()!!.toLongOrNull()
                            ?: return@mapNotNull null,
                        title = item.name,
                        artist = item.artist,
                        album = item.album,
                        duration = 0,
                        path = item.url,
                        albumArt = item.pic,
                        isLocal = false
                    )
                }
                Result.Success(songs)
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_VIEWMODEL,
                    "FreeMusicOnlineRepositoryImpl getPlaylistDetail 异常: ${e.javaClass.simpleName}: ${e.message}"
                )
                Result.Error(e)
            }
        }

    /**
     * 获取歌词
     *
     * 重构说明（2025-11-11）:
     * - 现在是 OnlineMusicRepository 接口的实现方法
     */
    override suspend fun getLyric(songId: Long): Result<String?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val cacheKey = KeyValueCacheManager.getLyricKey(songId.toString())
            val cacheLyric = keyValueCacheManager.getCache(cacheKey, String::class.java)
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "FreeMusicOnlineRepositoryImpl updateSongCacheState songId: $songId"
            )
            // 命中歌词缓存,则使用缓存
            if (!cacheLyric.isNullOrBlank()) {
                LogConfig.d(
                        LogConfig.TAG_PLAYER_DATA_REMOTE,
                        "FreeMusicRepositoryImpl: 命中歌词缓存, currentThread: ${Thread.currentThread()}"
                    )
                return@withContext Result.Success(cacheLyric)
            }
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl: 未命中歌词缓存,执行网络查询"
                )
            // 未命中缓存,在线获取歌词
            val response = freeMusicApiService.getLyric(
                id = songId
            )
            if (response != "") {
                // 将纯文本歌词包装成 LyricData
                val lyricData = LyricData(
                    lrc = LrcDTO(lyric = response),
                    tlyric = null // 暂时不对翻译做额外处理
                )
                val lyric = lyricData.lrc?.lyric
                LogConfig.d(LogConfig.TAG_PLAYER_DATA_REMOTE, "FreeMusicRepositoryImpl getLyric: response: $response")
                keyValueCacheManager.saveCache(
                    key = cacheKey,
                    data = lyric,
                    expiresIn = 30.days
                )
                Result.Success(lyric)
            } else {
                Result.Error(Exception("获取歌词失败"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * String 扩展函数：从 URL 中提取查询参数
     *
     * 用途：FreeMusicAPI 返回的歌曲信息中，真实的歌曲 ID 在 URL 参数中
     * 例如：url = "http://music.163.com/song/media/outer/url?id=123456"
     * 需要提取出 id=123456
     */
    private fun String.extractQueryParameter(paramName: String): String? {
        return try {
            Uri.parse(this).getQueryParameter(paramName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * String 扩展函数：从 URL 中提取歌曲 ID
     */
    private fun String.extractIdFromUrl(): String? {
        return extractQueryParameter("id")
    }

    /**
     * 获取歌单详情（带分页参数，已废弃）
     *
     * 重构说明（2025-11-27）:
     * - 歌手歌单的分页逻辑已移至 SearchViewModel + NeteaseSearchRepositoryImpl
     * - 普通歌单不支持分页，直接调用无分页版本
     * - 此方法保留仅为满足接口要求，实际不应被调用
     */
    @Deprecated("歌手歌单分页已移至 SearchViewModel，请使用 SearchViewModel.loadDetail()")
    override suspend fun getPlaylistDetail(
        playlistId: String,
        pageSize: Int,
        offset: Int,
        source: String,
        artistName: String?,
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        // 直接调用无分页版本
        getPlaylistDetail(playlistId, source)
    }

    /**
     * 动态更新歌手封面地址 (2025-11-20)
     *
     * 功能：通过网易云音乐搜索API获取歌手的最新封面地址
     * 原因：预制数据中的封面地址容易过期失效
     *
     * 实现策略：
     * - 并发请求所有歌手的封面地址，提高效率
     * - 请求失败时保留原有预制封面地址
     * - 使用 type=100 搜索歌手，从响应中提取 picUrl
     *
     * 更新歌手封面和播放次数（2025-11-27优化）
     *
     * 优化点：
     * - 从搜索API同时获取封面、专辑数、歌曲数
     * - 使用 PlayCountCalculator 计算播放次数，保持与详情页一致
     * - 确保精选歌单卡片的播放次数准确
     *
     * @param artists 预制歌手数据列表
     * @return 更新封面地址后的歌手列表
     */

    private suspend fun updateArtistCovers(artists: List<RecommendPlaylist>): List<RecommendPlaylist> {
        return try {
            // 并发获取所有歌手的封面地址和播放次数
            kotlinx.coroutines.coroutineScope {
                val updatedArtists = artists.map { artist ->
                    async {
                        try {
                            // 调用网易云音乐搜索API获取歌手信息
                            val response = neteaseSearchApiService.search(
                                keyword = artist.name,
                                type = 100,  // 100 = 搜索歌手
                                limit = 1
                            )
                            // 从搜索结果中提取歌手信息
                            val artistResult = response.resultDTO?.artistsDTO?.firstOrNull()
                            if (artistResult != null) {
                                val newCover = artistResult.picUrl
                                val albumSize = artistResult.albumSize ?: 0
                                val musicSize = artistResult.mvSize ?: 0  // mvSize实际是歌曲数
                                // 计算播放次数（与详情页保持一致）
                                val playCount =
                                    PlayCountCalculator.estimateArtistPlayCount(
                                        albumSize = albumSize,
                                        musicSize = musicSize
                                    )
                                LogConfig.e(
                                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                                    "FreeMusicRepositoryImpl async并发获取歌手信息成功: ${artist.name} -> cover=$newCover, albums=$albumSize, songs=$musicSize, playCount=${
                                        PlayCountCalculator.formatPlayCount(
                                            playCount
                                        )
                                    }"
                                )
                                artist.copy(
                                    cover = newCover ?: artist.cover,
                                    playCount = playCount
                                )
                            } else {
                                LogConfig.w(
                                    "FreeMusicRepositoryImpl",
                                    "动态获取歌手信息失败，使用预制数据: ${artist.name}"
                                )
                                artist
                            }
                        } catch (e: Exception) {
                            // 请求失败，保留原有数据
                            LogConfig.w(
                                "FreeMusicRepositoryImpl",
                                "获取歌手信息失败: ${artist.name}, error=${e.message}"
                            )
                            artist
                        }
                    }
                }.awaitAll()
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 动态更新歌手信息完成: ${updatedArtists.size}位歌手"
                )
                updatedArtists
            }
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_REMOTE,
                "FreeMusicRepositoryImpl 批量更新歌手信息失败: ${e.message}"
            )
            // 出现异常时返回原始列表
            artists
        }
    }

    /**
     * 获取精选歌单列表 (2025-11-20)
     *
     * 功能：获取预制的精选歌单数据，并实时更新歌手类型歌单的封面地址
     * 原因：预制数据中的封面地址容易过期失效
     *
     * @return 精选歌单列表
     */
    override suspend fun getFeaturedPlaylists(): Result<List<RecommendPlaylist>> =
        withContext(Dispatchers.IO) {
            try {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 获取精选歌单列表"
                )
                // 获取预制的精选歌单数据
                val featuredPlaylists = PresetData.PRESET_FEATURED_PLAYLIST
                // 筛选出歌手类型的歌单，需要实时更新封面
                val artistPlaylists =
                    featuredPlaylists.filter { it.source == PlaylistSource.ARTIST_PLAYLIST }
                val otherPlaylists =
                    featuredPlaylists.filter { it.source != PlaylistSource.ARTIST_PLAYLIST }
                // 动态更新歌手封面地址
                val updatedArtistPlaylists = if (artistPlaylists.isNotEmpty()) {
                    updateArtistCovers(artistPlaylists)
                } else {
                    emptyList()
                }
                // 合并歌单，保持原有顺序
                val result = featuredPlaylists.map { playlist ->
                    if (playlist.source == PlaylistSource.ARTIST_PLAYLIST) {
                        updatedArtistPlaylists.find { it.id == playlist.id } ?: playlist
                    } else {
                        playlist
                    }
                }
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 成功获取精选歌单：${result.size}个（${artistPlaylists.size}个歌手歌单已更新封面）"
                )
                Result.Success(result)
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 获取精选歌单失败：${e.message}"
                )
                // 出现异常时返回原始预制数据
                Result.Success(PresetData.PRESET_FEATURED_PLAYLIST)
            }
        }

    /**
     * 获取推荐歌单（混合推荐）
     *
     * 重构：支持混合推荐，包含普通歌单和歌手歌单
     * 功能：
     * - 获取4个普通歌单
     * - 获取1个热门歌手（Top榜单）
     * - 随机展示，提供多样化的推荐体验
     */
    override suspend fun getRecommendPlaylists(): Result<List<RecommendPlaylist>> =
        withContext(Dispatchers.IO) {
            try {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 获取混合推荐歌单"
                )
                val allRecommendations = mutableListOf<RecommendPlaylist>()
                // 1. 获取1个随机普通歌单
                val randomPlaylists = PresetData.PRESET_PLAYLISTS.shuffled().take(1)
                allRecommendations.addAll(randomPlaylists)
                // 2. 获取6位热门歌手
                val topArtistsResult = getTopArtists(offset = 0, limit = 6)
                if (topArtistsResult is Result.Success) {
                    allRecommendations.addAll(topArtistsResult.data)
                } else {
                    LogConfig.w("FreeMusicRepositoryImpl", "获取热门歌手失败，全部使用普通歌单")
                    // 如果获取热门歌手失败，补充更多普通歌单
                    val additionalPlaylists = PresetData.PRESET_PLAYLISTS.shuffled().take(3)
                    allRecommendations.addAll(additionalPlaylists)
                }
                // 3. 随机打乱展示顺序
                val mixedRecommendations = allRecommendations.shuffled()
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 成功获取混合推荐：${mixedRecommendations.size}个（${randomPlaylists.size}个歌单 + ${if (topArtistsResult is Result.Success) topArtistsResult.data.size else 0}个歌手）"
                )
                Result.Success(mixedRecommendations)
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 获取混合推荐失败：${e.message}"
                )
                Result.Error(e)
            }
        }

    override suspend fun getTopArtists(offset: Int, limit: Int): Result<List<RecommendPlaylist>> =
        withContext(Dispatchers.IO) {
            val presetArtists = PresetData.PRESET_TOP_ARTISTS.shuffled().take(limit)
            // 动态获取歌手封面地址 (2025-11-20)
            // 预制数据的封面地址容易过期，通过搜索API获取最新的封面地址
            val updatedArtists = updateArtistCovers(presetArtists)
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_REMOTE,
                "FreeMusicRepositoryImpl 从预制数据获取热门歌手：${updatedArtists.size}位"
            )
            Result.Success(updatedArtists)

            // 该接口无法使用,也没有找到可使用的替代接口，暂时使用以上预置数据的方式实现
            // neteaseSearchApiService.getTopArtists(limit = limit, offset = offset)

        }

    /**
     * 获取歌手热门歌曲
     *
     * 功能：从网易云音乐API获取歌手的Top歌曲，用于歌手歌单详情页
     *
     * 分页支持 (2025-11-17):
     * - API本身不支持offset参数，只能一次性获取所有热门歌曲
     *   另外歌曲中缺少duration、artists、album等字段,与歌手接口返回的数据格式差异较大,需要封装另一个DTO,暂时不使用该接口
     *
     * @param artistId 歌手ID
     * @return 歌手热门歌曲列表
     */
    override suspend fun getArtistTopSongs(artistId: Long): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            try {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 获取歌手热门歌曲：artistId=$artistId"
                )
                // 调用网易云音乐API获取歌手热门歌曲
                val response = neteaseSearchApiService.getArtistTopSongs(id = artistId)
                // 转换API响应为Song列表
                val songs = response.songs.map { apiSong ->
                    Song(
                        id = apiSong.id,
                        title = apiSong.name,
                        artist = apiSong.artists?.joinToString("/") { it.name } ?: "未知歌手",
                        album = apiSong.album?.name ?: "未知专辑",
                        duration = apiSong.duration.coerceAtLeast(0),
                        // 修复 (2025-11-21): 设置为null而非空字符串，避免ERROR_CODE_IO_FILE_NOT_FOUND
                        // 待播放时再获取真实URL
                        path = null,
                        albumArt = apiSong.album?.picUrl,
                        isLocal = false
                    )
                }
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 成功获取歌手热门歌曲：${songs.size}首"
                )
                Result.Success(songs)
            } catch (e: Exception) {
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_REMOTE,
                    "FreeMusicRepositoryImpl 获取歌手热门歌曲失败：${e.message}"
                )
                Result.Error(e)
            }
        }
    /**
     * 检测当前网络类型 (2025-12-03)
     */
    private fun detectNetworkType(): String {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            when {
                capabilities == null -> "Unknown"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}