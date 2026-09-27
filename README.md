# Pixel Magic · Android 17 M3E 纯正旗舰定制项目
针对 Google Pixel 9 Pro XL 专属调校的原生体验扩展套件。

## 核心功能
1. **锁屏灵犀一瞥流体胶囊 (3.0)**：
   - 收起态死死卡在锁屏底端【手电筒与相机正中间】(y = 36dp)；
   - 展开态大圆角浮动卡片呈现气象、外设全电量、闹钟精准倒计时与 80% 充电保护；
   - 彻底摒弃冗余音乐控件；解锁进入桌面瞬间彻底销毁隐形，绝不污染桌面。
2. **端侧意图任意门 (3.0)**：
   - 44dp 宽隐形触摸热区，调用 `setSystemGestureExclusionRects` 豁免系统侧滑返回拦截；
   - 展开为 M3E 半月弧形磁吸抽屉，自动应用壁纸莫奈色彩；
   - 动态获取焦点突破 Android 10+ 剪贴板读取限制。
3. **全局莫奈动态取色 (MonetColorProvider)**：
   - 实时读取 `system_neutral1_900` 与 `system_accent1_300`，浮窗色彩 100% 随壁纸动态呼吸流转。
4. **硬件特权控制**：
   - 120Hz 极速锁频、80% 电池充电上限、自由小窗调度。

## GitHub Actions 自动构建
代码推送到 GitHub 仓库后，GitHub Actions 将自动执行编译，并在 Actions -> Artifacts 中生成 `PixelMagic-Pure-M3E-Debug-v1.0.0` (app-debug.apk) 供直接下载。
