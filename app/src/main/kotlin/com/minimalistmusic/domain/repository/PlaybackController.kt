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

import com.minimalistmusic.domain.model.Song

/**
 * 播放器Repository接口
 *
 * 架构目的:
 * - 解耦ViewModel和MusicService的直接依赖
 * - 遵循依赖倒置原则：ViewModel依赖抽象接口而非具体实现
 * - 提升可测试性：可以mock PlaybackRepository进行单元测试
 * - 简化ViewModel职责：将Service绑定、生命周期管理等基础设施代码移至Repository层
 *
 * 职责分离:
 * - 播放控制：play、pause、next、previous等操作
 * - Service管理：绑定、解绑、生命周期管理
 * - 状态管理：由PlaybackStateManager统一管理（ViewModel直接依赖）
 *
 * 架构说明:
 * - PlaybackController 只负责播放控制，不管理状态
 * - PlaybackState 由 PlaybackStateManager (@Singleton) 统一管理
 * - ViewModel 同时注入 PlaybackController 和 PlaybackStateManager
 *
 * 使用场景：
 * - PlayerViewModel: 核心播放控制
 * - MiniPlayerUI: 迷你播放器
 * - 通知栏控制: 后台播放控制
 */
interface PlaybackController {

    /**
     * 绑定MusicService
     *
     * 在Application创建时调用
     * Repository内部管理Service生命周期
     */
    fun bindService()

    /**
     * 解绑MusicService
     *
     * 在Application或Activity销毁时调用
     */
    fun unbindService()

    /**
     * 播放歌曲列表
     *
     * @param songs 歌曲列表
     * @param startIndex 起始播放索引
     */
    suspend fun playSongs(songs: List<Song>, startIndex: Int = 0)


    /**
     * 暂停播放
     */
    fun pause()

    /**
     * 切换播放/暂停状态
     */
    fun togglePlayPause()

    /**
     * 跳到下一首
     */
    fun skipToNext()

    /**
     * 跳到上一首
     */
    fun skipToPrevious()

    /**
     * 跳转到指定播放位置
     *
     * @param position 播放位置（毫秒）
     */
    fun seekTo(position: Long)

    /**
     * 切换播放模式
     *
     * 循环切换：顺序播放 → 单曲循环 → 随机播放
     */
    fun togglePlayMode()

    /**
     * 从播放列表中移除指定歌曲
     *
     * @param songId 要移除的歌曲ID
     */
    fun removeSongFromPlaylist(songId: Long)

    /**
     * 更新歌曲并跳转到指定位置
     *
     * 用于URL失效后更新歌曲URL并恢复播放
     *
     * @param index 歌曲索引
     * @param song 更新后的歌曲对象
     */
    fun updateSongAndSeek(index: Int, song: Song)

    /**
     * 设置URL失效监听器
     *
     * 当播放器检测到403错误时触发
     *
     * @param listener 监听器回调，参数为(songId, currentIndex)
     */
    fun setOnUrlExpiredListener(listener: ((Long, Int) -> Unit)?)
}
