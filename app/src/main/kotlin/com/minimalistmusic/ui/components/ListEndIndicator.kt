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

package com.minimalistmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
/**
 * 列表底线指示器组件
 *
 * 当用户滑动到列表底部时显示，提供视觉反馈和快速返回顶部的提示。
 * 符合 Material Design 3 设计规范。
 *
 * @param itemCount 列表总条目数
 * @param titleText 页面标题文字（用于"点击xx可快速滑至顶部"提示）
 * @param onScrollToTop 点击时滚动到顶部的回调
 */
@Composable
fun ListEndIndicator(
    itemCount: Int,
    titleText: String = "左上角标题",
    onScrollToTop: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 装饰线条和文字
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧曲线
            Divider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(end = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 1.dp
            )
            // "我是底线"文字
            Text(
                text = "我是底线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            // 右侧曲线
            Divider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(start = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 1.dp
            )
        }
        // 当记录条数大于50条时，显示快速返回顶部提示
        if (itemCount >=10 && onScrollToTop != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击$titleText 可快速滑至顶部",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onScrollToTop() }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
