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

package com.minimalistmusic.domain.usecase.history

import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.util.Result
import javax.inject.Inject
/**
 * 分页获取播放历史 UseCase
 *
 * 职责：
 * - 从本地数据库分页查询播放历史记录
 * - 统一错误处理
 *
 * 重构说明（2025-11-11）:
 * - 迁移依赖：MusicRepository → LocalMusicRepository
 * - 原因：播放历史存储在本地数据库，应使用 LocalMusicRepository
 */
class GetPagedPlayHistoryUseCase @Inject constructor(
    private val musicLocalRepository: MusicLocalRepository
) {
    /**
     * 分页获取播放历史记录
     *
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 返回一个包含歌曲列表或异常的Result
     */
    suspend operator fun invoke(limit: Int, offset: Int): Result<List<Song>> {
        return try {
            val songs = musicLocalRepository.getPlayHistoryPaged(limit, offset)
            Result.Success(songs)
        } catch (e: Exception) {
            // UseCase只负责捕获异常并将其包装成Error，不直接处理UI状态
            Result.Error(e)
        }
    }
}