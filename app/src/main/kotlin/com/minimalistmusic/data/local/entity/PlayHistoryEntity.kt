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
 * 播放历史实体
 *
 * 性能优化说明 (2025-11-12):
 * - 添加复合索引: (songId, playedAt) - 优化分组和排序查询
 * - 原有的 songId 索引被复合索引替代（复合索引的第一列可以单独使用）
 * - 加速 GROUP BY songId + ORDER BY playedAt 的复杂查询
 *
 * 架构优化 (2025-11-20):
 * - 添加 isListeningRecord 字段：区分"播放记录"和"聆听足迹"
 * - 播放记录：所有播放过的歌曲（无论播放时长）
 * - 聆听足迹：播放时长>=10秒的歌曲（显示在"聆听足迹"界面）
 * - 限制200首：仅限制聆听足迹数量，播放记录数量由总表限制控制
 */
@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["songId", "playedAt"]),  // 复合索引：优化 JOIN + GROUP BY + ORDER BY 查询
        Index(value = ["isListeningRecord"])     // 加速聆听足迹筛选 (2025-11-20)
    ]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val isListeningRecord: Boolean = false  // 是否计入聆听足迹（播放>=10秒）(2025-11-20)
)
