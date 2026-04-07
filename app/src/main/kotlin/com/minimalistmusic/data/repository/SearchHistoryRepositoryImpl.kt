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

import com.minimalistmusic.data.local.dao.SearchHistoryDao
import com.minimalistmusic.data.local.entity.SearchHistoryEntity
import com.minimalistmusic.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索历史仓库 - 处理搜索历史管理
 *
 * 职责：
 * - 搜索历史管理 (增删查)
 *
 * 重构说明:
 * - 2025-11-12: 删除未使用的 searchAll() 方法和 NeteaseCloudMusicApiService 依赖
 * - 2025-11-10: 更新依赖: MusicApiService -> NeteaseCloudMusicApiService
 *
 * 说明:
 * - 实际的搜索功能由 UnifiedSearchRepositoryImpl 实现
 * - 此Repository仅负责搜索历史的本地存储
 */
@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) : SearchHistoryRepository {
    /**
     * 获取搜索历史
     */
    override fun getSearchHistory(): Flow<List<String>> {
        return searchHistoryDao.getSearchHistory().map { entities ->
            entities.map { it.keyword }
        }
    }
    /**
     * 添加搜索历史
     *
     * 去重逻辑 (2025-11-17):
     * - 先删除已存在的相同关键词的历史记录
     * - 再插入新记录（时间戳为当前时间）
     * - 确保同一关键词在历史列表中只显示一次，且始终显示最新的搜索
     *
     * 数量限制 (2025-11-21):
     * - 插入新记录后，保留最近6条记录
     * - 删除超过6条的旧记录，防止数据库无限增长
     * - 符合MD3简洁原则和用户使用习惯
     */
    override suspend fun addSearchHistory(keyword: String) {
        // 先删除已存在的相同关键词（去重）
        searchHistoryDao.deleteByKeyword(keyword)
        // 插入新记录（时间戳为当前时间，会显示在列表最前面）
        val history = SearchHistoryEntity(keyword = keyword)
        searchHistoryDao.insertHistory(history)
        // 限制搜索历史最多6条（2025-11-21）
        searchHistoryDao.trimSearchHistory(maxCount = 6)
    }
    /**
     * 清空搜索历史
     */
    override suspend fun clearSearchHistory() {
        searchHistoryDao.clearHistory()
    }
    /**
     * 删除单条搜索历史
     *
     * 新增 (2025-11-21): 支持用户删除单条历史记录
     * 复用已有的 deleteByKeyword 方法
     *
     * @param keyword 要删除的关键词
     */
    override suspend fun deleteSearchHistory(keyword: String) {
        searchHistoryDao.deleteByKeyword(keyword)
    }
}
