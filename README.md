# Minimalist Music

一款极简主义设计的 Android 音乐播放器，提供流畅的在线音乐播放和本地音乐管理体验。

## 项目特性

- **极简设计**: Material Design 3 风格，简洁优雅的用户界面
- **在线音乐**: 支持在线搜索、播放热门歌曲和歌单
- **本地播放**: 完整的本地音乐库管理和播放功能
- **智能缓存**: 离线缓存机制，节省流量并支持离线播放
- **歌词显示**: 支持滚动歌词显示和拖拽定位
- **收藏管理**: 收藏喜欢的歌曲和歌单
- **播放历史**: 自动记录播放历史
- **性能监控**: 内置性能监控系统，优化应用性能

## 技术栈

### 核心框架
- **Kotlin**: 100% Kotlin 开发
- **Jetpack Compose**: 现代化声明式 UI 框架
- **Material Design 3**: 最新设计规范

### 架构
- **Clean Architecture**: 清晰的分层架构（Presentation/Domain/Data）
- **MVVM**: ViewModel + StateFlow 状态管理
- **Hilt**: 依赖注入
- **Kotlin Coroutines**: 异步编程和响应式流

### 数据层
- **Room**: 本地数据库持久化
- **DataStore**: 用户偏好设置存储
- **Retrofit + OkHttp**: 网络请求
- **Gson**: JSON 序列化

### 媒体播放
- **ExoPlayer (Media3)**: 强大的音频播放引擎
- **MediaSession**: 媒体控制和通知

### UI 组件
- **Coil**: 图片加载和缓存
- **Accompanist**: 系统 UI 控制和权限管理
- **Navigation Compose**: 页面导航
- **Reorderable**: 拖拽排序
- **LazyColumnScrollbar**: 快速滑动条

### 性能优化
- 自定义性能监控系统
- 智能音频缓存策略
- UI 性能优化和卡顿监控
- 启动性能优化

## 项目结构

```
app/src/main/kotlin/com.minimalistmusic/
├── presentation/           # UI 层（43.7%）
│   ├── ui/                # Compose UI 组件
│   │   ├── screens/       # 各个功能页面
│   │   ├── components/    # 可复用组件
│   │   └── theme/         # 主题配置
│   └── viewmodel/         # ViewModel 层
│
├── domain/                # 领域层（4.9%）
│   ├── model/             # 领域模型
│   ├── repository/        # Repository 接口
│   ├── usecase/           # 业务用例
│   └── cache/             # 缓存状态管理
│
├── data/                  # 数据层（28.7%）
│   ├── repository/        # Repository 实现
│   ├── remote/            # 网络 API
│   ├── local/             # 本地数据库
│   ├── cache/             # 缓存管理
│   └── mapper/            # 数据映射
│
├── service/               # 服务层（5.5%）
│   └── MusicService.kt    # 音乐播放服务
│
├── performance/           # 性能监控（10.2%）
│   ├── monitor/           # 各类性能监控器
│   ├── reporter/          # 性能数据上报
│   ├── metric/            # 性能指标
│   └── config/            # 监控配置
│
├── di/                    # 依赖注入（2.1%）
│   ├── AppModule.kt       # 应用级依赖
│   ├── RepositoryModule.kt
│   └── MonitoringModule.kt
│
└── util/                  # 工具类（1.9%）
    ├── LogConfig.kt       # 日志配置
    └── ...
```

## 构建和运行

### 环境要求

- **Android Studio**: Hedgehog | 2023.1.1 或更高版本
- **JDK**: JDK 17
- **Android SDK**:
  - minSdk: 26 (Android 8.0)
  - targetSdk: 35 (Android 15)
  - compileSdk: 35
- **Kotlin**: 1.9.10
- **Gradle**: 8.x

### 构建步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/yourusername/minimalistmusic.git
   cd minimalistmusic
   ```

2. **配置 API**

   打开 `app/build.gradle.kts`，配置你的 API 信息：
   ```kotlin
   buildConfigField("String", "NETEASE_API_BASE_URL", "\"YOUR_API_BASE_URL\"")
   buildConfigField("String", "API_KEY", "\"YOUR_API_KEY\"")
   ```

3. **同步依赖**
   ```bash
   ./gradlew build
   ```

4. **运行应用**
   - 使用 Android Studio: 点击 Run 按钮
   - 使用命令行:
     ```bash
     ./gradlew installDebug
     ```

### 构建变体

- **Debug**: 开发调试版本（包含日志和性能监控）
  ```bash
  ./gradlew assembleDebug
  ```

- **Release**: 生产发布版本（代码混淆和资源压缩）
  ```bash
  ./gradlew assembleRelease
  ```

## 主要功能

### 音乐播放
- 支持在线音乐播放
- 本地音乐扫描和播放
- 后台播放和锁屏控制
- 播放队列管理
- 循环模式切换（顺序/随机/单曲循环）

### 音乐发现
- 推荐歌单（每日推荐）
- 热门歌手和歌曲
- 在线搜索（歌曲/歌手/歌单）

### 缓存管理
- 智能音频缓存（LRU 策略）
- 缓存保护机制（收藏歌曲优先保护）
- 缓存大小限制和清理
- 离线播放支持

### 个人中心
- 播放历史记录
- 收藏歌曲管理
- 缓存歌曲查看
- 用户设置

## 性能特性

### 启动优化
- 延迟初始化非关键组件
- Splash Screen 优化
- 冷启动时间监控

### 运行时优化
- UI 性能监控（帧率/卡顿检测）
- 内存泄漏检测
- 网络流量监控
- 智能预加载机制

### 缓存策略
- ExoPlayer LRU 缓存
- 自定义缓存驱逐策略
- 受保护文件白名单
- 动态缓存空间管理

## 代码规范

### 日志打印
项目使用统一的 `LogConfig` 进行日志管理：

```kotlin
// 按架构层级选择 TAG
LogConfig.d(LogConfig.TAG_PLAYER_VIEWMODEL, "PlayerViewModel: message")
LogConfig.e(LogConfig.TAG_PLAYER_DATA_LOCAL, "Error message", exception)

// 日志格式：FileName -> MethodName -> Message
"PlayerViewModel smartPreloadSurroundingSongs: [预加载] 开始智能预加载"
```

可用的 TAG：
- `TAG_PLAYER_UI`: UI 层
- `TAG_PLAYER_VIEWMODEL`: ViewModel 层
- `TAG_PLAYER_DOMAIN`: Domain 层
- `TAG_PLAYER_DATA_REMOTE`: Data 层（网络）
- `TAG_PLAYER_DATA_LOCAL`: Data 层（本地）
- `TAG_PERFORMANCE_*`: 性能监控相关

### 架构原则
- 严格遵守 Clean Architecture 分层
- 单向数据流（UI -> ViewModel -> UseCase -> Repository）
- 依赖倒置（Domain 层不依赖 Data 层）
- 单一职责原则

## 开源许可证

本项目基于 Apache License 2.0 开源协议发布。

```
Copyright (C) 2025 JG.Y

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

完整许可证文本请查看 [LICENSE](LICENSE) 文件。

## 第三方依赖许可证

本项目使用了以下开源库，感谢这些优秀的开源项目：

- **Jetpack Compose**: Apache License 2.0
- **Kotlin**: Apache License 2.0
- **ExoPlayer (Media3)**: Apache License 2.0
- **Hilt**: Apache License 2.0
- **Room**: Apache License 2.0
- **Retrofit**: Apache License 2.0
- **OkHttp**: Apache License 2.0
- **Gson**: Apache License 2.0
- **Coil**: Apache License 2.0
- **Accompanist**: Apache License 2.0

## 贡献指南

欢迎贡献代码！请遵循以下流程：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 联系方式

- **项目地址**: [https://github.com/dqh7ld9pzg-cell/minimalistmusic](https://github.com/dqh7ld9pzg-cell/minimalistmusic)
- **问题反馈**: [Issues](https://github.com/dqh7ld9pzg-cell/minimalistmusic/issues)

---

**注意**: 本项目仅供学习和交流使用，请勿用于商业用途。
