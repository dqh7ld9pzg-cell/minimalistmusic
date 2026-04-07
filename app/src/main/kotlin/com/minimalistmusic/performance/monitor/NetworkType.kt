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

package com.minimalistmusic.performance.monitor

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.minimalistmusic.performance.metric.MetricType
import com.minimalistmusic.performance.metric.PerformanceMetric
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException

/**
 * 网络类型枚举
 */
enum class NetworkType {
    WIFI, CELLULAR, VPN, ETHERNET, BLUETOOTH, LOWPAN, OTHER, NONE, UNKNOWN
}
/**
 * 网络性能监控器
 * 
 * 监控目标详解：
 * 
 * 1. 请求成功率监控
 *   - 问题：API接口不可用或返回错误
 *   - 影响：核心功能失效，用户体验差
 *   - 监控：HTTP状态码、网络异常类型
 *   - 解决：优化重试机制，改进错误处理
 * 
 * 2. 请求耗时分析
 *   - 问题：接口响应慢，用户等待时间长
 *   - 影响：用户流失，转化率下降
 *   - 监控：DNS、连接、TLS、请求、响应各阶段耗时
 *   - 解决：CDN优化、接口拆分、缓存策略
 * 
 * 3. 网络质量监控
 *   - 问题：弱网环境下体验差
 *   - 影响：用户满意度下降，差评增加
 *   - 监控：网络类型、信号强度、连接稳定性
 *   - 解决：自适应码率、预加载、离线功能
 * 
 * 4. 流量使用监控
 *   - 问题：应用消耗过多用户流量
 *   - 影响：用户成本增加，卸载风险
 *   - 监控：请求大小、重复请求、缓存命中
 *   - 解决：数据压缩、智能缓存、流量节省模式
 */
@Singleton
class NetworkPerformanceMonitor @Inject constructor(
    private val performanceMonitor: PerformanceMonitor,
    private val connectivityManager: ConnectivityManager
) {
    
    /**
     * 创建网络监控拦截器
     * 
     * 集成方式：
     * 将此拦截器添加到OkHttpClient中即可自动监控所有网络请求
     * 
     * 示例：
     * OkHttpClient.Builder()
     *     .addInterceptor(networkMonitor.createNetworkInterceptor())
     *     .build()
     */
    fun createNetworkInterceptor(): Interceptor {
        return NetworkMonitoringInterceptor(performanceMonitor, connectivityManager)
    }
    
    /**
     * 创建高级网络监控拦截器
     * 
     * 增强特性：
     * - 详细的时间阶段分析
     * - 网络质量关联分析
     * - 自动重试监控
     * - 缓存策略评估
     */
    fun createAdvancedNetworkInterceptor(): Interceptor {
        return AdvancedNetworkMonitoringInterceptor(performanceMonitor, connectivityManager)
    }
}
/**
 * 基础网络监控拦截器
 */
class NetworkMonitoringInterceptor(
    private val performanceMonitor: PerformanceMonitor,
    private val connectivityManager: ConnectivityManager
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val callStartTime = System.nanoTime()
        
        // 记录请求开始
        recordRequestStart(request)
        
        try {
            val response = chain.proceed(request)
            val callEndTime = System.nanoTime()
            
            // 记录请求成功
            recordRequestSuccess(request, response, callStartTime, callEndTime)
            
            return response
            
        } catch (e: Exception) {
            val callEndTime = System.nanoTime()
            
            // 记录请求失败
            recordRequestFailure(request, e, callStartTime, callEndTime)
            throw e
        }
    }
    
    private fun recordRequestStart(request: Request) {
        val metadata = mapOf(
            "url" to getSanitizedUrl(request.url.toString()),
            "method" to request.method,
            "host" to request.url.host,
            "network_type" to getCurrentNetworkType().name
        )
        
        performanceMonitor.recordMetric(
            PerformanceMetric.network(
                name = "request_start",
                value = 1.0,
                metadata = metadata
            )
        )
    }
    
    private fun recordRequestSuccess(
        request: Request,
        response: Response,
        startTime: Long,
        endTime: Long
    ) {
        val durationMs = (endTime - startTime) / 1_000_000.0
        val contentLength = response.body?.contentLength() ?: -1L
        
        val metadata = buildMap {
            putAll(getBaseRequestMetadata(request))
            put("status_code", response.code.toString())
            put("content_length", contentLength.toString())
            put("cache_strategy", getCacheStrategy(response))
            put("protocol", response.protocol.toString())
            put("duration_ms", durationMs.toString())
        }
        
        // 根据耗时分类记录
        val metricName = when {
            durationMs < 1000 -> "request_fast"
            durationMs < 3000 -> "request_normal" 
            else -> "request_slow"
        }
        
        performanceMonitor.recordMetric(
            PerformanceMetric.network(
                name = metricName,
                value = durationMs,
                metadata = metadata
            )
        )
        
        // 慢请求额外告警
        if (durationMs > 3000) {
            performanceMonitor.recordMetric(
                PerformanceMetric.error(
                    type = MetricType.NETWORK,
                    name = "slow_request",
                    value = durationMs,
                    metadata = metadata
                )
            )
        }
    }
    
    private fun recordRequestFailure(
        request: Request,
        exception: Exception,
        startTime: Long,
        endTime: Long
    ) {
        val durationMs = (endTime - startTime) / 1_000_000.0
        
        val metadata = buildMap {
            putAll(getBaseRequestMetadata(request))
            put("exception_type", exception::class.simpleName ?: "Unknown")
            put("error_message", exception.message ?: "No message")
            put("duration_ms", durationMs.toString())
        }
        
        performanceMonitor.recordMetric(
            PerformanceMetric.error(
                type = MetricType.NETWORK,
                name = "request_failed",
                value = durationMs,
                metadata = metadata
            )
        )
    }
    
    private fun getBaseRequestMetadata(request: Request): Map<String, String> {
        return mapOf(
            "url" to getSanitizedUrl(request.url.toString()),
            "method" to request.method,
            "host" to request.url.host,
            "network_type" to getCurrentNetworkType().name
        )
    }
    
    /**
     * 移除URL中的敏感参数
     */
    private fun getSanitizedUrl(url: String): String {
        return url.replace(Regex("[?&](token|password|auth|key)=[^&]+"), "")
    }
    
    private fun getCacheStrategy(response: Response): String {
        return when {
            response.networkResponse == null -> "CACHE"
            response.cacheResponse == null -> "NETWORK" 
            else -> "CACHE_NETWORK"
        }
    }
    
    /**
     * 获取当前网络类型
     */
    private fun getCurrentNetworkType(): NetworkType {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            when {
                capabilities == null -> NetworkType.NONE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) -> NetworkType.LOWPAN
                else -> NetworkType.OTHER
            }
        } catch (e: Exception) {
            NetworkType.UNKNOWN
        }
    }
}
/**
 * 高级网络监控拦截器
 */
class AdvancedNetworkMonitoringInterceptor(
    private val performanceMonitor: PerformanceMonitor,
    private val connectivityManager: ConnectivityManager
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val callStartTime = System.nanoTime()
        
        // 添加请求标识
        val monitoredRequest = request.newBuilder()
            .addHeader("X-Request-ID", UUID.randomUUID().toString())
            .build()
        
        try {
            val response = chain.proceed(monitoredRequest)
            val callEndTime = System.nanoTime()
            
            recordDetailedMetrics(request, response, callStartTime, callEndTime)
            return response
            
        } catch (e: Exception) {
            val callEndTime = System.nanoTime()
            recordErrorMetrics(request, e, callStartTime, callEndTime)
            throw e
        }
    }
    
    private fun recordDetailedMetrics(
        request: Request,
        response: Response,
        startTime: Long,
        endTime: Long
    ) {
        val durationMs = (endTime - startTime) / 1_000_000.0
        
        val metadata = buildMap {
            putAll(getDetailedRequestMetadata(request, response))
            put("duration_ms", durationMs.toString())
            put("network_quality", assessNetworkQuality(durationMs))
        }
        
        performanceMonitor.recordMetric(
            PerformanceMetric.network(
                name = "request_detailed",
                value = durationMs,
                metadata = metadata
            )
        )
        
        // 性能瓶颈分析
        analyzePerformanceBottlenecks(request, durationMs, metadata)
    }
    
    private fun recordErrorMetrics(
        request: Request,
        exception: Exception,
        startTime: Long,
        endTime: Long
    ) {
        val durationMs = (endTime - startTime) / 1_000_000.0
        
        val errorType = when (exception) {
            is IOException -> "io_error"
            is SocketTimeoutException -> "timeout"
            is ConnectException -> "connect_failed"
            is SSLHandshakeException -> "ssl_error"
            is UnknownHostException -> "dns_error"
            else -> "unknown_error"
        }
        
        val metadata = buildMap {
            putAll(getBaseRequestMetadata(request))
            put("error_type", errorType)
            put("error_message", exception.message ?: "No message")
            put("duration_ms", durationMs.toString())
        }
        
        performanceMonitor.recordMetric(
            PerformanceMetric.error(
                type = MetricType.NETWORK,
                name = "request_${errorType}",
                value = durationMs,
                metadata = metadata
            )
        )
    }
    
    private fun getDetailedRequestMetadata(request: Request, response: Response): Map<String, String> {
        return buildMap {
            putAll(getBaseRequestMetadata(request))
            put("status_code", response.code.toString())
            put("content_length", (response.body?.contentLength() ?: -1).toString())
            put("content_type", response.header("Content-Type") ?: "unknown")
            put("cache_control", response.header("Cache-Control") ?: "none")
        }
    }
    
    private fun getBaseRequestMetadata(request: Request): Map<String, String> {
        return mapOf(
            "url" to getSanitizedUrl(request.url.toString()),
            "method" to request.method,
            "host" to request.url.host,
            "path" to request.url.encodedPath,
            "network_type" to getCurrentNetworkType().name
        )
    }
    
    private fun getSanitizedUrl(url: String): String {
        return url.replace(Regex("[?&](token|password|auth|key)=[^&]+"), "")
    }
    
    private fun getCurrentNetworkType(): NetworkType {
        return try {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            when {
                capabilities == null -> NetworkType.NONE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.OTHER
            }
        } catch (e: Exception) {
            NetworkType.UNKNOWN
        }
    }
    
    private fun assessNetworkQuality(durationMs: Double): String {
        return when {
            durationMs < 1000 -> "excellent"
            durationMs < 3000 -> "good"
            durationMs < 5000 -> "fair"
            else -> "poor"
        }
    }
    
    private fun analyzePerformanceBottlenecks(
        request: Request,
        durationMs: Double,
        metadata: Map<String, String>
    ) {
        when {
            durationMs > 5000 -> {
                performanceMonitor.recordMetric(
                    PerformanceMetric.error(
                        type = MetricType.NETWORK,
                        name = "very_slow_request",
                        value = durationMs,
                        metadata = metadata
                    )
                )
            }
            getCurrentNetworkType() == NetworkType.CELLULAR && durationMs > 3000 -> {
                performanceMonitor.recordMetric(
                    PerformanceMetric.error(
                        type = MetricType.NETWORK,
                        name = "slow_mobile_request",
                        value = durationMs,
                        metadata = metadata
                    )
                )
            }
        }
    }
}