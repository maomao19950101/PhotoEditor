# AI修图大师 📸

一款专为旅游摄影设计的AI修图App，集成10种网红滤镜风格和智能美颜功能，支持批量处理照片。

## ✨ 核心功能

### 🎨 10种网红滤镜
| 滤镜名称 | 效果描述 |
|---------|---------|
| 清透感 | 高亮度、冷白皮，适合人像 |
| 胶片复古 | 暖色调、颗粒感、暗角，街拍必备 |
| 日系清新 | 低饱和、偏绿、柔和，适合日常 |
| 法式浪漫 | 暖黄、柔光、朦胧，穿搭美食 |
| 夜景霓虹 | 青橙对比、高饱和，城市夜景 |
| ins风 | 低饱和、偏灰、高级感 |
| 黑白 | 经典黑白风格 |
| 美食 | 暖调、增强食欲 |
| 人像 | 自然美颜优化 |
| 风景 | 饱和度增强，突出层次 |

### 🤖 AI智能功能
- **美颜**：磨皮、美白、瘦脸、大眼
- **去物体**：AI去除路人、杂物
- **换天空**：7种天空效果（蓝天/晚霞/星空/彩虹）
- **画质增强**：超分辨率、智能降噪
- **场景识别**：自动识别风景/人像/美食等场景优化
- **智能调色**：一键AI调色

### 📦 批量处理
- 一次处理 **50+张** 照片
- **4线程并发** 处理，带实时进度
- 批量应用滤镜/美颜/水印
- 自定义导出命名规则（IMG_001, IMG_002...）

### 🏷️ 水印功能
- 文字水印（自定义内容）
- 日期/地点自动添加
- 图片水印
- 编号水印（批量时自动递增）

## 📱 界面预览

```
┌─────────────────────────────────────────┐
│ 📸 AI修图大师                 [设置]    │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐   │
│  │                                 │   │
│  │      [选择照片开始编辑]          │   │
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│ 🎨 热门滤镜                             │
│ ┌────────┬────────┬────────┬────────┐  │
│ │ 清透感 │胶片复古│日系清新│法式浪漫│  │
│ └────────┴────────┴────────┴────────┘  │
│                                         │
│ ✨ AI功能                               │
│ [智能美颜] [去路人] [换天空] [增强]    │
│                                         │
├─────────────────────────────────────────┤
│  [相册]    [编辑]    [批量]    [我的]  │
└─────────────────────────────────────────┘
```

## 🛠 技术栈

- **语言**: Kotlin
- **架构**: MVVM + Repository模式
- **依赖注入**: Hilt
- **数据库**: Room (编辑历史)
- **图像处理**: GPUImage + 自定义算法
- **并发**: Kotlin Coroutines + Flow
- **UI**: Material Design 3 + ViewBinding
- **导航**: Navigation Component
- **分页**: Paging 3

## 📋 系统要求

- **最低Android版本**: Android 7.0 (API 24)
- **目标Android版本**: Android 14 (API 34)
- **Java版本**: 17

## 🚀 快速开始

### 1. 克隆项目
```bash
git clone https://github.com/YOUR_USERNAME/PhotoEditor.git
cd PhotoEditor
```

### 2. 打开项目
使用 Android Studio 打开项目文件夹

### 3. 同步Gradle
等待 Gradle Sync 完成

### 4. 运行
点击 Run 按钮或 `Shift + F10`

## 📦 构建Release APK

```bash
# 在Android Studio中
Build → Generate Signed Bundle/APK → APK

# 或使用命令行
./gradlew assembleRelease
```

APK输出路径: `app/build/outputs/apk/release/`

## 📝 项目结构

```
PhotoEditor/
├── app/src/main/java/com/photo/editor/
│   ├── ai/                 # AI图像处理
│   ├── data/               # 数据层
│   │   ├── model/          # 数据模型
│   │   └── repository/     # 仓库
│   ├── ui/                 # UI层
│   │   ├── activity/       # Activity
│   │   ├── fragment/       # Fragment
│   │   ├── adapter/        # 适配器
│   │   └── viewmodel/      # ViewModel
│   └── utils/              # 工具类
├── app/src/main/res/       # 资源文件
└── docs/                   # 文档
```

## ⚙️ 配置说明

### 百度AI API（可选）
如需使用云端AI功能，请在 `local.properties` 中添加：
```properties
BAIDU_AI_APP_ID=your_app_id
BAIDU_AI_API_KEY=your_api_key
BAIDU_AI_SECRET_KEY=your_secret_key
```

### 滤镜强度调节
在 `FilterRepository.kt` 中可调整默认滤镜强度

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 许可证

MIT License

## 💬 联系方式

如有问题或建议，欢迎联系！

---

**Made with ❤️ for travel photography enthusiasts**
