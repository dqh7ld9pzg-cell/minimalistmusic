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

package com.minimalistmusic.domain.repository

import kotlinx.coroutines.flow.Flow
/**
 * 为所有搜索相关的操作定义的统一领域层接口.
 */
interface SearchHistoryRepository {
    // --- 搜索历史相关 ---
    fun getSearchHistory(): Flow<List<String>>
    suspend fun addSearchHistory(keyword: String)
    suspend fun clearSearchHistory()
    /**
     * 删除单条搜索历史
     * @param keyword 要删除的关键词
     * @since 2025-11-21
     */
    suspend fun deleteSearchHistory(keyword: String)
}