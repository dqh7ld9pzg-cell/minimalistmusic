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

package com.minimalistmusic.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.minimalistmusic.data.repository.FreeMusicOnlineRepositoryImpl
import com.minimalistmusic.data.repository.MusicLocalRepositoryImpl
import com.minimalistmusic.data.repository.PlaybackControllerImpl
import com.minimalistmusic.data.repository.SearchHistoryRepositoryImpl
import com.minimalistmusic.data.repository.NeteaseSearchRepositoryImpl
import com.minimalistmusic.data.repository.UserRepositoryImpl
import com.minimalistmusic.domain.repository.MusicLocalRepository
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.domain.repository.PlaybackController
import com.minimalistmusic.domain.repository.SearchHistoryRepository
import com.minimalistmusic.domain.repository.SearchRepository
import com.minimalistmusic.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
/**
 * Repository 依赖注入模块
 *
 * 现有Repository:
 * - LocalMusicRepository: 本地歌曲、收藏、播放列表、播放历史
 * - OnlineMusicRepository: 推荐歌单、歌单详情、播放URL、歌词
 * - PlaybackController: 播放器控制和状态管理
 * - SearchRepository: 统一搜索
 * - SearchHistoryRepository: 搜索历史管理
 * - UserRepository: 用户认证和同步
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /**
     * 本地音乐仓库
     * 职责：本地歌曲、收藏、播放列表、播放历史
     */
    @Binds
    @Singleton
    abstract fun bindLocalMusicRepository(
        localMusicRepositoryImpl: MusicLocalRepositoryImpl,
    ): MusicLocalRepository
    /**
     * 在线音乐仓库
     * 职责：推荐歌单、歌单详情、播放URL、歌词
     */
    @Binds
    @Singleton
    abstract fun bindOnlineMusicRepository(
        onlineMusicRepositoryImpl: FreeMusicOnlineRepositoryImpl,
    ): MusicOnlineRepository
    /**
     * 搜索历史仓库
     * 职责：搜索历史的增删查
     */
    @Binds
    @Singleton
    abstract fun bindSearchMusicHistoryRepository(
        searchHistoryRepository: SearchHistoryRepositoryImpl,
    ): SearchHistoryRepository
    /**
     * 统一搜索仓库
     * 职责：整合多个音乐平台的搜索结果
     */
    @Binds
    @Singleton
    abstract fun bindSearchMusicRepository(
        searchRepositoryImpl: NeteaseSearchRepositoryImpl,
    ): SearchRepository
    /**
     * 用户仓库
     * 职责：用户认证、喜欢列表和播放历史的云端同步
     */
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl,
    ): UserRepository

    /**
     * 播放器仓库
     * 职责：封装MusicService，提供播放控制接口和状态管理
     */
    @OptIn(UnstableApi::class)
    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        playbackControllerImpl: PlaybackControllerImpl,
    ): PlaybackController
}
