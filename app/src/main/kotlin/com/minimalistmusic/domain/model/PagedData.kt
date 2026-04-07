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

package com.minimalistmusic.domain.model

/**
 * 分页数据封装
 *
 * 用于Repository层返回带有分页信息的数据
 *
 * @param T 数据类型
 * @property songsList 数据列表
 * @property hasMore 是否还有更多数据
 * @property total 总数据量（可选）
 */
data class PagedData<T>(
    val songsList: List<T>,
    val hasMore: Boolean,
    val total: Int = 0
)
