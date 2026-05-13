# AI 智能化妆 (MakeupAI) — Android

> 拍一张自拍，Claude 分析面部特征，Stability AI 生成四种专业妆效图

---

## 功能说明

1. **面部分析**：将自拍照发送给 Claude (claude-sonnet-4-20250514)，AI 识别肤色、脸型、五官特征，为每种妆效生成个性化的图像生成提示词
2. **妆效生成**：将提示词连同原始自拍发送给 Stability AI SDXL img2img，生成保留用户面部特征的妆效图
3. **四种风格**：日常清新妆 / 欧美烟熏妆 / 复古红唇妆 / 日系甜美妆
4. **一键保存**：将生成的妆效图保存至系统相册

---

## 环境要求

| 项目 | 要求 |
|---|---|
| Android Studio | Hedgehog 2023.1.1 及以上 |
| 最低 Android 版本 | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| 需要摄像头 | 前置摄像头（推荐） |
| 网络 | 需要互联网连接（API 调用） |

---

## API Key 获取

### 1. Anthropic API Key
- 访问：https://console.anthropic.com
- 注册账号 → API Keys → Create Key
- 格式：`sk-ant-api03-...`

### 2. Stability AI API Key（免费注册可获得试用额度）
- 访问：https://platform.stability.ai
- 注册账号 → Account → API Keys → Create API Key  
- 免费账户赠送 25 credits，生成 1 张图约消耗 1 credit
- 格式：`sk-...`

---

## 安装步骤

### 方法一：Android Studio 编译安装（推荐）

```bash
# 1. 解压项目
unzip MakeupAI.zip
cd MakeupAI

# 2. 配置 Android SDK 路径
# 编辑 local.properties：
#   sdk.dir=/Users/yourname/Library/Android/sdk   (macOS)
#   sdk.dir=C:/Users/yourname/AppData/Local/Android/Sdk   (Windows)

# 3. 用 Android Studio 打开项目目录
# 4. 等待 Gradle 同步完成
# 5. 连接 Android 手机（开启开发者模式）
# 6. 点击 Run ▶
```

### 方法二：直接安装 APK
如需直接安装 APK，在 Android Studio 中选择：
Build → Build Bundle(s) / APK(s) → Build APK(s)
生成的 APK 在 `app/build/outputs/apk/debug/` 目录

---

## 项目结构

```
app/src/main/
├── java/com/makeupai/app/
│   ├── Constants.kt                  # 全局常量（API URL、参数）
│   ├── api/
│   │   ├── AnthropicService.kt       # Claude API 调用（面部分析+提示词生成）
│   │   ├── StabilityService.kt       # Stability AI img2img 调用（妆效生成）
│   │   └── ApiException.kt           # 统一错误类型
│   ├── model/
│   │   └── MakeupStyle.kt            # 妆效数据模型
│   ├── adapter/
│   │   └── StyleGridAdapter.kt       # 2x2 网格 RecyclerView Adapter
│   └── ui/
│       ├── MainActivity.kt           # API Key 配置页
│       ├── CameraActivity.kt         # 前置摄像头自拍（CameraX）
│       ├── ResultActivity.kt         # 妆效生成与展示（4宫格）
│       └── FullscreenActivity.kt     # 单张妆效全屏查看
└── res/
    ├── layout/                        # 所有布局文件
    ├── drawable/                      # 矢量图标和形状
    └── values/                        # 颜色、字符串、主题
```

---

## 技术架构

### API 调用流程

```
用户自拍
  │
  ▼
Claude API (claude-sonnet-4-20250514)
  ├── 输入：自拍图片 (base64) + 提示词
  └── 输出：4种妆效的 JSON
         { name_cn, name_en, sd_prompt, characteristics }
  │
  ▼ (并发 × 4)
Stability AI SDXL img2img
  ├── 输入：原始自拍 + sd_prompt
  ├── image_strength: 0.38  (保留62%面部特征)
  ├── cfg_scale: 8
  └── 输出：1024×1024 JPEG 妆效图
  │
  ▼
展示在2×2网格，支持点击全屏、保存相册
```

### 关键参数说明

| 参数 | 值 | 作用 |
|---|---|---|
| `image_strength` | 0.38 | 图像变化强度，数值越低越保留原脸 |
| `cfg_scale` | 8 | 提示词引导强度，越高越贴近提示词 |
| `steps` | 30 | 扩散步骤，越多质量越高但越慢 |
| 并发数 | 2 | 同时发起的图像生成请求数 |

---

## 常见问题

**Q: 生成的图和本人差别太大？**  
调低 `Constants.kt` 中的 `IMAGE_STRENGTH`（例如改为 `0.25f`），面部特征保留更多

**Q: Stability AI 报 402 错误？**  
账户 credits 已用完，需要充值或更换 API Key

**Q: 生成速度很慢？**  
SDXL 30步约需 15-25 秒/张，4张合计 30-50 秒（2并发）。可将 `STEPS` 改为 `20` 加速

**Q: 图像质量较差？**  
确保自拍照光线充足、脸部清晰居中；或将 `STEPS` 改为 `40` 提升质量

---

## 后续开发方向（下一步）

- [ ] 选定妆效后进入逐步骤 AR 引导模式
- [ ] 实时摄像头颜色检测（判断化妆进度）
- [ ] 边缘过渡质量评分（晕染效果检测）
- [ ] 妆效历史记录与对比
- [ ] 品牌产品推荐联动

---

## 许可

MIT License — 仅供学习和原型验证使用
