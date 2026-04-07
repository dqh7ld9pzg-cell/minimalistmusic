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

package com.minimalistmusic.data.local.entity

import androidx.room.*

/**
 * 搜索历史实体
 *
 * 性能优化说明 (2025-11-12):
 * - 添加索引: searchedAt - 加速按时间排序查询
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["searchedAt"])    // 加速 ORDER BY searchedAt 查询
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val searchedAt: Long = System.currentTimeMillis()
)
