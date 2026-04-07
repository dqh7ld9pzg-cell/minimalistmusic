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

package com.minimalistmusic.domain.usecase.player

import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.domain.repository.MusicOnlineRepository
import com.minimalistmusic.util.LyricLine
import com.minimalistmusic.util.LyricParser
import com.minimalistmusic.util.Result
import javax.inject.Inject
/**
 * 加载歌词 UseCase
 *
 * 职责：
 * - 根据歌曲类型（本地/在线）从不同数据源获取歌词
 * - 本地歌曲：从本地 LRC 文件加载
 * - 在线歌曲：从在线音乐服务获取
 * - 解析歌词为结构化数据
 *
 * 重构说明（2025-11-11）:
 * - 迁移依赖：FreeMusicRepositoryImpl (data层) → OnlineMusicRepository (domain层)
 * - 原因：UseCase 应该依赖 domain 层的抽象接口，而不是 data 层的具体实现
 * - 好处：符合依赖倒置原则，提高代码的可测试性和可维护性
 */
class LoadLyricsUseCase @Inject constructor(
    private val musicOnlineRepository: MusicOnlineRepository
) {
    suspend operator fun invoke(song: Song): List<LyricLine> {
        return if (song.isLocal) {
            // 本地歌曲，尝试加载本地lrc文件
            val lrcContent = song.path?.let { LyricParser.extractLyricFromLocalFile(it) }
            lrcContent?.let { LyricParser.parse(it) } ?: emptyList()
        } else {
            // 在线歌曲，从API获取歌词
            when (val result = musicOnlineRepository.getLyric(song.id)) {
                is Result.Success -> LyricParser.parse(result.data)
                else -> emptyList()
            }
        }
    }
}