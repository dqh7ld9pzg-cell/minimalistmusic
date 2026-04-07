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

package com.minimalistmusic.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
/**
 * Material Design 3 间距规范 (2025-11-28)
 *
 * 统一所有页面的间距，符合MD3设计规范
 * 参考：https://m3.material.io/foundations/layout/applying-layout/spacing
 */
object Spacing {
    /**
     * 标准水平内边距：16dp
     * 用于：LazyColumn、LazyRow等列表容器的左右边距
     */
    val standardHorizontal = 16.dp
    /**
     * 标准垂直内边距：16dp（优化 2025-11-29）
     * 用于：LazyColumn顶部和底部的边距
     * MD3 官方推荐值：16dp，提供更好的呼吸感，与水平间距保持一致
     */
    val standardVertical = 16.dp
    /**
     * 标准内容间距
     */
    object Content {
        /** 极小间距：4dp - 用于紧密相关的内容（如搜索历史项） */
        val extraSmall = 4.dp
        /** 小间距：8dp - 用于一般列表项（如歌曲列表） */
        val small = 8.dp
        /** 中等间距：12dp - 用于模块内的内容分组 */
        val medium = 12.dp
        /** 大间距：16dp - 用于卡片之间、模块之间 */
        val large = 16.dp
        /** 超大间距：20dp - 用于重要模块分隔 */
        val extraLarge = 20.dp
    }
    /**
     * 标准LazyColumn内边距配置
     *
     * @param horizontal 水平内边距（默认16dp）
     * @param vertical 垂直内边距（默认16dp）
     */
    fun lazyColumnPadding(
        horizontal: androidx.compose.ui.unit.Dp = standardHorizontal,
        vertical: androidx.compose.ui.unit.Dp = standardVertical
    ): PaddingValues = PaddingValues(horizontal = horizontal, vertical = vertical)
    /**
     * 仅水平内边距的LazyColumn配置
     * 用于：已经通过Scaffold的paddingValues处理了顶部/底部间距的场景
     *
     * @param padding 水平内边距（默认16dp）
     */
    fun lazyColumnHorizontalPadding(
        padding: androidx.compose.ui.unit.Dp = standardHorizontal
    ): PaddingValues = PaddingValues(horizontal = padding)
    /**
     * 统一的内边距（四个方向相同）
     *
     * @param all 所有方向的内边距（默认16dp）
     */
    fun allPadding(
        all: androidx.compose.ui.unit.Dp = standardHorizontal
    ): PaddingValues = PaddingValues(all = all)
}
