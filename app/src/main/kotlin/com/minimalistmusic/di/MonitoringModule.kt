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

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.minimalistmusic.BuildConfig
import com.minimalistmusic.performance.config.PerformanceConfig
import com.minimalistmusic.performance.monitor.MemoryPerformanceMonitor
import com.minimalistmusic.performance.monitor.NetworkPerformanceMonitor
import com.minimalistmusic.performance.monitor.PerformanceMonitor
import com.minimalistmusic.performance.monitor.StartupPerformanceMonitor
import com.minimalistmusic.performance.monitor.UIPerformanceMonitor
import com.minimalistmusic.performance.reporter.PerformanceReporter
import com.minimalistmusic.performance.reporter.PerformanceStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton
/**
 * 性能监控模块依赖注入配置
 *
 * 设计原则：
 * - 单一职责：每个类有明确的职责边界
 * - 依赖注入：通过构造函数注入依赖
 * - 生命周期：合理管理组件生命周期
 * - 可测试性：便于单元测试和集成测试
 */
@Module
@InstallIn(SingletonComponent::class)
object MonitoringModule {
    @Provides
    @Singleton
    fun providePerformanceConfig(): PerformanceConfig {
        return if (BuildConfig.DEBUG) {
            PerformanceConfig.development()
        } else {
            PerformanceConfig.production()
        }
    }
    @Provides
    @Singleton
    fun providePerformanceStorage(
        @ApplicationContext context: Context
    ): PerformanceStorage {
        return PerformanceStorage(context)
    }
    @Provides
    @Singleton
    fun providePerformanceReporter(
        okHttpClient: OkHttpClient,
        performanceStorage: PerformanceStorage,
        @ApplicationContext context: Context
    ): PerformanceReporter {
        return PerformanceReporter(okHttpClient, performanceStorage, context)
    }
    @Provides
    @Singleton
    fun providePerformanceMonitor(
        @ApplicationContext context: Context,
        performanceReporter: PerformanceReporter,
        config: PerformanceConfig
    ): PerformanceMonitor {
        return PerformanceMonitor(context as Application, performanceReporter, config)
    }
    @Provides
    @Singleton
    fun provideMemoryPerformanceMonitor(
        performanceMonitor: PerformanceMonitor,
        @ApplicationContext context: Context,
        config: PerformanceConfig
    ): MemoryPerformanceMonitor {
        return MemoryPerformanceMonitor(performanceMonitor, context, config.memoryConfig)
    }
    @Provides
    @Singleton
    fun provideNetworkPerformanceMonitor(
        performanceMonitor: PerformanceMonitor,
        @ApplicationContext context: Context
    ): NetworkPerformanceMonitor {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return NetworkPerformanceMonitor(performanceMonitor, connectivityManager)
    }
    @Provides
    @Singleton
    fun provideStartupPerformanceMonitor(
        performanceMonitor: PerformanceMonitor
    ):  StartupPerformanceMonitor{
        return StartupPerformanceMonitor(performanceMonitor)
    }
    @Provides
    @Singleton
    fun provideUIPerformanceMonitor(
        performanceMonitor: PerformanceMonitor
    ): UIPerformanceMonitor {
        return UIPerformanceMonitor(performanceMonitor)
    }
}