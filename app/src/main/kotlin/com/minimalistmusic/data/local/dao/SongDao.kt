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

package com.minimalistmusic.data.local.dao

import androidx.room.*
import com.minimalistmusic.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲数据访问对象
 *
 * 性能优化说明 (2025-11-12):
 * - getAllLocalSongs/getFavoriteSongs: 利用索引 (isLocal/isFavorite + addedAt) 加速
 * - updateFavorite: 只更新单个字段，避免全对象更新
 * - searchSongs: LIKE查询适用于中小规模数据集（<10000首）
 *   TODO: 如果本地歌曲超过10000首，考虑使用 FTS4/FTS5 全文搜索
 */
@Dao
interface SongDao {
    /**
     * 获取所有本地歌曲
     * 性能: O(n) - 但有索引加速筛选和排序
     */
    @Query("SELECT * FROM songs WHERE isLocal = 1 ORDER BY addedAt DESC")
    fun getAllLocalSongs(): Flow<List<SongEntity>>

    /**
     * 获取收藏歌曲（按收藏时间排序）
     * 性能: O(n) - 但有索引加速筛选和排序
     * 更新 (2025-11-19): 使用 favoritedAt 排序
     */
    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY favoritedAt DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    /**
     * 获取收藏歌曲（一次性查询，非Flow）- 2025-11-14
     * 用于CachedMusicViewModel加载已缓存歌曲列表
     * 性能: O(n) - 但有索引加速筛选和排序
     * 更新 (2025-11-19): 使用 favoritedAt 排序
     */
    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY favoritedAt DESC")
    suspend fun getFavoriteSongsOnce(): List<SongEntity>

    /**
     * 根据ID查询歌曲
     * 性能: O(1) - 主键查询
     */
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    /**
     * 插入单首歌曲
     * 性能: O(log n) - 索引更新开销
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    /**
     * 批量插入歌曲
     * 性能: O(m log n) - m为插入数量，建议使用事务包裹
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE isLocal = 1")
    suspend fun deleteAllLocalSongs()

    /**
     * 更新收藏状态
     * 性能优化: 仅更新单个字段，避免UPDATE整行
     */
    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavorite(songId: Long, isFavorite: Boolean)

    /**
     * 更新收藏状态并更新收藏时间戳
     * 用于收藏/取消收藏时更新
     * @param favoritedAt 收藏时间，取消收藏时传null
     * @since 2025-11-19
     */
    @Query("UPDATE songs SET isFavorite = :isFavorite, favoritedAt = :favoritedAt WHERE id = :songId")
    suspend fun updateFavoriteWithTime(songId: Long, isFavorite: Boolean, favoritedAt: Long?)

    /**
     * 搜索歌曲
     * 性能: O(n) - LIKE全表扫描
     * 优化建议: 对于<10000首歌曲可接受，超过则应使用FTS全文搜索
     */
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * 清理冗余的在线歌曲记录
     *
     * 新增 (2025-11-20): 防止songs表无限增长
     *
     * 清理策略：
     * 1. 保留所有本地歌曲（isLocal=true）
     * 2. 保留所有收藏的歌曲（isFavorite=true）
     * 3. 保留有播放历史的歌曲（存在play_history记录）
     * 4. 保留有缓存的歌曲（存在cached_songs记录）
     * 5. 其余在线歌曲保留最近添加的maxCount首，删除更早的记录
     *
     * @param maxCount 最大保留的在线无依赖歌曲数量（默认1000）
     */
    @Query("""
        DELETE FROM songs
        WHERE isLocal = 0
        AND isFavorite = 0
        AND id NOT IN (SELECT DISTINCT songId FROM play_history)
        AND id NOT IN (SELECT DISTINCT songId FROM cached_songs)
        AND id NOT IN (
            SELECT id FROM songs
            WHERE isLocal = 0
            AND isFavorite = 0
            AND id NOT IN (SELECT DISTINCT songId FROM play_history)
            AND id NOT IN (SELECT DISTINCT songId FROM cached_songs)
            ORDER BY addedAt DESC
            LIMIT :maxCount
        )
    """)
    suspend fun trimOnlineSongs(maxCount: Int = 1000)

    /**
     * 获取在线歌曲总数（用于判断是否需要清理）
     *
     * @since 2025-11-20
     */
    @Query("SELECT COUNT(*) FROM songs WHERE isLocal = 0")
    suspend fun getOnlineSongCount(): Int

    /**
     * 清除歌曲的播放URL（path字段）
     *
     * 用于删除缓存时清理失效的URL
     * 避免下次播放时使用已失效的403 URL
     *
     * @param songId 歌曲ID
     * @since 2025-11-22
     */
    @Query("UPDATE songs SET path = NULL WHERE id = :songId")
    suspend fun clearSongPath(songId: Long)
}
