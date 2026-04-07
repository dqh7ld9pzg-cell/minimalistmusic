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
import com.minimalistmusic.data.local.entity.FavoritePlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * 收藏歌单数据访问对象 (2025-11-16)
 *
 * 用于管理用户收藏的在线歌单
 * 功能: 添加收藏、移除收藏、查询收藏列表、检查是否已收藏
 */
@Dao
interface FavoritePlaylistDao {
    /**
     * 获取所有收藏的歌单
     * 按收藏时间倒序排列（最近收藏的在最前面）
     */
    @Query("SELECT * FROM favorite_playlists ORDER BY favoritedAt DESC")
    fun getAllFavoritePlaylists(): Flow<List<FavoritePlaylistEntity>>

    /**
     * 检查歌单是否已收藏
     */
    @Query("SELECT COUNT(*) > 0 FROM favorite_playlists WHERE playlistId = :playlistId")
    suspend fun isFavorited(playlistId: Long): Boolean

    /**
     * 插入收藏歌单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoritePlaylist(playlist: FavoritePlaylistEntity)

    /**
     * 移除收藏歌单
     */
    @Query("DELETE FROM favorite_playlists WHERE playlistId = :playlistId")
    suspend fun deleteFavoritePlaylist(playlistId: Long)

    /**
     * 清空所有收藏歌单
     */
    @Query("DELETE FROM favorite_playlists")
    suspend fun clearAllFavoritePlaylists()
}
