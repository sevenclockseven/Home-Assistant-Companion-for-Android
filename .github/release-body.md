本 Release 由 GitHub Actions 基于 Home Assistant 官方 Android 应用自动构建，包含国内环境适配（无 GMS 依赖、前端组件 UA 修复等）。

## 安装说明

| 安装包 | 适用场景 |
| --- | --- |
| `app-full-release.apk` | 含 Google Play 服务依赖（Cronet 网络库），推荐大多数设备使用 |
| `app-minimal-release.apk` | 无谷歌服务（FOSS）版本，内置 Cronet，包体更大，适合没有 GMS 的设备 |

两个安装包包名不同（minimal 带 `.minimal` 后缀），可以同时安装。

## 升级方式

- 应用内会自动检测新版本，下载与当前版本匹配的 APK 并提示安装；
- 也可直接下载上方 APK 覆盖安装（签名相同）。
