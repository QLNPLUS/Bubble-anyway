
## Bubble Anyway

Forge 1.20.1 的通用高优先级信息气泡提示模组。

### 特性

- `/bubble show <targets> <json>` 触发提示
- `/bubble clear [targets]` 清除提示
- 注册在 Forge GUI overlay 的最上层，可覆盖常规 HUD 与其他界面上的 GUI 内容
- 九宫格锚点：`TOP_LEFT`、`CENTER_TOP`、`TOP_RIGHT`、`CENTER_LEFT`、`CENTER`、`CENTER_RIGHT`、`BOTTOM_LEFT`、`CENTER_BOTTOM`、`BOTTOM_RIGHT`
- 动画：`FADE`、`SLIDE_FROM_LEFT`、`SLIDE_FROM_RIGHT`、`SLIDE_FROM_TOP`、`SLIDE_FROM_BOTTOM`
- `x`/`y` 是相对锚点的像素偏移；`fontSize` 是缩放倍数
- 支持换行、自动换行、颜色、加粗、斜体、下划线、删除线、乱码、阴影
- 支持文本左对齐、居中、右对齐
- 支持通过物品 ID 显示 Minecraft 物品图标，图标位于文字左侧，并可分别微调图标和文字的 X/Y 偏移
- 支持气泡出现音效，默认使用 Minecraft 按钮音效
- 省略 `width`、`height` 时自动根据文字、换行和图标计算气泡尺寸
- 支持 `background` 纹理资源路径，例如 `bubble_anyway:textures/gui/example.png`
- `backgroundBorder` 大于 0 时启用九宫格背景；四角和四条边保持边框像素，中间区域自动拉伸。`backgroundGuide` 可跳过边框与中心之间的参考线像素，例如 66×66 图片使用 `backgroundBorder:8, backgroundGuide:1`。省略或设为 0 时整张 PNG 按气泡区域绘制
- 支持 `priority`、`id`、`replace`；气泡会根据屏幕空间自动排队，不使用固定数量上限
- 文字颜色使用 `textColor`，也兼容 `color`；支持 `#RRGGBB`、`#AARRGGBB` 或整数
- 支持可复用主题：主题定义来自数据包或 `config/bubble_anyway/themes.json`
- 主题气泡优先读取客户端本地主题；本地不存在时才按需向服务器请求对应主题
- 支持多段文本：标题和副标题可以分别设置颜色、字号、粗体、斜体和阴影
- 支持 PNG 纹理图标，并让图标跟随气泡的透明度和滑入滑出动画
- 可选接管原版成就、配方解锁以及 FTB Quests Toast，开关和主题 ID 位于客户端 TOML 配置

### 指令示例

```text
/bubble show @a {"id":"welcome","text":"欢迎进入服务器\n祝你游戏愉快","color":"#FFFFFF","backgroundColor":"#E6111720","anchor":"CENTER_TOP","x":0,"y":18,"animation":"SLIDE_FROM_TOP","duration":100,"fadeIn":8,"fadeOut":12,"priority":100,"fontSize":1.0,"bold":true}
```

带九宫格 PNG 的示例：

```text
/bubble show @a {"id":"notice","text":"这段文字可以变长","icon":"minecraft:diamond","iconSize":16,"iconGap":6,"iconOffsetX":2,"iconOffsetY":-1,"textOffsetX":4,"textOffsetY":1,"background":"bubble_anyway:textures/gui/background.png","backgroundBorder":8,"backgroundGuide":1,"width":240,"padding":12,"textAlign":"CENTER","anchor":"CENTER_TOP","y":18,"animation":"SLIDE_FROM_TOP","fadeIn":10,"fadeOut":14,"duration":100,"priority":100,"replace":true}
```

清除所有玩家的气泡：

```text
/bubble clear
```

### 主题

数据包主题放在 `data/<命名空间>/bubble_anyway/themes/<主题路径>.json`，例如：

```text
data/my_mod/bubble_anyway/themes/quest_notice.json
```

文件内容只需要填写希望预设的字段，`text` 可以省略：

```json
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
```

上面的主题 ID 是 `my_mod:quest_notice`。管理员也可以创建 `config/bubble_anyway/themes.json`，配置文件中的同名主题会覆盖数据包主题：

```json
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
      "priority": 200
    }
  }
}
```

执行 `/reload` 会重新加载服务器数据包和服务器配置主题，并向当前在线客户端同步完整主题目录，使客户端刷新本地主题缓存。主题中的 `background` 和 `sound` 仍然引用客户端已有的资源；数据包本身不会把 PNG 上传给客户端，因此 PNG 应由本模组、第三方 mod 或客户端资源包提供。

客户端本地主题可以放在客户端资源包或第三方 mod 的资源中：

```text
assets/<命名空间>/bubble_anyway/themes/<主题路径>.json
```

也可以放在客户端的 `config/bubble_anyway/themes.json`。客户端查找顺序是：客户端配置、客户端资源主题、按需缓存的服务器主题；如果同名，客户端配置优先于资源主题，客户端本地主题优先于服务器回退主题。

服务器不会在玩家登录时主动发送整套主题目录。第一次遇到客户端没有的主题时，客户端才请求这一条主题定义；收到后缓存到当前连接，后续气泡只发送主题 ID、气泡 ID、文字和覆盖字段。主题完整配置时，实际气泡包就只需要主题 ID 和文字，气泡 ID 只有在填写了 `id` 时才会额外发送。

使用主题的短指令：

```text
/bubble show theme @a bubble_anyway:warning 服务器将在五分钟后重启
```

最后的文字会覆盖主题中的 `text`。旧的 `/bubble show <targets> <json>` 仍然可用。

推荐的统一 JSON 格式如下；有 `theme` 时，其余未填写字段都从本地主题读取：

```javascript
BubbleAnyway.showJson(JSON.stringify({
  id: 'showcase_top_left',
  theme: 'testtheme',
  text: 'text'
}));
```

如果服务器发送这类 JSON，服务端只会把 `theme`、`id`、`text` 以及明确覆盖的字段转成主题气泡包，不会把主题的完整参数重复发送。

### KubeJS 服务端

把 `examples/kubejs/server_scripts/bubble_anyway_example.js` 复制到 KubeJS 的 `server_scripts` 目录即可。服务端脚本直接调用模组 API，通过服务器网络包发送到目标客户端，不执行 `/bubble` 指令。

```javascript
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    text: '欢迎进入服务器',
    icon: 'minecraft:diamond',
    animation: 'SLIDE_FROM_TOP',
    duration: 100
  }));
});
```

服务器事件中可用的方法：`showJson(player, json)`、`showAllJson(server, json)`、`show(player, text)`、`showAll(server, text)`、`showTheme(player, themeId, text)`、`showThemeJson(player, themeId, overridesJson)`、`showAllTheme(server, themeId, text)`、`showAllThemeJson(server, themeId, overridesJson)`、`clear(player)`、`clearAll(server)`。

主题调用示例：

```javascript
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'login_theme_hint',
    theme: 'bubble_anyway:warning',
    text: '欢迎回来'
  }));
});

// 只覆盖主题中的文字和优先级，其他样式继续使用主题预设。
BubbleServer.showThemeJson(event.player, 'my_mod:quest_notice', JSON.stringify({
  text: '任务已完成',
  priority: 250
}));
```

### KubeJS 客户端

把 `examples/kubejs/client_scripts/bubble_anyway_client_example.js` 复制到 KubeJS 的 `client_scripts` 目录。客户端脚本先用 `Java.loadClass` 加载本模组的本地桥接类，再直接调用，不经过服务器：

```javascript
const BubbleAnyway = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSBindings');
BubbleAnyway.show('本地提示');
BubbleAnyway.showJson(JSON.stringify({
  id: 'local_hint',
  text: '客户端脚本触发',
  anchor: 'CENTER_BOTTOM',
  y: 40,
  animation: 'FADE',
  sound: 'minecraft:ui.button.click',
  soundVolume: 1.0,
  soundPitch: 1.0,
  priority: 50
}));
BubbleAnyway.clear();
```

客户端 KubeJS 也可以调用本地配置主题：

```javascript
BubbleAnyway.showJson(JSON.stringify({
  id: 'showcase_top_left',
  theme: 'bubble_anyway:warning',
  text: '这是客户端本地提示'
}));

BubbleAnyway.showJson(JSON.stringify({
  theme: 'bubble_anyway:warning',
  text: '只覆盖文字',
  textAlign: 'RIGHT'
}));
```

最简客户端示例：只填写文字，其余布局参数使用默认值。

```javascript
BubbleAnyway.showJson(JSON.stringify({
  text: '自动适配宽高的提示\\n带一个钻石图标',
  icon: 'minecraft:diamond',
  anchor: 'CENTER_TOP',
  y: 18,
  animation: 'SLIDE_FROM_TOP',
  duration: 100
}));
```

能力演示脚本：`examples/kubejs/client_scripts/bubble_anyway_showcase.js` 使用 `ClientEvents.tick` 延迟轮换九个屏幕区域，展示不同动画、对齐方式、图标、九宫格背景、文本格式和自适应尺寸。

新增功能测试脚本：`examples/kubejs/client_scripts/bubble_anyway_new_features.js` 覆盖多段文本、文本角色样式、PNG 图标、无淡入淡出的滑入、FADE 忽略滑动时间、自动换行和主题覆盖。服务端网络测试脚本位于 `examples/kubejs/server_scripts/bubble_anyway_new_features_server.js`。

原版和 FTB Quests Toast 接管使用 `config/bubble_anyway/client.toml`，主题定义仍放在 `config/bubble_anyway/themes.json`。完整配置示例见 `examples/config/bubble_anyway/client.toml`。

### JSON 字段

`duration`、`fadeIn`、`fadeOut`、`slideIn`、`slideOut` 使用 tick，20 tick 约等于 1 秒。`fadeIn` 和 `fadeOut` 只控制透明度；`slideIn` 和 `slideOut` 只控制 `SLIDE_FROM_*` 的位移动画，`FADE` 模式会忽略它们。设置 `fadeIn: 0, fadeOut: 0, slideIn: 8, slideOut: 0` 可实现完全不透明地从屏幕外弹入。

`text` 必填。颜色字段推荐使用 `textColor`，同时兼容 `color`；支持 `#RRGGBB` 或 `#AARRGGBB`。`duration`、`fadeIn`、`fadeOut` 使用 tick，20 tick 约等于 1 秒。`width` 和 `height` 省略或设为 `0` 时自动适配；填写后分别固定气泡的最小宽度和最小高度，文字过多时仍会自动增高避免裁切。`maxWidth` 默认 `320`，用于自动宽度过长时换行。`textAlign` 支持 `LEFT`、`CENTER`、`RIGHT`，默认是 `LEFT`。`icon` 填物品 ID，例如 `minecraft:diamond`；`iconSize` 默认 `16`，`iconGap` 默认 `6`；`iconOffsetX`、`iconOffsetY` 默认 `0`，用于按 GUI 像素微调图标位置。`textOffsetX`、`textOffsetY` 默认 `0`，用于按 GUI 像素微调文字位置，支持负数。偏移只改变绘制位置，不参与自动宽高计算。`layer` 默认是 `BELOW_PAUSE`，可设为 `ABOVE_PAUSE`；普通气泡默认位于暂停页面下方，成就、配方和 FTB Toast 接管气泡会自动使用 `ABOVE_PAUSE`。`sound` 默认是 `minecraft:ui.button.click`，填写音效资源 ID即可更换；`soundVolume` 默认 `1.0`，范围 `0.0` 到 `2.0`；`soundPitch` 默认 `1.0`，范围 `0.5` 到 `2.0`。将 `sound` 设为空字符串或将 `soundVolume` 设为 `0` 可以关闭音效。普通 64×64 九宫格使用 `backgroundBorder:8`；如果 PNG 是 66×66，并在边框与中心之间保留 1px 黑色参考线，则使用 `backgroundBorder:8, backgroundGuide:1`，参考线不会参与拼接或显示。PNG 放在 `src/main/resources/assets/bubble_anyway/textures/gui/` 下，资源路径不写 `assets/`。
