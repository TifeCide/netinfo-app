# NetInfo

**NetInfo** 是一款面向 Android 的网络信息查看与诊断工具，基于 Jetpack Compose 构建，采用 Material 3 设计风格。

---

## 功能概览

### 状态页（Status）
实时显示当前网络的详细信息：

- 网络类型（Wi-Fi / 移动数据 / 以太网）及连接状态
- 本地 IPv4 / IPv6 地址、网关、DNS 服务器
- MAC 地址、接口名称、是否计费网络、VPN 是否激活
- **Wi-Fi 专属**：SSID、BSSID、RSSI（含信号强度图形化展示）、链路速率、频段、信道、Wi-Fi 标准
- **移动数据专属**：运营商名称、网络制式标签（4G/5G 等）、Cell ID、LAC、5G 模式
- **以太网专属**：链路速率、双工模式
- **外网信息**：公网 IP、所属城市/地区/国家、ASN/运营商、时区、国旗

支持**暂停刷新**（暂停后停止轮询与网络回调，节省资源）。

### 工具页（Tools）
提供四种网络诊断工具：

| 工具 | 说明 |
|------|------|
| **Ping** | 向指定主机发送 4 个 ICMP 包，展示丢包率和平均延迟 |
| **外网信息** | 查询当前公网 IP 的地理位置和运营商信息 |
| **局域网扫描仪** | 对当前子网（自动计算范围）并发探测在线设备 |
| **路由跟踪** | 通过 TTL 递增的 Ping 实现最多 15 跳的路由追踪 |

### 关于页（About）
显示应用版本、包名、构建类型，以及指向 GitHub 开源仓库的链接和开源许可声明。

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM（ViewModel + StateFlow） |
| 导航 | AndroidX Navigation Compose |
| 最低 SDK | API 24（Android 7.0） |
| 目标 SDK | API 34（Android 14） |
| 构建工具 | AGP 8.3.2 + Gradle Kotlin DSL |

---

## 项目结构

```
app/src/main/java/cn/aeolusdev/netinfo/
├── MainActivity.kt                        # 应用入口，启用边到边显示
├── model/
│   └── NetworkDetailInfo.kt               # 数据模型（NetworkDetailInfo、ExternalNetworkInfo、枚举）
├── network/
│   └── NetworkInfoRepository.kt           # 网络状态采集（ConnectivityManager、WifiManager、TelephonyManager）
└── ui/
    ├── NetworkVisuals.kt                   # 网络类型图标/标签/状态标签辅助函数
    ├── components/
    │   └── SignalStrengthBar.kt            # Wi-Fi 信号强度条自定义组件
    ├── navigation/
    │   └── NavGraph.kt                     # 导航图、顶部栏、底部导航栏
    ├── screens/
    │   ├── NetworkViewModel.kt             # 网络状态 ViewModel（封装 Repository）
    │   ├── StatusScreen.kt                 # 网络状态页 UI
    │   ├── ToolsScreen.kt                  # 工具列表页 UI（Ping / 外网信息 / 局域网 / 路由）
    │   └── ToolsViewModel.kt               # 工具页 ViewModel（含 Ping / LAN 扫描 / 路由追踪逻辑）
    └── theme/
        ├── Color.kt                        # 颜色定义
        ├── Theme.kt                        # Material 3 主题（支持动态颜色）
        └── Type.kt                         # 字体排版
```

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `ACCESS_NETWORK_STATE` | 读取网络连接状态 |
| `ACCESS_WIFI_STATE` | 读取 Wi-Fi 信息（SSID、BSSID、RSSI 等） |
| `CHANGE_WIFI_STATE` | 触发 Wi-Fi 扫描（部分型号需要） |
| `INTERNET` | 查询公网 IP 信息 |
| `ACCESS_FINE_LOCATION` | Android 10+ 获取 SSID / 精确 Wi-Fi 信息所需 |
| `ACCESS_COARSE_LOCATION` | 获取移动网络小区信息 |

---

## 构建方法

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 开源许可

本项目基于 **Apache License 2.0** 开源发布。

主要依赖均采用 Apache 2.0 许可证：Jetpack Compose、Material 3、AndroidX Navigation、Kotlin Coroutines。
