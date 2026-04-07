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
import com.minimalistmusic.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 搜索历史数据访问对象
 */
@Dao
interface SearchHistoryDao {
    /**
     * 获取搜索历史（最多6条）
     *
     * MD3优化 (2025-11-21):
     * - 从10条减少到6条，符合MD3简洁原则
     * - 6条可在一屏内展示，无需滚动
     * - 符合用户高频搜索习惯（通常重复搜索最近几个关键词）
     * - 参考主流应用（Google、网易云音乐等）的5-7条标准
     */
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 6")
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SearchHistoryEntity)

    @Delete
    suspend fun deleteHistory(history: SearchHistoryEntity)

    /**
     * 根据关键词删除搜索历史
     * 用于去重：在插入新记录前删除同名的旧记录
     */
    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    /**
     * 限制搜索历史数量（2025-11-21）
     *
     * 删除超过maxCount条的旧记录，防止数据库无限增长
     * 保留最近的maxCount条记录（按searchedAt降序）
     *
     * SQL逻辑：
     * - 删除不在最近maxCount条记录中的所有记录
     * - 使用子查询获取最近maxCount条记录的ID
     * - 删除ID不在子查询结果中的所有记录
     *
     * @param maxCount 最大保留数量（默认6条）
     */
    @Query("""
        DELETE FROM search_history
        WHERE id NOT IN (
            SELECT id FROM search_history
            ORDER BY searchedAt DESC
            LIMIT :maxCount
        )
    """)
    suspend fun trimSearchHistory(maxCount: Int = 6)
}
