# Minimalist Music

[![中文](https://img.shields.io/badge/语言-中文-red)](README_CN.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.09-blue)](https://developer.android.com/compose)

A minimalist Android music player built with Kotlin and Jetpack Compose, designed as a **personal technical research project** exploring modern Android architecture, media playback engineering, and performance optimization.

> **Note**: This project uses publicly accessible music APIs for research purposes. It is **non-commercial** and intended for individual study and technical exploration.

## Screenshots

| Home | Discover | Player | Search |
|------|----------|--------|--------|
| ![Home](screenshots/home.jpg) | ![Discover](screenshots/discover.jpg) | ![Player](screenshots/player.jpg) | ![Search](screenshots/search.jpg) |

| Profile | Favorites | Cached |
|---------|-----------|--------|
| ![Profile](screenshots/profile.jpg) | ![Favorites](screenshots/favorites.jpg) | ![Cached](screenshots/cached.jpg) |

## Download

[![Download APK](https://img.shields.io/badge/Download-APK-blue)](https://github.com/dqh7ld9pzg-cell/minimalistmusic/releases/latest/download/minimalist-music-v1.2.19.apk)

> APK is distributed via GitHub Releases. No API keys or credentials are included — configure your own before building from source.

## Demo Video

[Watch Demo](https://github.com/dqh7ld9pzg-cell/minimalistmusic/releases/latest/download/recordApp.mp4)

## Overview

Minimalist Music is a full-featured music player that combines online streaming, local playback, and intelligent caching under a Material Design 3 interface. It was built to explore:

- **Clean Architecture** at scale in a real-world Compose application
- **Media3 ExoPlayer** integration with custom caching and lyrics synchronization
- **Reactive state management** with Kotlin StateFlow across a multi-screen navigation graph
- **Performance engineering** — startup optimization, frame monitoring, memory leak detection

## Features

### Playback
- Online streaming via public music APIs
- Local media scanning and playback
- Background playback with MediaSession controls (notification + lock screen)
- Play queue management with drag-to-reorder
- Three play modes: Sequential / Shuffle / Repeat One
- Synchronized lyric display with scroll-to-position

### Discovery
- Curated playlists and hot tracks
- Artist search and detail pages
- Full-text search across songs, artists, and playlists

### Personal
- User account with cloud sync (favorites, play history)
- Favorite songs and playlists management
- Play history with paginated browsing
- Cached music management with smart eviction

### Performance
- Cold-start optimization via Splash Screen API + Baseline Profile + lazy initialization
- UI frame monitoring with `ChoreographerMonitor` — dropped-frame stack capture
- Memory profiling with periodic GC triggers and leak预警
- Network-aware adaptive caching (Wi-Fi vs. mobile data strategies)

## Architecture

The project follows **Clean Architecture** with strict layer separation. The Domain layer has zero Android dependencies, making it fully unit-testable.

```
┌──────────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ HomeScreen│  │Discover  │  │Player    │  │Search    │  ...   │
│  │ (Compose) │  │Screen    │  │Screen    │  │Screen    │        │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘      │
│        │              │              │              │             │
│        └──────────────┴──────────────┴──────────────┘             │
│                          │  StateFlow                            │
│  ┌───────────────────────┴────────────────────────────────────┐  │
│  │                    ViewModel Layer                         │  │
│  │  HomeVM  │  PlayerVM  │  DiscoverVM  │  ProfileVM  │ ...  │  │
│  │  AccountSyncVM  │  CachedMusicVM  │  LoginVM              │  │
│  └───────────────────────┬────────────────────────────────────┘  │
├──────────────────────────┼───────────────────────────────────────┤
│                     DOMAIN LAYER                                 │
│  ┌───────────────────────┴────────────────────────────────────┐  │
│  │  UseCases                    Repository Interfaces         │  │
│  │  PlayNextSongUseCase         MusicOnlineRepository         │  │
│  │  ToggleFavoriteWithSync      MusicLocalRepository          │  │
│  │  LoadLyricsUseCase           SearchRepository              │  │
│  │  PreparePlaylistUseCase      PlaybackController            │  │
│  │  HandleUrlExpiredUseCase     UserRepository                │  │
│  │  SyncFavoritesToCloud        SearchHistoryRepository       │  │
│  │  SyncHistoryToCloud          CacheStateManager             │  │
│  └───────────────────────┬────────────────────────────────────┘  │
├──────────────────────────┼───────────────────────────────────────┤
│                      DATA LAYER                                  │
│  ┌───────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Remote        │  │ Local        │  │ Cache                │  │
│  │ Retrofit +    │  │ Room +       │  │ AudioCacheManager    │  │
│  │ OkHttp + Gson │  │ DataStore    │  │ ProtectedLruEvictor  │  │
│  │ DTO mapping   │  │ DAO + Entity │  │ CacheConfig          │  │
│  └───────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
└──────────┼─────────────────┼─────────────────────┼──────────────┘
           │                 │                     │
           ▼                 ▼                     ▼
     Public Music API    SQLite / File       ExoPlayer LRU Cache
```

### Layer Responsibilities

| Layer | Responsibility | Dependencies |
|-------|---------------|-------------|
| **Presentation** | Compose UI, ViewModel state management, screen navigation | Domain (UseCase, Model) |
| **Domain** | Business logic, repository contracts, cache state management | None (pure Kotlin) |
| **Data** | API calls, database operations, file cache, DTO-to-domain mapping | Domain (implements Repository interfaces) |

### State Management

- **StateFlow** as the single source of truth in every ViewModel
- **Activity-scoped ViewModels** (via Hilt) shared across screens to eliminate redundant data loading and UI flicker when navigating
- Unidirectional data flow: `UI Event → ViewModel → UseCase → Repository → StateFlow → UI`

## Tech Stack

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Kotlin | 2.0.21 | 100% Kotlin codebase |
| **UI Framework** | Jetpack Compose (BOM) | 2024.09.00 | Declarative UI with Material 3 |
| **Design System** | Material Design 3 | 1.4.0 | Theming, components, dynamic color |
| **DI** | Hilt (Dagger) | 2.48.1 | Compile-time dependency injection |
| **Navigation** | Navigation Compose | 2.7.5 | Type-safe declarative routing |
| **HTTP Client** | Retrofit + OkHttp | 2.9.0 / 4.12.0 | REST API consumption with interceptors |
| **JSON** | Gson | 2.10.1 | Serialization / deserialization |
| **Database** | Room | 2.6.0 | Local persistence with compile-time SQL verification |
| **KV Storage** | DataStore Preferences | 1.0.0 | Reactive preference storage |
| **Media Playback** | Media3 ExoPlayer | 1.2.0 | Audio decoding, streaming, caching |
| **Media Session** | Media3 Session | 1.2.0 | System media controls and notifications |
| **Image Loading** | Coil Compose | 2.5.0 | Async image loading with memory/disk cache |
| **System UI** | Accompanist | 0.32.0 | Status bar insets, runtime permissions |
| **Drag & Drop** | Reorderable | 2.4.0 | Playlist drag-to-reorder |
| **Scrollbar** | LazyColumnScrollbar | 2.2.0 | Fast-scroll indicators |
| **Build** | AGP / Gradle | 8.13.0 / 8.13 | Build system |
| **Perf** | Baseline Profile | 1.2.4 | AOT compilation for startup |

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Compose over XML Views** | Declarative paradigm aligns with StateFlow reactivity; eliminates Fragment/Adapter boilerplate; `Crossfade` and `animateDpAsState` enable fluid transitions with minimal code |
| **Clean Architecture (3-layer)** | Domain layer has zero Android dependencies — fully unit-testable; Data layer is replaceable (e.g., swap API providers without touching UI or business logic) |
| **Hilt over Koin** | Compile-time DI graph verification catches misconfiguration at build time; better performance via generated code rather than runtime reflection |
| **Activity-scoped shared ViewModels** | Eliminates redundant data loading when navigating between screens; prevents UI flicker from empty→data state transitions |
| **ExoPlayer (Media3)** | Modular pipeline architecture supports custom `CacheEvictor`, custom `DataSource.Factory` for URL expiry handling, and precise seek control for lyrics synchronization |
| **Song-count-based caching** | Device storage tier detection (`StatFs`) determines defaults — high-end devices (≥10GB free) cache up to 100 songs, lower-end devices cache 50; avoids fixed-size limits that don't adapt to device capability |
| **Protected LRU eviction** | Favorited songs are whitelisted from cache eviction; `ProtectedLruCacheEvictor` extends ExoPlayer's `CacheEvictor` to preserve user preferences |
| **Dynamic cache ceiling** | `CacheConfig.getDynamicMaxBytes()` adapts the cache budget based on actual song sizes + 100MB buffer, preventing unbounded growth from fragmentation |
| **Custom performance monitors** | `ChoreographerMonitor` hooks into the frame callback to detect jank; `MemoryPerformanceMonitor` triggers GC on background and tracks heap trends; all data flows through `PerformanceReporter` for structured logging |
| **Netease API integration** | Reverse-engineered API protocol with custom header signing (`NeteaseHeaderInterceptor`) and error-handling interceptor for graceful degradation |

## Project Structure

```
app/src/main/kotlin/com/minimalistmusic/
├── presentation/               # Presentation layer
│   ├── ui/
│   │   ├── screens/            # Screen-level composables (Home, Discover, Player, etc.)
│   │   ├── components/         # Reusable UI components (MiniPlayer, FavoriteButton, etc.)
│   │   └── theme/              # Material 3 theme, color, typography
│   ├── viewmodel/              # ViewModels with StateFlow state
│   ├── error/                  # Global error channel and handler
│   └── Navigation.kt           # NavHost, bottom bar, MiniPlayer orchestration
│
├── domain/                     # Domain layer (zero Android dependencies)
│   ├── model/                  # Domain models (PlayData, PagedData, CacheProgress, PlayMode)
│   ├── repository/             # Repository interfaces (contracts)
│   ├── usecase/                 # Business logic use cases
│   │   ├── player/             # PlayNextSong, LoadLyrics, PreparePlaylist, HandleUrlExpired
│   │   ├── favorite/           # ToggleFavoriteWithSync, SyncFavoritesToCloud
│   │   └── history/            # GetPagedPlayHistory, SyncHistoryToCloud
│   └── cache/                  # CacheStateManager
│
├── data/                       # Data layer
│   ├── remote/                 # Retrofit API services, DTOs, interceptors
│   ├── local/                  # Room database, DAOs, entities, DataStore preferences
│   ├── cache/                  # AudioCacheManager, CacheConfig, ProtectedLruCacheEvictor
│   ├── repository/             # Repository implementations
│   └── mapper/                 # Entity/DTO → Domain model mappers
│
├── service/                    # Foreground service
│   └── MusicService.kt         # MediaSession + ExoPlayer lifecycle
│
├── performance/                # Custom performance monitoring
│   ├── monitor/                # Choreographer, Memory, UI, Startup monitors
│   ├── reporter/               # PerformanceReporter, PerformanceStorage
│   ├── metric/                 # PerformanceMetric data class
│   └── config/                 # PerformanceConfig thresholds
│
├── di/                         # Hilt DI modules
│   ├── AppModule.kt
│   ├── RepositoryModule.kt
│   └── MonitoringModule.kt
│
└── util/                       # Shared utilities (LogConfig, etc.)
```

## Getting Started

### Prerequisites

| Requirement | Version |
|------------|---------|
| Android Studio | Ladybug (2024.2.1) or later |
| JDK | 17 |
| Kotlin | 2.0.21 |
| Gradle | 8.13 |
| Android SDK (compileSdk) | 35 |
| Android SDK (minSdk) | 26 (Android 8.0) |
| Android SDK (targetSdk) | 35 (Android 15) |

### Build

```bash
# 1. Clone the repository
git clone https://github.com/dqh7ld9pzg-cell/minimalistmusic.git
cd minimalistmusic

# 2. Sync dependencies
./gradlew build

# 3. Install debug build to connected device
./gradlew installDebug
```

### Build Variants

| Variant | Command | Description |
|---------|---------|-------------|
| **Debug** | `./gradlew assembleDebug` | Development build with logging and monitoring enabled |
| **Release** | `./gradlew assembleRelease` | Minified with ProGuard, optimized for distribution |

### API Configuration

This app relies on publicly accessible music APIs. To configure your own API credentials:

1. Open `app/build.gradle.kts`
2. Locate the `buildConfigField` entries under `defaultConfig`
3. Replace the placeholder values with your own:
   ```groovy
   buildConfigField("String", "NETEASE_API_BASE_URL", "\"your-api-url\"")
   buildConfigField("String", "API_KEY", "\"your-api-key\"")
   ```

> **No API keys are committed to this repository.**

## Performance

### Startup
- **Splash Screen API** with `core-splashscreen:1.0.1` for instant branding
- **Baseline Profile** (1.2.4) for AOT compilation of critical startup paths
- Lazy initialization of non-critical components (performance monitors, sync services)
- Target: cold start under 800ms on reference devices

### Runtime
- **ChoreographerMonitor**: Hooks into `Choreographer.FrameCallback` to detect dropped frames; automatically captures stack traces when jank exceeds threshold
- **MemoryPerformanceMonitor**: Triggers periodic GC on app backgrounding and tracks heap size trends; warns when allocations exceed configured thresholds
- **UIPerformanceMonitor**: Counts Compose recomposition passes, flags excessive recompositions for investigation

### Cache Strategy

The caching system uses a **device-tiered, song-count-based** approach rather than a fixed size limit:

| Device Tier | Free Storage | Default Cache | Maximum Cache |
|------------|-------------|--------------|--------------|
| High-end | ≥ 10 GB | 100 songs | 200 songs |
| Mid/Low-end | < 10 GB | 50 songs | 100 songs |

- Detection via `StatFs` at app startup
- Each song estimated at ~8 MB with 1.5× buffer (`songCount × 8 × 1.5` MB)
- `ProtectedLruCacheEvictor` preserves favorited songs from eviction
- `CacheConfig.getDynamicMaxBytes()` adjusts budget based on actual cached sizes, preventing unbounded growth from ExoPlayer cache fragmentation
- Network-aware: aggressive preloading on Wi-Fi, conservative on mobile data

## Contributing

Contributions are welcome. Please follow the standard workflow:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Disclaimer

This project is for **personal learning and research purposes only**.

- Music data is sourced from publicly accessible APIs and is **not** obtained through official partnerships or commercial agreements
- This repository does **not** contain any official API keys, tokens, or credentials — users must configure their own
- This project is **non-commercial** — do not use it for any commercial purpose or distribution
- All music content rights belong to their respective copyright holders

If you are a copyright holder and have concerns, please [open an issue](https://github.com/dqh7ld9pzg-cell/minimalistmusic/issues).

## License

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

See [LICENSE](LICENSE) for the full text.

---

**Built with Kotlin, Compose, and attention to detail.**
