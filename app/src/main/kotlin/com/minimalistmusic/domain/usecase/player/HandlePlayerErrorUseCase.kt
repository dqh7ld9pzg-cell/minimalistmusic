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

package com.minimalistmusic.domain.usecase.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.service.state.PlaybackStateManager
import com.minimalistmusic.util.LogConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 处理播放器错误用例 (重构 2025-12-01)
 *
 * 职责：
 * - 解析播放错误类型
 * - 显示用户友好提示
 * - 清理失效的缓存碎片
 * - 清除失效的URL
 * - 通知需要重新获取URL
 *
 * 架构优势：
 * - 职责单一：只处理播放错误
 * - 逻辑清晰：从 MusicService 的 223 行降至独立模块
 * - 易于测试：可以 Mock 依赖
 * - 遵循分层：Domain 层业务逻辑，不直接依赖 Service
 *
 * 使用方式：
 * ```kotlin
 * // 在 MusicService 的 onPlayerError 中
 * override fun onPlayerError(error: PlaybackException) {
 *     viewModelScope.launch {
 *         handlePlayerErrorUseCase(
 *             error = error,
 *             currentUrl = player?.currentMediaItem?.localConfiguration?.uri?.toString(),
 *             onUrlExpired = { songId, index ->
 *                 // 通知 ViewModel 重新获取 URL
 *             }
 *         )
 *     }
 * }
 * ```
 */
@Singleton
class HandlePlayerErrorUseCase @OptIn(UnstableApi::class)
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicLocalRepository: MusicLocalRepository,
    private val audioCacheManager: AudioCacheManager,
    private val cachedSongDao: CachedSongDao,
    private val playbackStateManager: PlaybackStateManager,
) {
    /**
     * 处理播放错误
     *
     * @param error PlaybackException 播放错误对象
     * @param currentUrl 当前播放的 URL
     * @param onUrlExpired URL 失效回调（通知 ViewModel 重新获取 URL）
     */
    suspend operator fun invoke(
        error: PlaybackException,
        currentUrl: String?,
        onUrlExpired: (suspend (songId: Long, currentIndex: Int) -> Unit)?,
    ) {
        // 1. 记录错误日志
        logError(error, currentUrl)

        // 2. 显示用户友好提示
        showErrorToast(error)

        // 3. 清理缓存（如果需要）
        val currentSongId = playbackStateManager.currentState.currentSong?.id
        if (shouldCleanCache(error) && !currentUrl.isNullOrEmpty() && currentSongId != null) {
            cleanupCache(error, currentUrl, currentSongId, onUrlExpired)
        }
    }

    /**
     * 记录错误日志
     */
    private fun logError(error: PlaybackException, currentUrl: String?) {
        LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "播放错误:")
        LogConfig.e(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "错误类型: ${getErrorTypeString(error.errorCode)}"
        )
        LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "错误信息: ${error.message}")
        LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "当前URL: $currentUrl")
        error.cause?.let { cause ->
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "原始异常: ${cause.javaClass.simpleName}: ${cause.message}"
            )
        }
    }

    /**
     * 显示用户友好的错误提示
     */
    private fun showErrorToast(error: PlaybackException) {
        val userMessage = when {
            // HTTP 403/404错误
            error.cause is HttpDataSource.InvalidResponseCodeException -> {
                val httpError = error.cause as HttpDataSource.InvalidResponseCodeException
                when (httpError.responseCode) {
                    403 -> "该歌曲因版权原因无法播放"
                    404 -> "歌曲资源不存在"
                    else -> "无法播放，服务器错误(${httpError.responseCode})"
                }
            }
            // 网络连接错误
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                "无法播放，请检查网络连接"
            }
            // 文件不存在
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                "歌曲文件不存在"
            }
            // 其他错误
            else -> "该歌曲无法播放"
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 判断是否需要清理缓存
     */
    private fun shouldCleanCache(error: PlaybackException): Boolean {
        return when {
            error.cause is HttpDataSource.InvalidResponseCodeException -> true
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> true
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
            else -> false
        }
    }

    /**
     * 清理失效的缓存碎片
     *
     * 关键原则：
     * - 已完整缓存的歌曲：音频文件在本地，URL失效不影响播放，但如果播放错误说明文件损坏，需要重置
     * - 未完整缓存的歌曲：删除碎片文件和数据库记录，下次重新获取URL
     */
    @OptIn(UnstableApi::class)
    private suspend fun cleanupCache(
        error: PlaybackException,
        currentUrl: String,
        currentSongId: Long,
        onUrlExpired: (suspend (songId: Long, currentIndex: Int) -> Unit)?,
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. 诊断缓存状态
            val cachedSong = cachedSongDao.getCachedSong(currentSongId)
            val dbIsFullyCached = cachedSong?.isFullyCached == true
            val actualIsCached = audioCacheManager.isSongCached(currentUrl, false)

            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "缓存状态诊断:\n" +
                "  songId=$currentSongId\n" +
                "  数据库标记已缓存: $dbIsFullyCached\n" +
                "  实际缓存验证结果: $actualIsCached\n" +
                "  URL: $currentUrl\n" +
                "  错误类型: ${error.cause?.javaClass?.simpleName}"
            )

            // 2. 诊断缓存文件详细状态
            if (dbIsFullyCached) {
                audioCacheManager.debugCacheStatus(currentUrl)
            }

            // 3. 决策逻辑：根据实际缓存状态决定是否删除
            if (dbIsFullyCached && actualIsCached) {
                // 情况1: 数据库标记已缓存 && 实际也缓存了
                // 这不应该发生，因为 ExoPlayer 完整缓存不访问网络
                // 修复: 重置记录而不是完全保留
                // 原因：完全保留会导致歌曲永远无法重新缓存
                LogConfig.e(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "[异常] 完整缓存的歌曲出现播放错误!\n" +
                    "  文件可能损坏或解码失败\n" +
                    "  重置缓存记录，允许用户重新缓存"
                )
                // 删除数据库记录 + 清理可能损坏的缓存文件
                musicLocalRepository.deleteCachedSong(currentSongId)
                audioCacheManager.removeCachedSong(currentUrl)
            } else {
                // 情况2+3合并: 未完整缓存 或 数据库与实际不一致
                // 两种情况都需要清理碎片文件和数据库记录
                val reason = if (dbIsFullyCached && !actualIsCached) {
                    "数据库与实际不一致: 标记已缓存但文件不存在\n  可能原因: ExoPlayer LRU删除了文件但未通知数据库"
                } else {
                    "未完整缓存,存在碎片文件"
                }
                LogConfig.w(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "$reason\n  清理数据库记录和碎片文件: songId=$currentSongId"
                )
                // 删除数据库记录 + 清理碎片文件
                musicLocalRepository.deleteCachedSong(currentSongId)
                audioCacheManager.removeCachedSong(currentUrl)
            }

            // 4. 清除失效 URL
            // 原因：所有情况都已删除了数据库记录，必须同步清除 Song.path
            clearExpiredUrl(currentSongId)

            // 5. 通知 ViewModel 重新获取 URL
            val currentIndex = playbackStateManager.currentState.currentIndex
            withContext(Dispatchers.Main) {
                onUrlExpired?.invoke(currentSongId, currentIndex)
                LogConfig.d(
                    LogConfig.TAG_PLAYER_DATA_LOCAL,
                    "已通知 ViewModel 重新获取 URL: songId=$currentSongId, index=$currentIndex"
                )
            }

            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "已清理失效URL的缓存碎片: songId=$currentSongId, url=$currentUrl, Song.path已清除, playlist已更新"
            )
        } catch (e: Exception) {
            LogConfig.e(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "缓存清理失败: ${e.message}"
            )
        }
    }

    /**
     * 清除失效的 URL
     *
     * 步骤：
     * 1. 清除 Song 表中的 path 字段
     * 2. 更新内存中播放列表的 path 字段
     */
    private suspend fun clearExpiredUrl(songId: Long) {
        // 1. 清除 Song 表中的 path 字段
        musicLocalRepository.clearSongPath(songId)

        // 2. 更新内存中播放列表的 path 字段
        val updatedPlaylist = playbackStateManager.currentState.playlist.map { song ->
            if (song.id == songId) {
                song.copy(path = null)  // 清除 path
            } else {
                song
            }
        }
        playbackStateManager.updateSongsInPlaylist(updatedPlaylist)
    }

    /**
     * 获取错误类型的可读字符串
     */
    private fun getErrorTypeString(errorCode: Int): String {
        return when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "网络连接失败"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "网络连接超时"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                "无效的HTTP内容类型"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "HTTP错误状态码"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "文件未找到"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "无权限访问"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "不允许HTTP明文传输"
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                "读取位置超出范围"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "音频容器格式错误"
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "清单文件格式错误"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "不支持的音频容器"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "解码器初始化失败"
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "解码失败"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
                "音频轨道初始化失败"
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                "音频轨道写入失败"
            else -> "未知错误 (code: $errorCode)"
        }
    }
}
