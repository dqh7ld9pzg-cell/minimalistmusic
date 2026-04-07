package com.minimalistmusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成应用关键用户旅程的基准配置文件
 *
 * 覆盖以下关键路径：
 * 1. 应用冷启动
 * 2. 导航到发现页（通过底部导航栏）
 * 3. 在发现页滚动浏览推荐歌单
 * 4. 点击歌单进入详情页
 * 5. 在歌单详情页滚动浏览歌曲列表
 * 6. 返回发现页
 *
 * 注意：此生成器假设标准屏幕尺寸（如 1080x2340），
 * 在不同设备上可能需要调整坐标。建议在生成基准配置文件的设备上验证交互。
 *
 * 运行方式：
 * 1. 在 Android Studio 中运行 "Generate Baseline Profile" 配置
 * 2. 或执行 Gradle 任务：./gradlew :app:generateReleaseBaselineProfile
 *
 * 注意：生成基准配置文件需要 API 33+ 或已 root 的 API 28+ 设备
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AppCriticalUserJourneyProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateCriticalUserJourneyProfile() {
        // 从 instrumentation 参数读取应用 ID
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),
            includeInStartupProfile = true
        ) {
            // ========== 1. 应用冷启动 ==========
            pressHome()
            startActivityAndWait()

            // 等待应用完全启动
            device.waitForIdle()
            // 额外等待首屏内容加载
            Thread.sleep(1000)

            // ========== 2. 导航到发现页 ==========
            // 启动后默认在首页，需要点击底部导航栏的"发现"标签
            // 底部导航栏高度通常为 56dp，三个标签平均分布
            // 点击第二个标签（位置：屏幕宽度的一半，屏幕底部向上 28dp 处）
            val displayWidth = device.displayWidth
            val displayHeight = device.displayHeight
            val navBarHeight = 56 // dp，假设标准导航栏高度
            // 转换为像素（假设屏幕密度为 420 dpi，1 dp ≈ 2.625 像素）
            // 简化：点击屏幕底部向上 100 像素的位置
            val clickY = displayHeight - 100
            // 第二个标签的 X 坐标（屏幕宽度的一半）
            val clickX = displayWidth / 2

            device.click(clickX, clickY)
            device.waitForIdle()
            Thread.sleep(800) // 等待页面切换

            // ========== 3. 在发现页滚动浏览推荐歌单 ==========
            // 向上滚动以加载更多内容
            repeat(2) {
                device.swipe(
                    displayWidth / 2,
                    displayHeight * 3 / 4,
                  displayWidth / 2,
                    displayHeight / 4,
                     10
                )
                device.waitForIdle()
                Thread.sleep(300)
            }

            // ========== 4. 点击第一个歌单进入详情页 ==========
            // 假设第一个歌单在屏幕上半部分中央
            // 点击位置：屏幕宽度一半，屏幕高度 1/3 处
            val playlistClickX = displayWidth / 2
            val playlistClickY = displayHeight / 3

            device.click(playlistClickX, playlistClickY)
            device.waitForIdle()
            Thread.sleep(1500) // 等待歌单详情页加载

            // ========== 5. 在歌单详情页滚动浏览歌曲列表 ==========
            // 向下滚动歌曲列表
            repeat(2) {
                device.swipe(
                     displayWidth / 2,
                    displayHeight * 2 / 3,
                     displayWidth / 2,
                   displayHeight / 3,
                     10
                )
                device.waitForIdle()
                Thread.sleep(300)
            }

            // ========== 6. 返回发现页 ==========
            device.pressBack()
            device.waitForIdle()
            Thread.sleep(800)

            // ========== 7. 返回首页 ==========
            // 点击底部导航栏的第一个标签（首页）
            val homeClickX = displayWidth / 4 // 第一个标签在左侧 1/4 处
            val homeClickY = displayHeight - 100

            device.click(homeClickX, homeClickY)
            device.waitForIdle()
            Thread.sleep(500)

            // 最后等待所有操作完成
            device.waitForIdle()
        }
    }
}