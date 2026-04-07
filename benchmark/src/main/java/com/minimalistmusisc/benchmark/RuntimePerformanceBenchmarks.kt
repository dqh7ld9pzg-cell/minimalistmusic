package com.minimalistmusisc.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 运行时性能基准测试
 *
 * 测量应用关键用户流程的性能，对比优化（使用基准配置文件）和未优化版本的差异。
 *
 * 测试覆盖：
 * 1. 发现页滚动性能（帧率）
 * 2. 歌单详情页加载性能
 * 3. 搜索页面加载性能
 *
 * 每个测试都会在两种编译模式下运行：
 * - CompilationMode.None: 未优化版本（无基准配置文件）
 * - CompilationMode.Partial: 优化版本（使用基准配置文件）
 *
 * 运行方式：
 * 1. 在 Android Studio 中运行特定测试方法
 * 2. 或执行 Gradle 任务：./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
 *
 * 注意：需要在物理设备上运行，模拟器无法提供准确的性能数据。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RuntimePerformanceBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    // 从 instrumentation 参数读取应用 ID，回退到硬编码包名
    private val packageName: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "com.minimalistmusic"

    /**
     * 测试发现页滚动性能（帧率）
     *
     * 测量在发现页连续滚动时的帧率，评估列表渲染性能。
     * 使用 FrameTimingMetric 收集帧时间数据。
     */
    @Test
    fun discoverScrollPerformanceNone() =
        measureScrollPerformance(CompilationMode.None())

    @Test
    fun discoverScrollPerformanceWithBaselineProfile() =
        measureScrollPerformance(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureScrollPerformance(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                // 冷启动应用
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
                Thread.sleep(1000)

                // 导航到发现页
                navigateToDiscoverPage()
            }
        ) {
            // 测量块：执行滚动操作
            // 向上滚动（模拟用户浏览歌单）
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                20 // 较慢的滚动，模拟真实用户交互
            )
            device.waitForIdle()

            // 向下滚动返回
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight / 4,
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                20
            )
            device.waitForIdle()
        }
    }

    /**
     * 测试歌单详情页加载性能
     *
     * 测量从发现页点击歌单到详情页完全加载的时间。
     * 使用 StartupTimingMetric 测量页面加载时间。
     */
    @Test
    fun playlistDetailLoadPerformanceNone() =
        measurePlaylistDetailLoadPerformance(CompilationMode.None())

    @Test
    fun playlistDetailLoadPerformanceWithBaselineProfile() =
        measurePlaylistDetailLoadPerformance(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measurePlaylistDetailLoadPerformance(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
                Thread.sleep(1000)

                // 导航到发现页
                navigateToDiscoverPage()
                device.waitForIdle()
                Thread.sleep(800)
            }
        ) {
            // 点击第一个歌单（假设在屏幕上半部分中央）
            device.click(
                 device.displayWidth / 2,
                device.displayHeight / 3
            )
            // 等待详情页加载完成
            device.waitForIdle()
            Thread.sleep(1000) // 确保内容完全加载

            // 返回发现页，准备下一次迭代
            device.pressBack()
            device.waitForIdle()
            Thread.sleep(500)
        }
    }

    /**
     * 测试搜索页面加载性能
     *
     * 测量从首页点击搜索图标到搜索页面完全加载的时间。
     * 使用 StartupTimingMetric 测量页面加载时间。
     */
    @Test
    fun searchPageLoadPerformanceNone() =
        measureSearchPageLoadPerformance(CompilationMode.None())

    @Test
    fun searchPageLoadPerformanceWithBaselineProfile() =
        measureSearchPageLoadPerformance(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun measureSearchPageLoadPerformance(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                device.waitForIdle()
                Thread.sleep(1000)
            }
        ) {
            // 假设首页有搜索图标（根据实际UI调整坐标）
            // 点击搜索图标（假设在屏幕右上角）
            device.click(
                device.displayWidth - 100, // 右侧 100 像素
                100 // 顶部 100 像素
            )
            // 等待搜索页面加载完成
            device.waitForIdle()
            Thread.sleep(800)

            // 返回首页，准备下一次迭代
            device.pressBack()
            device.waitForIdle()
            Thread.sleep(500)
        }
    }
}

private fun androidx.benchmark.macro.MacrobenchmarkScope.navigateToDiscoverPage() {
    val displayWidth = device.displayWidth
    val displayHeight = device.displayHeight

    // 点击底部导航栏的第二个标签（发现）
    val clickX = displayWidth / 2
    val clickY = displayHeight - 100

    device.click(clickX, clickY)
    device.waitForIdle()
    Thread.sleep(800) // 等待页面切换
}