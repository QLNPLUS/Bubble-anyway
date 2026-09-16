# Bubble Anyway Wiki

适用于 Bubble Anyway 1.1.0 的全部支持版本。

Bubble Anyway 是一个通用的信息气泡提示模组，支持服务端触发、客户端 KubeJS 触发、主题、本地资源、自动布局、动画、物品图标和九宫格 PNG 背景。

## 支持版本

| 加载器 | Minecraft |
|---|---|
| Forge | 1.19.2 |
| Forge | 1.20.1 |
| NeoForge | 1.21.1 |
| NeoForge | 1.26.1.2 |
| Fabric | 1.20.1 |
| Fabric | 1.21.1 |

请安装与 Minecraft 版本和加载器都匹配的 JAR。本文中的 API、JSON、主题和 KubeJS 示例适用于全部版本；个别加载器的 Toast 配置位置和渲染事件实现会有所不同。

## 1.1.0 更新

- 增加可选的客户端诊断功能和自动化冒烟测试支持。
- 修复多个气泡快速进入时的空间检测、排队和覆盖绘制顺序。
- 气泡数量不再使用固定上限；屏幕空间不足时进入等待队列，空间释放后自动补位。
- 原版成就、配方解锁和 FTB Quests Toast 接管支持已迁移到各版本实现。
- NeoForge 1.21.1 的游戏 HUD 绘制位于原生 HUD 最后一层之后，以减少聊天栏和快捷栏覆盖问题。
- Forge 1.19.2 修复了物品图标在气泡外绘制的问题。

## 快速开始

### 普通 JSON

~~~javascript
BubbleAnyway.showJson(JSON.stringify({
  id: 'welcome',
  text: '欢迎进入服务器\n祝你游戏愉快',
  icon: 'minecraft:diamond',
  anchor: 'CENTER_TOP',
  y: 18,
  animation: 'SLIDE_FROM_TOP',
  duration: 100,
  priority: 100
}));
~~~

### 主题 JSON

有 theme 时，客户端会优先读取本地主题，未填写的参数从主题中取得：

~~~javascript
BubbleAnyway.showJson(JSON.stringify({
  id: 'showcase_top_left',
  theme: 'testtheme',
  text: 'text'
}));
~~~

如果没有填写命名空间，testtheme 等价于 bubble_anyway:testtheme。

参数覆盖顺序如下：

~~~text
BubbleSpec 默认值 < 主题定义 < 当前气泡 JSON
~~~

## JSON 参数

下面的参数可以用于普通气泡 JSON，也可以用于主题定义。主题定义可以只写其中一部分。

| 参数 | 类型 | 默认值 | 可用值或范围 | 说明 |
|---|---|---:|---|---|
| id | 字符串 | 随机 UUID | 任意非空字符串 | 气泡实例 ID。replace 为 true 时，相同 ID 的旧气泡会被替换。主题完整配置时可以省略。 |
| theme | 字符串 | 无 | namespace:path 或简写 path | 主题 ID。存在此字段时使用主题气泡网络流程。 |
| text | 字符串 | 无 | 普通气泡需要非空文本 | 显示文本，支持换行；`remove: true` 请求可以省略。使用主题时可以由主题提供，也可以由当前气泡覆盖。 |
| message | 字符串 | 无 | 同 text | text 的兼容别名。text 优先。 |
| icon | 字符串 | "" | 物品 ID，例如 minecraft:diamond | 显示在文字左侧的物品图标。 |
| item | 字符串 | "" | 同 icon | icon 的兼容别名。 |
| iconType | 字符串 | AUTO | AUTO、ITEM、TEXTURE、NONE | AUTO 先按物品 ID 查找，再按 PNG 纹理查找；ITEM 强制物品图标；TEXTURE 强制 PNG；NONE 禁用图标。 |
| iconSize | 整数 | 16 | 8 到 64 | 图标显示尺寸，单位为 GUI 像素。超出范围会被限制。 |
| iconGap | 整数 | 6 | 0 到 64 | 图标与文字之间的间距。 |
| iconOffsetX | 整数 | 0 | -4096 到 4096 | 图标相对于自动布局位置的水平偏移，单位为 GUI 像素。 |
| iconOffsetY | 整数 | 0 | -4096 到 4096 | 图标相对于自动布局位置的垂直偏移，单位为 GUI 像素。 |
| textOffsetX | 整数 | 0 | -4096 到 4096 | 文字相对于对齐后位置的水平偏移，单位为 GUI 像素。 |
| textOffsetY | 整数 | 0 | -4096 到 4096 | 文字相对于默认文字起始位置的垂直偏移，单位为 GUI 像素。 |
| textColor | 颜色 | #FFFFFFFF | #RRGGBB、#AARRGGBB 或整数 | 文字颜色。六位颜色自动使用不透明 Alpha。主题推荐使用此名称。 |
| color | 颜色 | 无 | 同 textColor | textColor 的兼容别名；当两者同时存在时 textColor 优先。 |
| backgroundColor | 颜色 | #E6111720 | #RRGGBB、#AARRGGBB 或整数 | 未使用 PNG 背景时的背景颜色。 |
| background | 字符串 | "" | 资源路径，例如 bubble_anyway:textures/gui/example.png | 背景 PNG 资源。路径不写 assets/。 |
| backgroundBorder | 整数 | 0 | 0 到 1024 | 大于 0 时启用九宫格背景。表示四边边框的源像素宽度。 |
| backgroundGuide | 整数 | 0 | 0 到 16 | 九宫格边框与中心之间要跳过的参考线像素数。66x66 黑色参考线 PNG 推荐设为 1。 |
| sound | 字符串 | minecraft:ui.button.click | 音效 ID 或空字符串 | 气泡出现时播放的 UI 音效。空字符串关闭音效。 |
| soundVolume | 小数 | 1.0 | 0.0 到 2.0 | 音效音量。设为 0 也会关闭音效。 |
| soundPitch | 小数 | 1.0 | 0.5 到 2.0 | 音效音调。 |
| x | 整数 | 0 | 任意整数 | 相对于锚点的水平偏移，单位为 GUI 像素。 |
| y | 整数 | 0 | 任意整数 | 相对于锚点的垂直偏移，单位为 GUI 像素。 |
| width | 整数 | 0 | 0 到 4096 | 0 为自动宽度；大于 0 时使用指定气泡宽度。 |
| height | 整数 | 0 | 0 到 4096 | 0 为自动高度；大于 0 时至少使用指定高度，文字过多时仍会增高避免裁切。 |
| maxWidth | 整数 | 320 | 40 到 4096 | 自动宽度模式下的最大文字换行宽度。 |
| padding | 整数 | 10 | 0 到 128 | 气泡内边距，单位为 GUI 像素。 |
| lineSpacing | 整数 | 0 | 0 到 128 | 文本行之间额外增加的 GUI 像素间距，会参与自动高度计算。 |
| textAlign | 字符串 | LEFT | LEFT、CENTER、RIGHT | 文字左对齐、居中或右对齐。 |
| align | 字符串 | 无 | 同 textAlign | textAlign 的兼容别名。 |
| duration | 整数 | 100 | -1 或 1 到 72000 | 显示时长，单位为 tick。20 tick 约等于 1 秒；`-1` 表示永久显示。 |
| fadeIn | 整数 | 8 | 0 到 duration | 淡入时长，单位为 tick。 |
| fadeOut | 整数 | 12 | 0 到 duration | 淡出时长，单位为 tick。 |
| slideIn | 整数 | 8 | 0 到 duration | 滑入时长，单位为 tick；仅对 `SLIDE_FROM_*` 生效。 |
| slideOut | 整数 | 12 | 0 到 duration | 滑出时长，单位为 tick；仅对 `SLIDE_FROM_*` 生效，设为 0 可禁用滑出。 |
| priority | 整数 | 0 | 任意整数 | 显示优先级。数值越大，优先级越高。 |
| layer | 字符串 | BELOW_PAUSE | BELOW_PAUSE、ABOVE_PAUSE | 暂停页面层级。普通气泡默认在暂停页面下方；成就、配方和 FTB Toast 接管气泡自动使用 `ABOVE_PAUSE`。 |
| fontSize | 小数 | 1.0 | 0.5 到 4.0 | 字体和气泡整体缩放倍数。 |
| scale | 小数 | 1.0 | 同 fontSize | fontSize 的兼容别名。 |
| bold | 布尔值 | false | true、false | 粗体。 |
| italic | 布尔值 | false | true、false | 斜体。 |
| underlined | 布尔值 | false | true、false | 下划线。 |
| strikethrough | 布尔值 | false | true、false | 删除线。 |
| obfuscated | 布尔值 | false | true、false | 乱码效果。 |
| shadow | 布尔值 | true | true、false | 是否绘制文字阴影。 |
| replace | 布尔值 | true | true、false | 相同 id 的气泡是否替换。 |
| remove | 布尔值 | false | true、false | 为 true 时移除同 ID 的活动或等待中气泡；活动气泡会播放原气泡的退出动画，不需要填写 text。 |
| anchor | 字符串 | CENTER_TOP | 见锚点列表 | 气泡定位基准点。 |
| animation | 字符串 | FADE | 见动画列表 | 气泡进入和离开动画。 |
| textParts | 数组 | 无 | 多个文本片段对象 | 支持多行、每段独立颜色、字号、粗体、斜体、下划线、删除线、乱码和阴影。存在时优先用于绘制文本。 |
| textStyles | 对象 | 无 | role 到样式对象的映射 | 为 textParts 中的 role 提供默认样式，例如 title、subtitle。当前片段字段优先于角色样式。 |

### 颜色格式

~~~json
{
  "textColor": "#FFFFFF",
  "backgroundColor": "#E6111720"
}
~~~

也可以使用整数，例如 -4521984 或 0xE6111720。`color` 仍可作为 `textColor` 的兼容别名，推荐使用 `textColor` 和十六进制字符串。

### 文本换行

JSON 字符串中使用 \n 表示换行。`lineSpacing` 可以在每一行之间增加额外间距。JavaScript 源码中的示例：

~~~javascript
text: '第一行\n第二行'
~~~

气泡会先处理手动换行，再根据 maxWidth 自动换行。

## 锚点值

| 值 | 位置 |
|---|---|
| TOP_LEFT | 左上 |
| CENTER_TOP | 顶部中央 |
| TOP_RIGHT | 右上 |
| CENTER_LEFT | 左侧中央 |
| CENTER | 屏幕中央 |
| CENTER_RIGHT | 右侧中央 |
| BOTTOM_LEFT | 左下 |
| CENTER_BOTTOM | 底部中央 |
| BOTTOM_RIGHT | 右下 |

兼容别名：

~~~text
TOP_CENTER    -> CENTER_TOP
BOTTOM_CENTER -> CENTER_BOTTOM
~~~

枚举值不区分大小写，空格和连字符会按下划线处理。例如 top-center 可以解析为 CENTER_TOP。

### 坐标规则

x 和 y 是相对于锚点的偏移：

~~~javascript
{
  anchor: 'CENTER_TOP',
  x: 0,
  y: 18
}
~~~

对于右侧锚点，正 x 表示向右偏移；对于底部锚点，正 y 表示向上偏移的距离语义。气泡最终位置会保留屏幕边距，滑入动画过程允许暂时位于屏幕外。

## 动画值

| 值 | 效果 |
|---|---|
| FADE | 淡入淡出 |
| SLIDE_FROM_LEFT | 从屏幕左侧外部滑入，结束时向左侧外部滑出 |
| SLIDE_FROM_RIGHT | 从屏幕右侧外部滑入，结束时向右侧外部滑出 |
| SLIDE_FROM_TOP | 从屏幕顶部外部滑入，结束时向顶部外部滑出 |
| SLIDE_FROM_BOTTOM | 从屏幕底部外部滑入，结束时向底部外部滑出 |

兼容别名：

~~~text
SLIDE_LEFT   -> SLIDE_FROM_LEFT
SLIDE_RIGHT  -> SLIDE_FROM_RIGHT
SLIDE_TOP    -> SLIDE_FROM_TOP
SLIDE_BOTTOM -> SLIDE_FROM_BOTTOM
~~~

## 九宫格背景

### 普通 PNG

普通九宫格图片可以使用 64x64，并设置：

~~~javascript
{
  background: 'bubble_anyway:textures/gui/background.png',
  backgroundBorder: 8
}
~~~

backgroundBorder: 8 表示左、右、上、下边缘各取 8 个源像素，四角保持原尺寸，四条边和中心区域按目标气泡尺寸拉伸。

### 带黑色参考线的 PNG

带参考线的图片可以使用 66x66，并设置：

~~~javascript
{
  background: 'bubble_anyway:textures/gui/background.png',
  backgroundBorder: 8,
  backgroundGuide: 1
}
~~~

66x66、backgroundBorder: 8、backgroundGuide: 1 时：

~~~text
源图片尺寸: 66x66
边框尺寸: 8px
参考线: 每条分界 1px
实际中心区域: 48x48
~~~

参考线位于边框和中心区域之间，不属于任何一个九宫格，也不会参与拼接或实际显示。绘制时会将每个源矩形的纹理采样范围向内收半个像素，避免纹理过滤把参考线渗入拉伸区域。

资源文件放置位置：

~~~text
src/main/resources/assets/<namespace>/textures/gui/example.png
~~~

资源引用路径不包含 assets/：

~~~text
<namespace>:textures/gui/example.png
~~~

如果 PNG 无法读取或资源不存在，会退回使用 backgroundColor。

## 物品图标

~~~javascript
{
  text: '获得奖励',
  icon: 'minecraft:diamond',
  iconSize: 16,
  iconGap: 6
}
~~~

图标位于文本左侧，并参与自动宽度和自动高度计算。图标会跟随气泡的淡入、淡出和滑入、滑出动画。

### PNG 图标

`iconType: 'TEXTURE'` 时，`icon` 填写纹理资源路径，模组会读取 PNG 的实际宽高并保持比例缩放到 `iconSize` 的方框内：

~~~javascript
{
  text: 'FTB Quests 特殊图标',
  icon: 'my_mod:textures/gui/quest_icon.png',
  iconType: 'TEXTURE',
  iconSize: 24
}
~~~

`iconType: 'AUTO'` 是默认值，会先尝试物品 ID，再尝试纹理路径。图标透明度会和气泡背景、文本使用同一个动画透明度。

## 多段文本

普通 `text` 仍然支持 `\\n` 换行。需要标题和副标题使用不同颜色或字号时，可以使用 `textParts`：

~~~json
{
  "textParts": [
    {"role": "title", "text": "任务完成"},
    {"role": "subtitle", "text": "\\n奖励已经发放"}
  ],
  "textStyles": {
    "title": {"color": "#FFFF55", "bold": true, "scale": 1.1},
    "subtitle": {"color": "#FFFFFF", "italic": true, "scale": 0.9, "shadow": false}
  }
}
~~~

每个片段也可以直接写 `color`、`scale`、`bold`、`italic`、`underlined`、`strikethrough`、`obfuscated` 和 `shadow`，会覆盖对应的 `textStyles`。片段会参与自动宽度、自动高度和自动换行计算。

## 主题系统

主题是部分定义的 JSON。主题中未填写的字段使用模组默认值，气泡 JSON 中明确填写的字段会覆盖主题。

### 数据包主题

服务器数据包路径：

~~~text
data/<namespace>/bubble_anyway/themes/<theme_path>.json
~~~

例如 data/my_mod/bubble_anyway/themes/quest_notice.json 的主题 ID 是 my_mod:quest_notice。

~~~json
{
  "textColor": "#FFFFFFFF",
  "background": "bubble_anyway:textures/gui/background.png",
  "backgroundBorder": 8,
  "backgroundGuide": 1,
  "padding": 12,
  "textAlign": "CENTER",
  "anchor": "CENTER_TOP",
  "y": 18,
  "animation": "SLIDE_FROM_TOP",
  "fadeIn": 8,
  "fadeOut": 12,
  "duration": 100,
  "priority": 100,
  "replace": true
}
~~~

### 配置文件主题

服务器和客户端都支持：

~~~text
config/bubble_anyway/themes.json
~~~

推荐格式：

~~~json
{
  "themes": {
    "bubble_anyway:warning": {
      "textColor": "#FFFFFFFF",
      "backgroundColor": "#D9A83232",
      "padding": 10,
      "anchor": "CENTER_TOP",
      "y": 18,
      "animation": "SLIDE_FROM_TOP",
      "duration": 100,
      "priority": 200,
      "replace": true
    }
  }
}
~~~

未使用 themes 外层对象时，也可以直接使用主题 ID 到主题定义的映射。

### 客户端本地主题

第三方 mod 或客户端资源包可以把主题放在：

~~~text
assets/<namespace>/bubble_anyway/themes/<theme_path>.json
~~~

客户端主题查找顺序：

~~~text
客户端 config/bubble_anyway/themes.json
    > 客户端资源主题
    > 当前连接中按需缓存的服务器主题
~~~

同名主题优先使用客户端本地主题。服务器数据包中的主题不会在登录时全量发送给客户端。

### /reload

服务器执行 /reload 时会重新读取：

- 服务器数据包中的 data/<namespace>/bubble_anyway/themes/；
- 服务器的 config/bubble_anyway/themes.json。

新的主题会用于之后触发的气泡。服务器会向当前在线客户端发送一次完整主题同步，客户端同时刷新本地资源和配置主题缓存；客户端本地同名主题仍然优先，因此修改客户端配置后执行 `/reload` 也会生效。已经显示中的气泡不会被重绘，重新触发后使用新主题。

### 主题与资源

主题 JSON 可以引用 background 和 sound，但服务器数据包不会自动把 PNG 或音效文件上传到客户端。引用的资源必须存在于 Bubble Anyway 本身、提供主题的第三方 mod 或客户端资源包。

## 原版和第三方 Toast 接管

支持 Toast 接管的版本可以将以下提示转换成 Bubble Anyway 气泡：

- 原版成就：Task、Goal、Challenge 三种 frame 分别使用独立主题；
- 原版配方解锁提示；
- FTB Quests 的完成提示和奖励提示；
- FTB Quests 能提供物品图标时使用物品图标，提供纹理资源时使用 PNG 图标。

各加载器的接管入口不同，但配置含义一致：

- Forge 1.19.2、Forge 1.20.1：Forge 客户端 TOML 配置；
- NeoForge 1.21.1、NeoForge 1.26.1.2：NeoForge 客户端 TOML 配置；
- Fabric 1.20.1、Fabric 1.21.1：模组生成的客户端 TOML 配置，使用 Fabric ToastManager 注入接管。

开关使用 Forge 的 TOML 客户端配置，文件由模组首次启动客户端时自动生成：

~~~text
config/bubble_anyway/client.toml
~~~

Forge 1.20.1 的示例文件见 `examples/config/bubble_anyway/client.toml`。主题仍然放在 `config/bubble_anyway/themes.json`，配置中只填写主题 ID：

~~~toml
[toast]
enabled = true
fallback = "ORIGINAL"

[toast.advancement]
enabled = true
taskTheme = "bubble_anyway:toast_advancement_task"
goalTheme = "bubble_anyway:toast_advancement_goal"
challengeTheme = "bubble_anyway:toast_advancement_challenge"

[toast.recipe]
enabled = true
theme = "bubble_anyway:toast_recipe"

[toast.ftbQuests]
enabled = true
completionTheme = "bubble_anyway:toast_ftb_completion"
rewardTheme = "bubble_anyway:toast_ftb_reward"
~~~

气泡不会因为固定数量上限被丢弃。模组会按照最终目标矩形计算屏幕空间：当前锚点区域放不下的气泡进入等待队列，已有气泡结束后自动补位；队列按 `priority` 从高到低处理，同优先级按进入顺序处理。同一锚点会自动堆叠，不同锚点或自定义坐标允许重叠，并按绘制层顺序相互覆盖。

`fallback = "ORIGINAL"` 时，如果主题不存在或第三方 Toast 无法识别，会保留原提示。已经成功进入 Bubble Anyway 等待队列的 Toast 会取消原版 Toast，不再受原版 Toast 槽位数量限制。`fallback = "IGNORE"` 时会隐藏无法转换的支持类型提示。修改主题 JSON 后执行 `/reload`，之后新产生的气泡使用新主题；已经显示中的气泡不会重绘。

## 诊断与自动测试

每个版本都包含默认关闭的诊断开关。诊断功能只在需要排查环境或加载器问题时启用，默认不会输出额外日志，也不会自动显示测试气泡。

Forge 和 NeoForge 的配置文件为：

```text
config/bubble_anyway/client.toml
```

配置项位于 `[debug]`：

```toml
[debug]
diagnosticsEnabled = false
showTestBubble = false
```

Fabric 使用相同路径和字段。启用 `diagnosticsEnabled` 后进入世界，日志会记录主题是否可解析、Overlay 回调是否触发、Toast 接管入口是否收到对象、当前 active/pending 数量以及图标转换结果。`showTestBubble` 会额外显示一个带钻石图标的测试气泡，必须与 `diagnosticsEnabled` 同时启用。

仓库提供自动启动测试脚本：

```powershell
.\tools\run-bubble-diagnostics.ps1 -TimeoutSeconds 150
```

脚本会使用本地实例启动各版本、临时打开诊断、进入世界并收集报告，测试结束后恢复原配置。Forge 1.19.2 的启动参数不支持自动进入单人世界，因此该版本默认执行启动级测试；其余版本会执行世界级诊断。脚本生成的报告只用于本地排查，不属于发布包。

暂停页面打开时，气泡计时和等待队列仍会继续运行，行为接近原版 Toast；普通气泡位于暂停页面下方，设置 `layer: 'ABOVE_PAUSE'` 的气泡位于暂停页面上方。成就、配方和 FTB Quests Toast 接管气泡会自动使用 `ABOVE_PAUSE`。退出世界时活动和等待中的气泡都会清空。

## 指令

指令需要权限等级 2。

### 普通 JSON

~~~text
/bubble show <targets> <json>
~~~

### 主题文本

~~~text
/bubble show theme <targets> <themeId> <text>
~~~

示例：

~~~text
/bubble show theme @a bubble_anyway:warning 服务器将在五分钟后重启
~~~

### 清除

~~~text
/bubble clear
/bubble clear <targets>
~~~

## KubeJS 服务端

加载类：

~~~javascript
const BubbleServer = Java.loadClass(
  'com.bubbleanyway.kubejs.BubbleKubeJSServerApi'
);
~~~

普通气泡：

~~~javascript
BubbleServer.show(event.player, '普通文本');

BubbleServer.showJson(event.player, JSON.stringify({
  id: 'login_hint',
  text: '欢迎进入服务器',
  icon: 'minecraft:diamond',
  duration: 100
}));
~~~

主题气泡：

~~~javascript
BubbleServer.showJson(event.player, JSON.stringify({
  id: 'quest_done',
  theme: 'my_mod:quest_notice',
  text: '任务已完成'
}));
~~~

也可以使用主题专用方法：

~~~javascript
BubbleServer.showTheme(event.player, 'my_mod:quest_notice', '任务已完成');

BubbleServer.showThemeJson(event.player, 'my_mod:quest_notice', JSON.stringify({
  id: 'quest_done',
  text: '任务已完成',
  priority: 250
}));
~~~

### 服务端 KubeJS 方法

| 方法 | 说明 |
|---|---|
| show(player, text) | 向一个玩家发送普通文本气泡。 |
| showJson(player, json) | 向一个玩家发送普通 JSON 或带 theme 的 JSON。 |
| showAll(server, text) | 向所有玩家发送普通文本气泡。 |
| showAllJson(server, json) | 向所有玩家发送普通 JSON 或带 theme 的 JSON。 |
| showTheme(player, themeId, text) | 向一个玩家发送主题文本气泡。 |
| showThemeJson(player, themeId, overridesJson) | 向一个玩家发送主题气泡，并覆盖指定字段。 |
| showAllTheme(server, themeId, text) | 向所有玩家发送主题文本气泡。 |
| showAllThemeJson(server, themeId, overridesJson) | 向所有玩家发送主题气泡，并覆盖指定字段。 |
| clear(player) | 清除一个玩家的气泡。 |
| clearAll(server) | 清除所有玩家的气泡。 |

兼容旧名称：

~~~javascript
const BubbleServer = Java.loadClass(
  'com.bubbleanyway.kubejs.BubbleKubeJSServerBindings'
);
~~~

BubbleKubeJSServerBindings 会转发到 BubbleKubeJSServerApi。

## KubeJS 客户端

加载类：

~~~javascript
const BubbleAnyway = Java.loadClass(
  'com.bubbleanyway.kubejs.BubbleKubeJSBindings'
);
~~~

~~~javascript
BubbleAnyway.show('客户端本地文本');

BubbleAnyway.showJson(JSON.stringify({
  id: 'client_hint',
  theme: 'bubble_anyway:warning',
  text: '客户端本地主题提示'
}));

BubbleAnyway.showTheme('bubble_anyway:warning', '主题文本');
BubbleAnyway.showThemeJson('bubble_anyway:warning', JSON.stringify({
  text: '只覆盖文字',
  textAlign: 'RIGHT'
}));

BubbleAnyway.clear();
~~~

永久显示和带退出动画的移除：

~~~javascript
BubbleAnyway.showJson(JSON.stringify({
  id: 'long_notice',
  text: '第一行\n第二行',
  lineSpacing: 4,
  animation: 'SLIDE_FROM_TOP',
  fadeOut: 12,
  slideOut: 12,
  duration: -1
}));

BubbleAnyway.showJson(JSON.stringify({
  id: 'long_notice',
  remove: true
}));
~~~

方法：

| 方法 | 说明 |
|---|---|
| show(text) | 显示普通本地文本气泡。 |
| showJson(json) | 显示普通 JSON 或带 theme 的本地主题 JSON。 |
| showTheme(themeId, text) | 显示本地主题气泡。 |
| showThemeJson(themeId, overridesJson) | 显示本地主题气泡并覆盖字段。 |
| clear() | 清除当前客户端的所有气泡。 |

## Java 服务端 API

类名：

~~~text
com.bubbleanyway.api.BubbleServerApi
~~~

主要方法：

~~~java
show(ServerPlayer player, BubbleSpec spec)
show(Collection<? extends ServerPlayer> players, BubbleSpec spec)
showJson(ServerPlayer player, String json)
showJson(Collection<? extends ServerPlayer> players, String json)
showAll(MinecraftServer server, BubbleSpec spec)
showAllJson(MinecraftServer server, String json)
showTheme(ServerPlayer player, String themeId, String text)
showTheme(Collection<? extends ServerPlayer> players, String themeId, String text)
showThemeJson(ServerPlayer player, String themeId, String overridesJson)
showThemeJson(Collection<? extends ServerPlayer> players, String themeId, String overridesJson)
showAllTheme(MinecraftServer server, String themeId, String text)
showAllThemeJson(MinecraftServer server, String themeId, String overridesJson)
clear(ServerPlayer player)
clear(Collection<? extends ServerPlayer> players)
clearAll(MinecraftServer server)
~~~

## 网络行为

没有 theme 时，服务端使用完整气泡网络包，发送完整的 BubbleSpec 参数。这是旧 API 的兼容模式。

有 theme 时：

1. 服务端解析并验证主题和当前气泡覆盖字段；
2. 服务端发送主题 ID、当前气泡覆盖字段；
3. 客户端优先查找本地主题；
4. 本地找不到时，客户端发送一个主题请求；
5. 服务端只返回请求的那一个主题定义；
6. 客户端缓存该主题，之后继续使用主题 ID 和覆盖字段。

因此，完整主题配置时，重复气泡不会重复传输背景、颜色、动画、坐标等样式参数。长文本和明确填写的覆盖字段仍然需要通过网络发送。

## 常见问题

### 主题找不到

检查以下内容：

- 主题 ID 是否正确；
- 数据包路径是否为 data/<namespace>/bubble_anyway/themes/<path>.json；
- 客户端资源路径是否为 assets/<namespace>/bubble_anyway/themes/<path>.json；
- 配置文件是否位于 config/bubble_anyway/themes.json；
- 服务器主题修改后是否执行了 /reload。

### 背景 PNG 没有显示

正确的 background 写法：

~~~text
bubble_anyway:textures/gui/background.png
~~~

不要写：

~~~text
assets/bubble_anyway/textures/gui/background.png
~~~

### 主题中的文字是否必须填写

不是必须。主题可以提供默认 text，也可以在每次调用时填写 text。普通无主题 JSON 仍然必须提供非空 text 或 message。

### 主题会不会自动上传 PNG

不会。主题定义和资源文件是两部分，客户端必须能够加载主题引用的图片和音效资源。

## 示例文件

- [Forge README](README.md)
- [配置主题示例](examples/config/bubble_anyway/themes.json)
- [数据包主题示例](examples/datapack/data/my_mod/bubble_anyway/themes/quest_notice.json)
- [客户端资源主题示例](examples/resourcepack/assets/my_mod/bubble_anyway/themes/quest_notice.json)
- [服务端 KubeJS 示例](examples/kubejs/server_scripts/bubble_anyway_example.js)
- [客户端 KubeJS 示例](examples/kubejs/client_scripts/bubble_anyway_client_example.js)
- [新增功能客户端测试脚本](examples/kubejs/client_scripts/bubble_anyway_new_features.js)
- [新增功能服务端测试脚本](examples/kubejs/server_scripts/bubble_anyway_new_features_server.js)
- [客户端 Toast 配置示例](examples/config/bubble_anyway/client.toml)
- [English Wiki](WIKI_EN.md)
