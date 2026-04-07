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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.minimalistmusic.util.LogConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
/**
 * 图片查看器组件
 *
 * 功能：
 * - 全屏查看大图
 * - 半透明蒙版背景（可以看到底层页面）
 * - 图片区域不透明
 * - 支持保存图片到相册
 * - 点击图片外区域关闭
 * - 使用Coil缓存，不重复下载图片
 * - 检测重复保存，快速提示
 * - 按原始宽高比例显示，不变形不裁剪
 * - 保存按钮防抖（300ms）
 *
 * 优化 (2025-11-18):
 * - 图片显示：fillMaxSize + Fit，保持原始宽高比
 * - 左右padding固定16.dp，上下padding 64.dp
 * - 使用Coil磁盘缓存，不重复下载
 * - 保存完成后立即停止动画
 * - 检测文件已存在，快速提示
 * - 防抖机制：忽略300ms内的重复点击
 *
 * @param imageUrl 图片URL
 * @param imageName 保存图片的文件名（不含扩展名）
 * @param onDismiss 关闭回调
 *
 * @since 2025-11-18
 */
@Composable
fun ImageViewer(
    imageUrl: String,
    imageName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // 缓存的图片Bitmap，用于保存时直接使用
    var cachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    // 防抖：记录上次点击时间
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceInterval = 1000L // 300ms防抖间隔
    // 预加载图片到内存（使用Coil缓存，不会重复下载）
    LaunchedEffect(imageUrl) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // 禁用硬件加速，以便获取Bitmap
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    cachedBitmap = drawable.bitmap
                }
            }
        } catch (e: Exception) {
            LogConfig.e(LogConfig.TAG_PLAYER_UI,
                "ImageViewer 预加载图片到内存失败: ${e.message}")
        }
    }
    // 保存图片到相册（带防抖）
    fun saveImageToGallery() {
        // 防抖检查
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < debounceInterval) {
            return // 忽略快速连续点击
        }
        lastClickTime = currentTime
        if (isDownloading) return
        coroutineScope.launch {
            try {
                val bitmap = cachedBitmap
                if (bitmap == null) {
                    snackbarHostState.showSnackbar("图片加载中，请稍后再试")
                    return@launch
                }
                // 检查文件是否已存在
                val fileName = "$imageName.jpg"
                val existingFile = checkFileExists(context, fileName)
                if (existingFile) {
                    // 已存在，快速提示
                    snackbarHostState.showSnackbar("图片已保存到相册")
                    return@launch
                }
                // 开始保存动画
                isDownloading = true
                val result = withContext(Dispatchers.IO) {
                    saveToGallery(context, bitmap, imageName)
                }
                // 保存完成后立即停止动画
                isDownloading = false
                if (result) {
                    snackbarHostState.showSnackbar("图片已保存到相册")
                } else {
                    snackbarHostState.showSnackbar("保存失败，请检查存储权限")
                }
            } catch (e: Exception) {
                LogConfig.e(LogConfig.TAG_PLAYER_UI,
                    "ImageViewer 保存图片到相册失败: ${e.message}")
                isDownloading = false
                snackbarHostState.showSnackbar("保存失败: ${e.message}")
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)) // 半透明蒙版
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        // 图片显示区域（按原始宽高比例显示）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "大图预览",
                modifier = Modifier
                    .fillMaxSize() // 填充可用空间
                    .clickable(
                        onClick = { /* 点击图片不关闭 */ },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentScale = ContentScale.Fit // Fit模式保持原始宽高比，不裁剪不变形
            )
        }
        // 关闭按钮（左上角）
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
        }
        // 下载按钮（右下角）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { saveImageToGallery() },
                enabled = !isDownloading,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "保存到相册",
                        tint = Color.White
                    )
                }
            }
        }
        // Snackbar提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}
/**
 * 检查文件是否已存在
 *
 * @return true 如果文件已存在
 */
private fun checkFileExists(context: Context, fileName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用MediaStore查询
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                it.count > 0
            } ?: false
        } else {
            // Android 9 及以下，检查文件系统
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            )
            val appDir = File(picturesDir, "MinimalistMusic")
            val file = File(appDir, fileName)
            file.exists()
        }
    } catch (e: Exception) {
        LogConfig.e(LogConfig.TAG_PLAYER_UI,
            "ImageViewer 检查文件是否存在时异常: ${e.message}")
        false
    }
}
/**
 * 保存图片到相册
 *
 * 兼容Android 10+的MediaStore API
 * 图片来源：使用Coil磁盘缓存，不重复下载
 */
private suspend fun saveToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MinimalistMusic")
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return@withContext false
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri)
        outputStream?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        true
    } catch (e: Exception) {
        LogConfig.e(LogConfig.TAG_PLAYER_UI,
            "ImageViewer 保存图片到相册失败: ${e.message}")
        false
    }
}
