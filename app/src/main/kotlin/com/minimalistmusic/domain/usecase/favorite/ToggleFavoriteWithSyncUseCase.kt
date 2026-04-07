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

package com.minimalistmusic.domain.usecase.favorite

import com.minimalistmusic.data.local.UserPreferencesDataStore
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.util.Result
import javax.inject.Inject
/**
 * 切换歌曲喜欢状态并自动同步 UseCase
 *
 * 职责：
 * - 切换指定歌曲的喜欢状态（收藏/取消收藏）
 * - 如果用户已登录且开启了同步，自动同步到云端
 * - 统一错误处理
 *
 * 复用场景：
 * - AccountSyncViewModel：处理所有页面的爱心点击
 * - PlayerViewModel：播放器页面的爱心按钮
 * - 所有显示歌曲列表的页面（通过 AccountSyncViewModel）
 *
 * 重构说明（2025-11-11）:
 * - 迁移依赖：MusicRepository → LocalMusicRepository
 * - 原因：此 UseCase 只涉及本地收藏数据操作，应使用 LocalMusicRepository
 *
 * 注意：此 UseCase 不处理"未登录时的同步提醒逻辑"，
 * 那部分逻辑应该由 ViewModel 层处理（UI 相关逻辑）
 */
class ToggleFavoriteWithSyncUseCase @Inject constructor(
    private val musicLocalRepository: MusicLocalRepository,
    private val syncFavoritesUseCase: SyncFavoritesToCloudUseCase,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    /**
     * 执行切换操作
     *
     * @param song 要切换喜欢状态的歌曲
     * @return Result<Boolean> 成功时返回切换后的状态（true=已收藏，false=已取消收藏）
     */
    suspend operator fun invoke(song: Song): Result<Boolean> {
        return try {
            // 1. 切换喜欢状态
            musicLocalRepository.toggleFavorite(song)
            // 2. 获取切换后的状态（通过查询数据库）
            val isFavorite = musicLocalRepository.isFavorite(song.id)
            // 3. 如果已登录且开启了同步，自动同步到云端
            if (userPreferencesDataStore.isLoggedIn.value && userPreferencesDataStore.syncFavorites.value) {
                // 异步同步，不阻塞主流程，同步失败不影响本地操作
                syncFavoritesUseCase()
            }
            Result.Success(isFavorite)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}