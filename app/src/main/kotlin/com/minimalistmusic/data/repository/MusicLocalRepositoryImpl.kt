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

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.util.LogConfig
import com.minimalistmusic.data.cache.AudioCacheManager
import com.minimalistmusic.data.local.*
import com.minimalistmusic.data.local.dao.CacheDao
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.data.local.dao.FavoritePlaylistDao
import com.minimalistmusic.data.local.dao.PlayHistoryDao
import com.minimalistmusic.data.local.dao.PlaylistDao
import com.minimalistmusic.data.local.dao.SongDao
import com.minimalistmusic.data.local.entity.FavoritePlaylistEntity
import com.minimalistmusic.data.local.entity.PlayHistoryEntity
import com.minimalistmusic.data.local.entity.PlaylistEntity
import com.minimalistmusic.data.local.entity.PlaylistSongCrossRef
import com.minimalistmusic.data.mapper.*
import com.minimalistmusic.domain.model.*
import com.minimalistmusic.domain.repository.MusicLocalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地音乐仓库实现
 *
 * 职责：
 * - 处理本地歌曲的增删改查
 * - 管理收藏歌曲（我喜欢）
 * - 管理播放列表
 * - 记录播放历史
 * - 管理收藏歌单（2025-11-16）
 *
 * 重构说明（2025-11-11）:
 * - 从 MusicRepositoryImpl 中拆分出本地数据操作
 * - 仅依赖本地数据库 DAO，不涉及网络 API
 *
 * 更新（2025-11-16）:
 * - 添加歌单收藏功能支持
 */
@Singleton
class MusicLocalRepositoryImpl @OptIn(UnstableApi::class) @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playHistoryDao: PlayHistoryDao,
    private val favoritePlaylistDao: FavoritePlaylistDao,  // 新增：收藏歌单DAO
    private val cachedSongDao: CachedSongDao,  // 新增：缓存歌曲DAO (2025-11-16)
    private val audioCacheManager: AudioCacheManager,
    private val cacheDao: CacheDao,
) : MusicLocalRepository {
    // ============ 本地歌曲操作 ============
    override fun getFavoriteSongs(): Flow<List<Song>> {
        return songDao.getFavoriteSongs().map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * 获取收藏的歌曲（一次性查询）- 2025-11-14
     *
     * 实现原理：
     * - 直接调用Dao的同步查询方法
     * - 不返回Flow，适用于一次性数据加载场景
     */
    override suspend fun getFavoriteSongsOnce(): List<Song> {
        return songDao.getFavoriteSongsOnce().map { it.toModel() }
    }

    override fun getAllLocalSongs(): Flow<List<Song>> {
        return songDao.getAllLocalSongs().map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * 插入歌曲（保留已有收藏状态）
     *
     * Bug修复 (2025-11-15):
     * - 问题：使用 REPLACE 策略会覆盖现有收藏状态
     * - 解决：扫描前检查歌曲是否已存在，如存在则保留 isFavorite 状态
     * - 场景：应用重启后重新扫描本地音乐时，已收藏的本地音乐不会丢失收藏状态
     */
    override suspend fun insertSongs(songs: List<Song>) {
        val songsToInsert = songs.map { song ->
            // 检查歌曲是否已存在数据库中
            val existingSong = songDao.getSongById(song.id)
            if (existingSong != null) {
                // 如果歌曲已存在，保留其收藏状态
                song.copy(isFavorite = existingSong.isFavorite).toEntity()
            } else {
                // 新歌曲，使用原有状态（isFavorite = false）
                song.toEntity()
            }
        }
        songDao.insertSongs(songsToInsert)
    }

    override suspend fun updateSong(song: Song) {
        songDao.updateSong(song.toEntity())
    }

    override suspend fun toggleFavorite(song: Song) {
        // 先检查歌曲是否在数据库中
        val existingSong = songDao.getSongById(song.id)
        if (existingSong == null) {
            // 如果歌曲不存在，先插入（通常是在线歌曲）
            val currentTime = System.currentTimeMillis()
            songDao.insertSong(
                song.copy(
                    isFavorite = true,
                    addedAt = currentTime,
                    favoritedAt = currentTime  // 收藏时间
                ).toEntity()
            )
            // 白名单管理 (2025-11-22): 收藏时自动加入白名单
            updateCacheProtectionOnFavorite(song.id, true)
        } else {
            val newFavoriteState = !existingSong.isFavorite
            if (newFavoriteState) {
                // 重新收藏：更新收藏状态和收藏时间戳
                songDao.updateFavoriteWithTime(song.id, true, System.currentTimeMillis())
                // 白名单管理 (2025-11-22): 收藏时自动加入白名单
                updateCacheProtectionOnFavorite(song.id, true)
            } else {
                // 取消收藏：根据歌曲类型和依赖关系处理
                if (existingSong.isLocal) {
                    // 本地歌曲：只更新状态，清除收藏时间（文件还在本地，保留记录）
                    songDao.updateFavoriteWithTime(song.id, false, null)
                } else {
                    // 在线歌曲：检查是否有其他依赖（播放历史或缓存）
                    val hasPlayHistory = playHistoryDao.getPlayHistoryPaged(1, 0)
                        .any { it.id == song.id }
                    val hasCachedData = cachedSongDao.getCachedSong(song.id) != null
                    if (hasPlayHistory || hasCachedData) {
                        // 有依赖：只更新状态，清除收藏时间，保留记录
                        songDao.updateFavoriteWithTime(song.id, false, null)
                    } else {
                        // 无依赖：删除记录，保持数据库干净
                        songDao.deleteSong(existingSong)
                    }
                }
                // 白名单管理 (2025-11-22): 取消收藏时检查是否手动加入了白名单
                // 如果没有手动加入白名单，则移除白名单保护
                updateCacheProtectionOnFavorite(song.id, false)
            }
        }
    }

    /**
     * 白名单管理 (2025-11-22)
     *
     * 策略：
     * - 收藏歌曲时：如果歌曲已完整缓存，自动加入白名单（isProtected=true）
     * - 取消收藏时：如果歌曲已完整缓存，自动移除白名单保护（isProtected=false）
     *
     * 修复 (2025-11-22): 取消收藏时也自动移除白名单保护
     * - 原问题：取消爱心后白名单图标不刷新
     * - 原因：updateCacheProtectionOnFavorite() 取消收藏时不执行任何操作
     * - 解决：取消收藏时调用 updateProtectedStatus(songId, false)
     *
     * @param songId 歌曲ID
     * @param isFavorite true-收藏, false-取消收藏
     */
    private suspend fun updateCacheProtectionOnFavorite(songId: Long, isFavorite: Boolean) {
        val cachedSong = cachedSongDao.getCachedSong(songId)
        if (cachedSong != null && cachedSong.isFullyCached) {
            // 只对完整缓存的歌曲更新白名单状态
            cachedSongDao.updateProtectedStatus(songId, isFavorite)
        }
    }

    override suspend fun isFavorite(songId: Long): Boolean {
        return songDao.getSongById(songId)?.isFavorite ?: false
    }

    override suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song.toEntity())
    }

    // ============ 播放历史操作 ============
    override fun getPlayHistory(): Flow<List<Song>> {
        return playHistoryDao.getPlayHistory().map { songsWithPlayTime ->
            songsWithPlayTime.map { songWithPlayTime ->
                Song(
                    id = songWithPlayTime.id,
                    title = songWithPlayTime.title,
                    artist = songWithPlayTime.artist,
                    album = songWithPlayTime.album,
                    duration = songWithPlayTime.duration,
                    path = songWithPlayTime.path,
                    albumArt = songWithPlayTime.albumArt,
                    isLocal = songWithPlayTime.isLocal,
                    isFavorite = songWithPlayTime.isFavorite,
                    addedAt = songWithPlayTime.playedAt  // 使用播放时间替换addedAt (2025-11-19)
                )
            }
        }
    }

    override suspend fun getPlayHistoryPaged(limit: Int, offset: Int): List<Song> {
        return playHistoryDao.getPlayHistoryPaged(limit, offset).map { it.toModel() }
    }

    override suspend fun getPlayHistoryWithTimePaged(limit: Int, offset: Int): List<Song> {
        return playHistoryDao.getPlayHistoryWithTimePaged(limit, offset).map { songWithPlayTime ->
            Song(
                id = songWithPlayTime.id,
                title = songWithPlayTime.title,
                artist = songWithPlayTime.artist,
                album = songWithPlayTime.album,
                duration = songWithPlayTime.duration,
                path = songWithPlayTime.path,
                albumArt = songWithPlayTime.albumArt,
                isLocal = songWithPlayTime.isLocal,
                isFavorite = songWithPlayTime.isFavorite,
                addedAt = songWithPlayTime.playedAt  // 使用播放时间替换addedAt
            )
        }
    }

    override suspend fun addToPlayHistory(song: Song) {
        // 确保歌曲在数据库中存在，并更新歌曲信息
        val existingSong = songDao.getSongById(song.id)
        val isNewSong = existingSong == null
        if (isNewSong) {
            // 新歌曲，直接插入
            songDao.insertSong(song.toEntity())
        } else {
            // 歌曲已存在，更新歌曲信息（保留收藏状态和收藏时间）
            // 修复 (2025-11-20): 确保播放历史中的歌曲信息完整（封面、歌手等）
            val updatedSong = song.copy(
                isFavorite = existingSong?.isFavorite ?: false,
                addedAt = existingSong?.addedAt ?: System.currentTimeMillis(),
                favoritedAt = existingSong?.favoritedAt
            )
            songDao.updateSong(updatedSong.toEntity())
        }
        // 添加或更新播放历史记录
        // 优化 (2025-11-20): 立即入库，初始不标记为聆听足迹
        // - 解决外键约束失败：歌曲开始播放就立即入库，避免缓存URL时外键约束失败
        // - isListeningRecord初始为false，播放>=10秒后由调用方更新为true
        //
        // 修复 (2025-11-20): 修复聆听足迹点击播放时排序混乱的问题
        // 问题：点击播放时更新playedAt导致列表重新排序，但UI未刷新
        // 解决：只在第一次插入时设置时间，后续播放不更新时间
        //      等待播放>=10秒触发markAsListeningRecord时才更新时间
        val existingHistory = playHistoryDao.getPlayHistory(song.id)
        if (existingHistory == null) {
            // 记录不存在，插入新记录（isListeningRecord=false）
            val currentTime = System.currentTimeMillis()
            playHistoryDao.insertPlayHistory(
                PlayHistoryEntity(
                    songId = song.id,
                    playedAt = currentTime,
                    isListeningRecord = false
                )
            )
        }
        // 记录已存在，不更新playedAt，等待markAsListeningRecord更新
        // 新增 (2025-11-20): 定期清理songs表，防止无限增长
        // 只在插入新的在线歌曲时检查并清理，避免频繁执行
        if (isNewSong && !song.isLocal) {
            val onlineCount = songDao.getOnlineSongCount()
            if (onlineCount > 1500) {  // 超过1500首在线歌曲时触发清理
                songDao.trimOnlineSongs(1000)  // 保留最近1000首
            }
        }
    }

    /**
     * 更新播放记录为聆听足迹
     * 当歌曲播放>=10秒时调用
     *
     * @param songId 歌曲ID
     * @since 2025-11-20
     */
    override suspend fun markAsListeningRecord(songId: Long) {
        val currentTime = System.currentTimeMillis()
        val updated = playHistoryDao.updatePlayedAtWithListeningFlag(songId, currentTime, true)
        if (updated > 0) {
            // 限制聆听足迹最多200首歌曲
            // 只在标记为聆听足迹时检查，避免频繁执行
            playHistoryDao.trimListeningRecords(200)
        }
    }

    override suspend fun clearPlayHistory() {
        playHistoryDao.clearPlayHistory()
    }

    override suspend fun getPlayHistoryCount(): Int {
        return playHistoryDao.getPlayHistoryCount()
    }

    override fun observePlayHistoryCount(): Flow<Int> {
        return playHistoryDao.observePlayHistoryCount()
    }

    override fun observeLatestPlayedAt(): Flow<Long?> {
        return playHistoryDao.observeLatestPlayedAt()
    }

    override suspend fun deletePlayHistoryBySongId(songId: Long) {
        playHistoryDao.deletePlayHistoryBySongId(songId)
    }

    // ============ 歌单收藏操作 (2025-11-16) ============
    override fun getAllFavoritePlaylists(): Flow<List<RecommendPlaylist>> {
        return favoritePlaylistDao.getAllFavoritePlaylists().map { entities ->
            entities.map { entity ->
                RecommendPlaylist(
                    id = entity.playlistId,
                    name = entity.name,
                    cover = entity.cover ?: "",
                    playCount = entity.songCount,
                    description = entity.description,
                    source = when (entity.source) {
                        "artist" -> PlaylistSource.ARTIST_PLAYLIST
                        else -> PlaylistSource.GENERATED_PLAYLIST
                    }
                )
            }
        }
    }

    override suspend fun toggleFavoritePlaylist(playlist: RecommendPlaylist) {
        val isFavorited = favoritePlaylistDao.isFavorited(playlist.id)
        if (isFavorited) {
            // 已收藏，移除收藏
            favoritePlaylistDao.deleteFavoritePlaylist(playlist.id)
        } else {
            // 未收藏，添加收藏
            val entity = FavoritePlaylistEntity(
                playlistId = playlist.id,
                name = playlist.name,
                cover = playlist.cover,
                description = playlist.description,
                songCount = playlist.playCount,
                source = when (playlist.source) {
                    PlaylistSource.ARTIST_PLAYLIST -> "artist"
                    else -> "playlist"
                },
                favoritedAt = System.currentTimeMillis()
            )
            favoritePlaylistDao.insertFavoritePlaylist(entity)
        }
    }

    override suspend fun isFavoritePlaylist(playlistId: Long): Boolean {
        return favoritePlaylistDao.isFavorited(playlistId)
    }

    override suspend fun removeFavoritePlaylist(playlistId: Long) {
        favoritePlaylistDao.deleteFavoritePlaylist(playlistId)
    }
    // ============ 已缓存音乐操作 (2025-11-16架构重构) ============
    /**
     * 获取所有已缓存的歌曲
     *
     * 架构说明：
     * - 从cached_songs表读取（独立的缓存音乐表）
     * - 不再依赖播放历史和收藏表
     * - 按缓存时间倒序排列
     *
     * @return 已缓存歌曲列表Flow
     */
    override fun getAllCachedSongs(): Flow<List<Song>> {
        return cachedSongDao.getAllCachedSongs().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getAllCachedSongsWithTime(): Flow<List<Song>> {
        // 更新 (2025-11-21): 使用getAllCachedSongsOrderedByPlayTime()按最近播放时间排序
        return cachedSongDao.getAllCachedSongsOrderedByPlayTime().map { songsWithCacheAndPlayTime ->
            songsWithCacheAndPlayTime.map { songWithTime ->
                Song(
                    id = songWithTime.id,
                    title = songWithTime.title,
                    artist = songWithTime.artist,
                    album = songWithTime.album,
                    duration = songWithTime.duration,
                    path = songWithTime.path,
                    albumArt = songWithTime.albumArt,
                    isLocal = songWithTime.isLocal,
                    isFavorite = songWithTime.isFavorite,
                    addedAt = songWithTime.lastPlayedAt  // 使用最近播放时间替换addedAt (2025-11-21)
                )
            }
        }
    }

    /**
     * 删除缓存记录
     *
     * 说明：
     * - 仅删除数据库中的缓存记录
     * - 音频文件的删除由AudioCacheManager负责
     *
     * @param songId 歌曲ID
     */
    override suspend fun deleteCachedSong(songId: Long) {
        cachedSongDao.deleteCachedSong(songId)
    }

    /**
     * 更新缓存歌曲的最近播放时间
     *
     * 新增 (2025-11-21): LRU缓存管理
     * 在播放完整缓存的歌曲时调用
     */
    override suspend fun updateCachedSongLastPlayedAt(songId: Long) {
        cachedSongDao.updateLastPlayedAt(songId)
    }

    /**
     * 清理超过上限的缓存歌曲
     *
     * 新增 (2025-11-21): LRU缓存管理
     * 执行流程：
     * 1. 获取需要删除的URL列表
     * 2. 删除ExoPlayer缓存文件
     * 3. 删除数据库记录
     *
     * @param maxCount 最大缓存歌曲数
     * @return 清理的歌曲数量
     */
    @OptIn(UnstableApi::class)
    override suspend fun trimCachedSongs(maxCount: Int): Int {
        // 1. 获取需要删除的URL列表
        val urlsToDelete = cachedSongDao.getUrlsToDelete(maxCount)
        // 2. 删除ExoPlayer缓存文件
        urlsToDelete.forEach { url ->
            audioCacheManager.removeCachedSong(url)
        }
        // 3. 删除数据库记录
        cachedSongDao.trimCachedSongsByPlayTime(maxCount)
        return urlsToDelete.size
    }

    /**
     * 清理超过上限的缓存歌曲（优先清理非白名单）
     *
     * 策略 (2025-11-25优化):
     * - 计算需要清理的数量：toDelete = currentCount - maxCount
     * - 清理顺序：
     *   1. 优先清理非白名单歌曲（按最久未播放排序）
     *   2. 如果非白名单清理完还不够，继续清理白名单歌曲（按最久未播放排序）
     *
     * 示例：
     * - 当前20首（15首白名单 + 5首非白名单），上限调整为10首
     * - 需要清理10首
     * - 先清理5首非白名单，再清理5首白名单（最久未播放）
     *
     * @param maxCount 最大缓存数量
     * @return 清理的歌曲数量
     */
    @OptIn(UnstableApi::class)
    override suspend fun trimCachedSongsWithProtection(maxCount: Int): Int {
        // 1. 获取当前缓存数量
        val currentCount = cachedSongDao.getCachedSongCount()
        // 2. 计算需要删除的数量（修复 2025-11-25）
        // 精确清理超出部分，不使用buffer策略
        // buffer策略应该在ExoPlayer的LRU层面处理，数据库记录层只做精确管理
        // 例如：上限20首，当前21首 -> 清理1首，剩余20首
        val exceededCount = currentCount - maxCount
        if (exceededCount <= 0) {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "LocalMusicRepository 无需清理: 当前=${currentCount}首 <= 上限=${maxCount}首"
            )
            return 0
        }
        val toDelete = exceededCount
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "LocalMusicRepository 开始清理: 当前=${currentCount}首, 上限=${maxCount}首, " +
                    "超出=${exceededCount}首, 需清理=${toDelete}首"
        )
        // 3. 获取所有歌曲URL（按清理优先级排序）
        val nonProtectedUrls = cachedSongDao.getAllNonProtectedUrlsSortedByLRU()  // 非白名单（最久未播放在前）
        val protectedUrls = cachedSongDao.getAllProtectedUrlsSortedByLRU()        // 白名单（最久未播放在前）
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "LocalMusicRepository 歌曲分布: 非白名单=${nonProtectedUrls.size}首, 白名单=${protectedUrls.size}首"
        )
        // 4. 组合删除列表：优先从非白名单中取，不够再从白名单中取
        val urlsToDelete = mutableListOf<String>()
        // 4.1 先从非白名单中取
        val nonProtectedToDelete = minOf(toDelete, nonProtectedUrls.size)
        urlsToDelete.addAll(nonProtectedUrls.take(nonProtectedToDelete))
        // 4.2 如果还不够，从白名单中取
        val remainingToDelete = toDelete - nonProtectedToDelete
        if (remainingToDelete > 0) {
            val protectedToDelete = minOf(remainingToDelete, protectedUrls.size)
            urlsToDelete.addAll(protectedUrls.take(protectedToDelete))
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "LocalMusicRepository 清理详情: 非白名单=${nonProtectedToDelete}首, 白名单=${protectedToDelete}首"
            )
        } else {
            LogConfig.d(
                LogConfig.TAG_PLAYER_DATA_LOCAL,
                "LocalMusicRepository 清理详情: 非白名单=${nonProtectedToDelete}首（白名单无需清理）"
            )
        }
        // 5. 删除ExoPlayer缓存文件
        urlsToDelete.forEach { url ->
            audioCacheManager.removeCachedSong(url)
        }
        // 6. 删除数据库记录（通过URL删除）
        urlsToDelete.forEach { url ->
            cachedSongDao.deleteCachedSongByCacheKey(url)
        }
        LogConfig.d(
            LogConfig.TAG_PLAYER_DATA_LOCAL,
            "LocalMusicRepository 清理完成: 已清理 ${urlsToDelete.size} 首歌曲"
        )
        return urlsToDelete.size
    }

    override suspend fun clearCachedRecords() {
        cachedSongDao.clearAllCachedSongs()
        cacheDao.clearAllCache()
    }

    /**
     * 清除歌曲的播放URL（path字段）
     *
     * 用于删除缓存时清理失效的URL
     * 避免下次播放时使用已失效的403 URL
     *
     * @param songId 歌曲ID
     * @since 2025-11-22
     */
    override suspend fun clearSongPath(songId: Long) {
        songDao.clearSongPath(songId)
    }

    // ============ 歌单操作 ============
    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun createPlaylist(name: String, cover: String?): Long {
        val playlist = PlaylistEntity(
            name = name,
            cover = cover,
            songCount = 0
        )
        return playlistDao.insertPlaylist(playlist)
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist.toEntity())
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val crossRef = PlaylistSongCrossRef(
            playlistId = playlistId,
            songId = songId,
            position = playlistDao.getPlaylistSongCount(playlistId)
        )
        playlistDao.insertPlaylistSong(crossRef)
        // 更新歌单歌曲数量
        val playlist = playlistDao.getPlaylistById(playlistId)
        playlist?.let {
            playlistDao.updatePlaylist(it.copy(songCount = it.songCount + 1))
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        val crossRef = PlaylistSongCrossRef(playlistId, songId)
        playlistDao.deletePlaylistSong(crossRef)
        // 更新歌单歌曲数量
        val playlist = playlistDao.getPlaylistById(playlistId)
        playlist?.let {
            playlistDao.updatePlaylist(it.copy(songCount = maxOf(0, it.songCount - 1)))
        }
    }

    override fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getPlaylistSongs(playlistId).map { entities ->
            entities.map { it.toModel() }
        }
    }
}
