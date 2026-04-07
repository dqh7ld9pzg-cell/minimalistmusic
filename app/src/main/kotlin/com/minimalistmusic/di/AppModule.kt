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

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.minimalistmusic.BuildConfig
import com.minimalistmusic.data.local.MusicDatabase
import com.minimalistmusic.data.local.dao.CacheDao
import com.minimalistmusic.data.local.dao.CachedSongDao
import com.minimalistmusic.data.local.dao.FavoritePlaylistDao
import com.minimalistmusic.data.local.dao.PlayHistoryDao
import com.minimalistmusic.data.local.dao.PlaylistDao
import com.minimalistmusic.data.local.dao.SearchHistoryDao
import com.minimalistmusic.data.local.dao.SongDao
import com.minimalistmusic.data.local.dao.UserDao
import com.minimalistmusic.data.remote.FreeMusicApiService
import com.minimalistmusic.data.remote.NeteaseSearchApiService
import com.minimalistmusic.data.remote.UserApiService
import com.minimalistmusic.data.remote.interceptor.ErrorHandlingInterceptor
import com.minimalistmusic.data.remote.interceptor.NeteaseHeaderInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

// 为Retrofit不同实例定义限定符
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FreeMusicRetrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NeteaseSearchRetrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserApiRetrofit

// 为OKHttpClient不同实例定义限定符
@Qualifier
@Retention(value = AnnotationRetention.BINARY)
annotation class NeteaseOkHttpClient
@Qualifier
@Retention(value = AnnotationRetention.BINARY)
annotation class FreeMusicOkHttpClient
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()
    /**
     * 提供 OkHttpClient
     *
     * 性能优化 (2025-11-12):
     * - 添加 HTTP 缓存: 10MB 磁盘缓存，减少重复网络请求
     * - 缓存策略: GET请求自动缓存，有效期由服务器Cache-Control决定
     * - 超时配置: 连接30秒，读取30秒
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val errorHandlingInterceptor = ErrorHandlingInterceptor()
        // HTTP缓存配置: 10MB磁盘缓存
        val cacheSize = 10 * 1024 * 1024L // 10 MB
        val cache = Cache(
            directory = context.cacheDir.resolve("http_cache"),
            maxSize = cacheSize
        )
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(cache)  // 启用HTTP缓存
            .addInterceptor(errorHandlingInterceptor) // 统一错误处理（最先执行）
            .addInterceptor(loggingInterceptor) // 日志记录（后执行，可以看到错误）
            .build()
    }
    /**
     * 为FreeMusicAPI提供专门的OkHttpClient
     *
     * 性能优化 (2025-11-15):
     * - 禁用自动重定向: followRedirects(false)
     * - 原因：FreeMusicAPI的URL接口会302重定向到MP3文件（9MB+）
     * - 默认情况下，OkHttp会跟随重定向并下载整个MP3，导致6-7秒延迟
     * - 禁用后，我们手动从Location头获取URL，耗时<500ms
     */
    @Provides
    @Singleton
    @FreeMusicOkHttpClient
    fun provideFreeMusicOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }
        val errorHandlingInterceptor = ErrorHandlingInterceptor()
        // HTTP缓存配置: 10MB磁盘缓存
        val cacheSize = 10 * 1024 * 1024L // 10 MB
        val cache = Cache(
            directory = context.cacheDir.resolve("http_cache"),
            maxSize = cacheSize
        )
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(cache)
            .followRedirects(false)  // 禁用自动重定向
            .followSslRedirects(false)
            .addInterceptor(errorHandlingInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * 网易云音乐搜索API配置 (2025-11-17更新)
     *
     * API地址说明：
     * - 使用网易云音乐官方API：https://music.163.com/api/
     *
     * 优化内容：
     * - 添加 NeteaseHeaderInterceptor 自动添加必要请求头
     * - 使用专用OkHttpClient，避免干扰其他API
     *
     * 备注：
     * - 官方API相对稳定，但可能有访问限制
     */
    @Provides
    @Singleton
    @NeteaseOkHttpClient
    fun provideNeteaseOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val neteaseHeaderInterceptor = NeteaseHeaderInterceptor()
        val errorHandlingInterceptor = ErrorHandlingInterceptor()
        val cacheSize = 10 * 1024 * 1024L
        val cache = Cache(
            directory = context.cacheDir.resolve("http_cache"),
            maxSize = cacheSize
        )
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(cache)
            .addInterceptor(neteaseHeaderInterceptor)
            .addInterceptor(errorHandlingInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @FreeMusicRetrofit
    fun provideFreeMusicRetrofit(@FreeMusicOkHttpClient okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.injahow.cn/")
        .client(okHttpClient)  // 使用禁用重定向的专用OkHttpClient
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    @NeteaseSearchRetrofit
    fun provideNeteaseSearchRetrofit(@NeteaseOkHttpClient okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl("https://music.163.com/api/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    @UserApiRetrofit
    fun provideUserApiRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl("https://minimalist-music-backend.vercel.app/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    // ============ API Service Providers ============
    @Provides
    @Singleton
    fun provideFreeMusicApiService(@FreeMusicRetrofit retrofit: Retrofit): FreeMusicApiService = retrofit.create(FreeMusicApiService::class.java)
    @Provides
    @Singleton
    fun provideNeteaseSearchApiService(@NeteaseSearchRetrofit retrofit: Retrofit): NeteaseSearchApiService = retrofit.create(NeteaseSearchApiService::class.java)
    @Provides
    @Singleton
    fun provideUserApiService(@UserApiRetrofit retrofit: Retrofit): UserApiService = retrofit.create(UserApiService::class.java)

    // SharedPreferences Provider
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("minimalist_music_prefs", Context.MODE_PRIVATE)

    // ConnectivityManager Provider
    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Database and DAO Providers
    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase = Room.databaseBuilder(
        context,
        MusicDatabase::class.java,
        "minimalist_music_database"
    ).addMigrations(
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13
    ).build()
    @Provides
    @Singleton
    fun provideSongDao(database: MusicDatabase): SongDao = database.songDao()
    @Provides
    @Singleton
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao = database.playlistDao()
    @Provides
    @Singleton
    fun provideUserDao(database: MusicDatabase): UserDao = database.userDao()
    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: MusicDatabase): SearchHistoryDao = database.searchHistoryDao()
    @Provides
    @Singleton
    fun providePlayHistoryDao(database: MusicDatabase): PlayHistoryDao = database.playHistoryDao()
    @Provides
    @Singleton
    fun provideCacheDao(database: MusicDatabase): CacheDao = database.cacheDao()
    @Provides
    @Singleton
    fun provideFavoritePlaylistDao(database: MusicDatabase): FavoritePlaylistDao = database.favoritePlaylistDao()
    @Provides
    @Singleton
    fun provideCachedSongDao(database: MusicDatabase): CachedSongDao = database.cachedSongDao()
    // SharedPreferences Provider

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE songs ADD COLUMN source TEXT DEFAULT NULL")
        }
    }
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建缓存表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS cache (
                    key TEXT PRIMARY KEY NOT NULL,
                    data TEXT NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    expiresAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    /**
     * 数据库迁移 4 -> 5: 性能优化 - 添加索引
     *
     * 优化内容 (2025-11-12):
     * 1. songs表: 添加索引提升查询性能
     *    - isLocal: 加速本地歌曲筛选
     *    - isFavorite: 加速收藏歌曲查询
     *    - addedAt: 加速时间排序
     * 2. search_history表: 添加searchedAt索引，加速历史记录排序
     * 3. play_history表: 优化为复合索引(songId, playedAt)，加速GROUP BY + ORDER BY查询
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // songs表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_isLocal ON songs(isLocal)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_isFavorite ON songs(isFavorite)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_addedAt ON songs(addedAt)")
            // search_history表索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_searchedAt ON search_history(searchedAt)")
            // play_history表: 删除旧索引，创建新的复合索引
            database.execSQL("DROP INDEX IF EXISTS index_play_history_songId")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_songId_playedAt ON play_history(songId, playedAt)")
        }
    }
    /**
     * 数据库迁移 5 -> 6: 添加歌单收藏功能 (2025-11-16)
     *
     * 新增内容:
     * 1. 创建 favorite_playlists 表
     * 2. 支持收藏在线推荐歌单
     * 3. 添加favoritedAt索引，加速按收藏时间排序
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建收藏歌单表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS favorite_playlists (
                    playlistId INTEGER PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    cover TEXT,
                    description TEXT,
                    songCount INTEGER NOT NULL DEFAULT 0,
                    favoritedAt INTEGER NOT NULL
                )
            """.trimIndent())
            // 添加索引：按收藏时间排序
            database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_playlists_favoritedAt ON favorite_playlists(favoritedAt)")
        }
    }
    /**
     * 数据库迁移 6 -> 7: 添加歌曲缓存表 (2025-11-16)
     *
     * 新增内容:
     * 1. 创建 cached_songs 表
     * 2. 支持从数据库中直接读取或删除缓存记录，避免需要多次同时查询播放历史和收藏列表
     * 3. 添加cachedAt索引，加速按缓存时间排序
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建缓存表
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_songs (
                    songId INTEGER PRIMARY KEY NOT NULL,
                    url TEXT NOT NULL,
                    cacheSize INTEGER NOT NULL DEFAULT 0,
                    cachedAt INTEGER NOT NULL,
                    FOREIGN KEY (songId) REFERENCES songs (id) ON DELETE CASCADE
                )
            """.trimIndent())
            // 添加索引：按缓存时间排序
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_songs_cachedAt ON cached_songs(cachedAt)")
        }
    }
    /**
     * 数据库迁移 7 -> 8: 为收藏歌单表添加source字段 (2025-11-18)
     *
     * 新增内容:
     * 1. 给 favorite_playlists 表添加 source 字段
     * 2. 用于区分歌单类型（playlist: 普通歌单，artist: 歌手歌单）
     * 3. 默认值为 "playlist"，保证已有数据兼容性
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 添加source字段到favorite_playlists表
            database.execSQL("ALTER TABLE favorite_playlists ADD COLUMN source TEXT NOT NULL DEFAULT 'playlist'")
        }
    }
    /**
     * 数据库迁移 8 -> 9: URL缓存优化 - 扩展cached_songs表 (2025-11-18)
     *
     * 新增内容:
     * 1. 给 cached_songs 表添加 isFullyCached 字段 - 区分URL缓存和音频文件缓存
     * 2. 给 cached_songs 表添加 urlExpireTime 字段 - 支持URL有效期管理
     * 3. 添加 urlExpireTime 索引 - 加速过期URL清理查询
     * 4. 更新现有数据：将已有记录标记为完整缓存（isFullyCached=1），URL永久有效
     *
     * 架构优化：
     * - 所有获取到的播放URL都缓存到数据库
     * - isFullyCached=false: 只缓存了URL，未缓存音频文件
     * - isFullyCached=true: 已完整缓存音频文件
     * - 减少重复的API请求，提升用户体验
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 添加 isFullyCached 字段（默认值为0，即false）
            database.execSQL("ALTER TABLE cached_songs ADD COLUMN isFullyCached INTEGER NOT NULL DEFAULT 0")
            // 2. 添加 urlExpireTime 字段（默认值为Long.MAX_VALUE，即永久有效）
            database.execSQL("ALTER TABLE cached_songs ADD COLUMN urlExpireTime INTEGER NOT NULL DEFAULT ${Long.MAX_VALUE}")
            // 3. 更新现有记录：已有的记录都是完整缓存的（音频文件已下载）
            database.execSQL("UPDATE cached_songs SET isFullyCached = 1 WHERE cacheSize > 0")
            // 4. 添加 urlExpireTime 索引，加速过期URL清理查询
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_songs_urlExpireTime ON cached_songs(urlExpireTime)")
        }
    }
    /**
     * 数据库迁移 9 -> 10: 架构优化 - 添加独立的收藏时间字段 (2025-11-19)
     *
     * 新增内容:
     * 1. 给 songs 表添加 favoritedAt 字段 - 专门记录收藏时间
     * 2. 添加 favoritedAt 索引 - 加速收藏时间排序查询
     *
     * 架构优化：
     * - 职责分离：addedAt 记录入库时间（本地扫描/首次入库），favoritedAt 记录收藏时间
     * - 解决时间戳混乱问题：不再滥用 addedAt 字段
     * - 收藏列表按 favoritedAt 排序，语义更清晰
     *
     * 数据迁移策略：
     * - 已收藏的歌曲：将 favoritedAt 设置为 addedAt（历史数据兼容）
     * - 未收藏的歌曲：favoritedAt 保持为 NULL
     */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 添加 favoritedAt 字段（可为空，默认为NULL）
            database.execSQL("ALTER TABLE songs ADD COLUMN favoritedAt INTEGER DEFAULT NULL")
            // 2. 数据迁移：将已收藏歌曲的 favoritedAt 设置为 addedAt（保持历史数据一致性）
            database.execSQL("UPDATE songs SET favoritedAt = addedAt WHERE isFavorite = 1")
            // 3. 添加 favoritedAt 索引，加速收藏时间排序查询
            database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_favoritedAt ON songs(favoritedAt)")
        }
    }
    /**
     * 数据库迁移 10 -> 11: 播放历史优化 - 区分播放记录和聆听足迹 (2025-11-20)
     *
     * 新增内容:
     * 1. 给 play_history 表添加 isListeningRecord 字段 - 标识是否计入聆听足迹
     * 2. 添加 isListeningRecord 索引 - 加速聆听足迹查询
     *
     * 架构优化：
     * - 播放记录：所有播放过的歌曲（立即入库，用于外键约束和缓存管理）
     * - 聆听足迹：播放>=10秒的歌曲（显示在"聆听足迹"界面）
     * - 限制200首：仅限制聆听足迹数量，播放记录由总表限制控制
     * - 解决外键约束失败：歌曲开始播放就立即入库，避免缓存URL时外键约束失败
     *
     * 数据迁移策略：
     * - 已有播放记录：全部标记为聆听足迹（isListeningRecord=1），保持兼容性
     * - 新播放记录：初始为false，播放>=10秒后更新为true
     */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 添加 isListeningRecord 字段（默认值为0，即false）
            database.execSQL("ALTER TABLE play_history ADD COLUMN isListeningRecord INTEGER NOT NULL DEFAULT 0")
            // 2. 数据迁移：将已有播放记录全部标记为聆听足迹（保持历史数据兼容性）
            database.execSQL("UPDATE play_history SET isListeningRecord = 1")
            // 3. 添加 isListeningRecord 索引，加速聆听足迹查询
            database.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_isListeningRecord ON play_history(isListeningRecord)")
        }
    }
    /**
     * 数据库迁移 11 -> 12: 缓存管理优化 - 支持按歌曲数量管理缓存 (2025-11-21)
     *
     * 新增内容:
     * 1. 给 cached_songs 表添加 lastPlayedAt 字段 - 记录最近播放时间
     * 2. 添加 lastPlayedAt 索引 - 加速按播放时间排序和LRU清理
     *
     * 架构优化：
     * - 支持LRU缓存策略：按最近播放时间删除旧缓存
     * - 用户可设置最大缓存歌曲数（5-100首），超过上限自动清理
     * - 缓存列表按最近播放时间排序（最近听的在前面）
     *
     * 数据迁移策略：
     * - 已有缓存记录：将 lastPlayedAt 设置为 cachedAt（保持兼容性）
     * - 新缓存记录：初始 lastPlayedAt = cachedAt，播放时更新
     */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 添加 lastPlayedAt 字段（默认值为0，后面会批量更新）
            database.execSQL("ALTER TABLE cached_songs ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
            // 2. 数据迁移：将已有缓存记录的 lastPlayedAt 设置为 cachedAt（保持历史数据一致性）
            database.execSQL("UPDATE cached_songs SET lastPlayedAt = cachedAt")
            // 3. 添加 lastPlayedAt 索引，加速按播放时间排序和LRU清理查询
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_songs_lastPlayedAt ON cached_songs(lastPlayedAt)")
        }
    }
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 添加 isProtected 字段（默认值为0/false，表示未加入白名单）
            database.execSQL("ALTER TABLE cached_songs ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0")
            // 2. 添加 isProtected 索引，加速白名单查询和过滤
            database.execSQL("CREATE INDEX IF NOT EXISTS index_cached_songs_isProtected ON cached_songs(isProtected)")
        }
    }
}
