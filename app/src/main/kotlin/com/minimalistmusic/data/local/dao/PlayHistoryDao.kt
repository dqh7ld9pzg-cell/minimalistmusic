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
import com.minimalistmusic.data.local.entity.PlayHistoryEntity
import com.minimalistmusic.data.local.entity.SongEntity
import com.minimalistmusic.data.local.model.SongWithPlayTime
import kotlinx.coroutines.flow.Flow

/**
 * 播放历史数据访问对象
 *
 * 性能优化说明 (2025-11-12):
 * - getPlayHistory/getPlayHistoryPaged: 利用复合索引 (songId, playedAt) 加速
 * - JOIN + GROUP BY + ORDER BY 查询已通过索引优化
 */
@Dao
interface PlayHistoryDao {
    /**
     * 获取播放历史（最近500首聆听足迹，包含播放时间）
     * 性能: O(n log n) - 有索引支持的JOIN和排序
     * 去重逻辑: GROUP BY songs.id 确保每首歌只出现一次
     * 更新 (2025-11-19): 返回 SongWithPlayTime 以便显示播放时间
     * 更新 (2025-11-20): 只返回isListeningRecord=true的记录（聆听足迹）
     */
    @Query("""
        SELECT songs.id, songs.title, songs.artist, songs.album, songs.duration,
               songs.path, songs.albumArt, songs.isLocal, songs.isFavorite, songs.addedAt, songs.source,
               MAX(play_history.playedAt) as playedAt
        FROM songs
        INNER JOIN play_history ON songs.id = play_history.songId
        WHERE play_history.isListeningRecord = 1
        GROUP BY songs.id
        ORDER BY MAX(play_history.playedAt) DESC
        LIMIT 500
    """)
    fun getPlayHistory(): Flow<List<SongWithPlayTime>>

    /**
     * 分页获取播放历史
     * 性能: O(n log n) - 支持大数据集的分页加载
     * 用途: 播放历史界面的分页显示
     */
    @Query("SELECT songs.* FROM songs INNER JOIN play_history ON songs.id = play_history.songId GROUP BY songs.id ORDER BY MAX(play_history.playedAt) DESC LIMIT :limit OFFSET :offset")
    suspend fun getPlayHistoryPaged(limit: Int, offset: Int): List<SongEntity>

    /**
     * 分页获取播放历史（包含播放时间）
     * 性能: O(n log n) - 支持大数据集的分页加载
     * 用途: 播放历史界面显示最近播放时间
     * @since 2025-11-19
     * 更新 (2025-11-20): 只返回isListeningRecord=true的记录（聆听足迹）
     */
    @Query("""
        SELECT songs.id, songs.title, songs.artist, songs.album, songs.duration,
               songs.path, songs.albumArt, songs.isLocal, songs.isFavorite, songs.addedAt, songs.source,
               MAX(play_history.playedAt) as playedAt
        FROM songs
        INNER JOIN play_history ON songs.id = play_history.songId
        WHERE play_history.isListeningRecord = 1
        GROUP BY songs.id
        ORDER BY MAX(play_history.playedAt) DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPlayHistoryWithTimePaged(limit: Int, offset: Int): List<SongWithPlayTime>

    /**
     * 插入或更新播放记录
     * 如果歌曲已存在播放记录，则更新播放时间
     * 如果不存在，则插入新记录
     * @since 2025-11-20 重构：改为upsert模式，避免记录无限增长
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(history: PlayHistoryEntity)

    /**
     * 更新指定歌曲的播放时间
     * @return 更新的行数（0表示记录不存在）
     */
    @Query("UPDATE play_history SET playedAt = :playedAt WHERE songId = :songId")
    suspend fun updatePlayedAt(songId: Long, playedAt: Long): Int

    /**
     * 更新指定歌曲的播放时间并标记为聆听足迹
     * 用于歌曲播放>=10秒时调用
     * @param isListeningRecord 是否计入聆听足迹
     * @return 更新的行数（0表示记录不存在）
     * @since 2025-11-20
     */
    @Query("UPDATE play_history SET playedAt = :playedAt, isListeningRecord = :isListeningRecord WHERE songId = :songId")
    suspend fun updatePlayedAtWithListeningFlag(songId: Long, playedAt: Long, isListeningRecord: Boolean): Int

    /**
     * 获取单条播放历史记录
     * 用于检查记录是否存在
     * @since 2025-11-20
     */
    @Query("SELECT * FROM play_history WHERE songId = :songId LIMIT 1")
    suspend fun getPlayHistory(songId: Long): PlayHistoryEntity?

    /**
     * 检查歌曲是否有播放记录
     */
    @Query("SELECT COUNT(*) > 0 FROM play_history WHERE songId = :songId")
    suspend fun hasPlayHistory(songId: Long): Boolean

    /**
     * 获取最新的播放时间（用于触发刷新）
     */
    @Query("SELECT MAX(playedAt) FROM play_history")
    fun observeLatestPlayedAt(): Flow<Long?>

    @Query("DELETE FROM play_history")
    suspend fun clearPlayHistory()

    /**
     * 获取聆听足迹总数（去重后的歌曲数量）
     * 用于显示"聆听足迹 xx首"
     * 更新 (2025-11-20): 只统计isListeningRecord=true的记录
     */
    @Query("SELECT COUNT(DISTINCT songId) FROM play_history WHERE isListeningRecord = 1")
    suspend fun getPlayHistoryCount(): Int

    /**
     * 持续监听聆听足迹总数变化（去重后的歌曲数量）
     * 用于UI显示"聆听足迹 (xx)"
     * 更新 (2025-11-20): 只统计isListeningRecord=true的记录
     */
    @Query("SELECT COUNT(DISTINCT songId) FROM play_history WHERE isListeningRecord = 1")
    fun observePlayHistoryCount(): Flow<Int>

    /**
     * 删除指定歌曲的所有播放历史记录
     * 用于长按删除播放记录功能
     * @since 2025-11-20
     */
    @Query("DELETE FROM play_history WHERE songId = :songId")
    suspend fun deletePlayHistoryBySongId(songId: Long)

    /**
     * 删除超过指定数量的旧聆听足迹记录
     * 用于限制聆听足迹最多200首歌曲
     * 删除策略：保留最近播放的maxCount首聆听足迹歌曲，删除其余的旧记录
     * 更新 (2025-11-20): 只限制isListeningRecord=true的记录数量
     * @param maxCount 最大保留数量（默认200）
     */
    @Query("""
        DELETE FROM play_history
        WHERE isListeningRecord = 1
        AND songId NOT IN (
            SELECT songId FROM play_history
            WHERE isListeningRecord = 1
            GROUP BY songId
            ORDER BY MAX(playedAt) DESC
            LIMIT :maxCount
        )
    """)
    suspend fun trimListeningRecords(maxCount: Int = 200)
}
