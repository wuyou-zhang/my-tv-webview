# 我的电视 · 本地版

[![Build Release APK](https://github.com/wuyou-zhang/my-tv-webview/actions/workflows/release.yml/badge.svg)](https://github.com/wuyou-zhang/my-tv-webview/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/Android-5.0%2B-green.svg)]()

Android TV / 平板 / 手机通用的电视应用：一边是央视、卫视等电视台的**官方网页直播**，一边是**本地剧集媒体库**（三级选集、断点续播）。遥控器、触屏两套操作习惯都照顾到。

![直播](./docs/screenshots/tv.jpg)

## 截图

| 频道菜单 | 三级选集 |
| --- | --- |
| ![](./docs/screenshots/menu.jpg) | ![](./docs/screenshots/episodes.jpg) |

| 便捷设置 | 直播 |
| --- | --- |
| ![](./docs/screenshots/settings.jpg) | ![](./docs/screenshots/tv.jpg) |

## 直播

- 央视、各大卫视、地方台、少儿卡通等分组，频道列表自动更新，支持自定义源（见 `source` 目录）
- 每个频道支持多条播放线路（央视频 / 央视网），加载失败自动切换下一条线路，并记住可用线路，下次直接播放
- 播放自愈：页面加载超时自动重试；播放中画面停滞会自动重载页面，仍无效则切换线路
- 断网保护：网络断开时提示「网络已断开，恢复后自动重试」，恢复后无需手动操作
- 收藏频道：点频道行右侧红心加入「我的收藏」
- 换台：遥控器上 / 下键、数字键；可在设置中开启换台反转

## 本地媒体库

自动扫描 `/storage/emulated/0/我的电视本地资源库/`（也支持 TF 卡、U 盘），按目录生成分组与频道：

```
我的电视本地资源库/
└── 经典影视/                          # 分组
    └── 寄生兽/                        # 频道（一部剧）
        ├── [Skytree][Kiseijuu_Sei_no_Kakuritsu][01][GB_JP][720P].mp4
        ├── [Skytree][Kiseijuu_Sei_no_Kakuritsu][02][GB_JP][720P].mp4
        └── ...
```

- 支持格式：mp4、mkv、avi、flv、ts、mov、rmvb、wmv、m4v、webm、iso
- 三级菜单：分组 → 频道（剧集）→ 选集列表，选集列表按「序号 文件名」显示
- 断点续播：每个频道保存一条播放进度，下次进入自动跳到上次位置；切换剧集即切换进度记录
- 最后一集播完自动回到第一集循环

## 操作说明

### 手机 / 平板手势

| 操作 | 功能 |
| --- | --- |
| 点按屏幕左侧 70% | 打开菜单 |
| 点按屏幕右侧 | 打开便捷设置 |
| 双击屏幕 | 开关菜单 |
| 左 1/3 区域上下滑 | 调节亮度 |
| 右 1/3 区域上下滑 | 调节音量 |
| 横向滑动（本地视频） | 快进 / 快退 |

### 遥控器

| 按键 | 功能 |
| --- | --- |
| 上 / 下 | 换台 |
| 中键 / 确认 | 打开频道菜单 |
| 左键 | 节目单（菜单展开时收起选集列） |
| 右键 | 便捷设置（菜单中展开选集列） |
| 长按左 / 右 | 本地视频快退 / 快进 |
| 菜单键 / 设置键 | 便捷设置 |
| 数字键 | 数字换台 |

### 便捷设置

开机自启、右上角显示时间、换台反转、退出应用；直播频道可在此切换播放线路。

## 安装

从 [Releases](https://github.com/wuyou-zhang/my-tv-webview/releases) 下载最新 APK 安装即可。构建使用固定签名，后续版本可直接覆盖安装，无需卸载。

## 常见问题

- **直播黑屏 / 加载失败**：应用会自动重试并切换线路，全部线路失败时换台再试，或检查网络
- **本地视频没有出现**：确认目录结构为「资源库/分组/剧集/视频文件」三级，且格式在支持列表内
- **画面卡住不动**：稍等片刻，应用检测到画面停滞会自动恢复；断网时会提示，恢复后自动重试

## 构建

要求 JDK 17、Android SDK 36：

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/我的电视 本地版.apk`

## 发布

推送 `v*` 标签会由 GitHub Actions 自动构建 APK 并创建 Release：

```bash
git tag v1.0.1
git push origin v1.0.1
```

构建使用仓库内 `keystore/debug.keystore` 固定签名，后续版本可直接覆盖安装。

## 赞赏

如果这个项目对你有帮助，欢迎扫码赞赏，支持作者继续维护：

![赞赏二维码](./docs/donate.png)

## 参考项目

- [my-tv-webview](https://github.com/zwc456baby/my-tv-webview)：沿用开发思路
- [WebViewTvLive](https://github.com/hxh19950701/WebViewTvLive)：同类网页直播播放器，功能参考
- [my-tv-0](https://github.com/lizongying/my-tv-0)：网页直播播放框架的早期项目

## 声明

- 本应用仅嵌套展示电视台公开的直播页面，所有内容来自原网站，版权归原网站所有。
- 本应用不提供任何视频资源，不储存、不转发，仅用于学习研究用途。
- [MIT License](./LICENSE)
