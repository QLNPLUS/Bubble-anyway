
## Bubble Anyway

Forge 1.19.2 的通用高优先级信息气泡提示模组。

### 特性

- `/bubble show <targets> <json>` 触发提示
- `/bubble clear [targets]` 清除提示
- 注册在 Forge GUI overlay 的最上层，可覆盖常规 HUD 与其他界面上的 GUI 内容
- 九宫格锚点：`TOP_LEFT`、`CENTER_TOP`、`TOP_RIGHT`、`CENTER_LEFT`、`CENTER`、`CENTER_RIGHT`、`BOTTOM_LEFT`、`CENTER_BOTTOM`、`BOTTOM_RIGHT`
- 动画：`FADE`、`SLIDE_FROM_LEFT`、`SLIDE_FROM_RIGHT`、`SLIDE_FROM_TOP`、`SLIDE_FROM_BOTTOM`
- `x`/`y` 是相对锚点的像素偏移；`fontSize` 是缩放倍数
- `iconOffsetX`/`iconOffsetY` 和 `textOffsetX`/`textOffsetY` 可按 GUI 像素微调图标与文字位置，默认都是 `0`
- 支持换行、自动换行、颜色、加粗、斜体、下划线、删除线、乱码、阴影
- 支持文本左对齐、居中、右对齐
- 支持通过物品 ID 显示 Minecraft 物品图标，图标位于文字左侧
- 支持气泡出现音效，默认使用 Minecraft 按钮音效
- 省略 `width`、`height` 时自动根据文字、换行和图标计算气泡尺寸
- 支持 `background` 纹理资源路径，例如 `bubble_anyway:textures/gui/example.png`
- `backgroundBorder` 大于 0 时启用九宫格背景；四角和四条边保持边框像素，中间区域自动拉伸。`backgroundGuide` 可跳过边框与中心之间的参考线像素，例如 66×66 图片使用 `backgroundBorder:8, backgroundGuide:1`。省略或设为 0 时整张 PNG 按气泡区域绘制
- 支持 `priority`、`id`、`replace`；气泡会根据屏幕空间自动排队，不使用固定数量上限

### 指令示例

```text
/bubble show @a {"id":"welcome","text":"欢迎进入服务器\n祝你游戏愉快","color":"#FFFFFF","backgroundColor":"#E6111720","anchor":"CENTER_TOP","x":0,"y":18,"animation":"SLIDE_FROM_TOP","duration":100,"fadeIn":8,"fadeOut":12,"priority":100,"fontSize":1.0,"bold":true}
```

带九宫格 PNG 的示例：

```text
/bubble show @a {"id":"notice","text":"这段文字可以变长","icon":"minecraft:diamond","iconSize":16,"iconGap":6,"background":"bubble_anyway:textures/gui/background.png","backgroundBorder":8,"backgroundGuide":1,"width":240,"padding":12,"textAlign":"CENTER","anchor":"CENTER_TOP","y":18,"animation":"SLIDE_FROM_TOP","fadeIn":10,"fadeOut":14,"duration":100,"priority":100,"replace":true}
```

清除所有玩家的气泡：

```text
/bubble clear
```

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

服务器事件中可用的方法：`showJson(player, json)`、`showAllJson(server, json)`、`show(player, text)`、`showAll(server, text)`、`clear(player)`、`clearAll(server)`。

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

### JSON 字段

`duration`、`fadeIn`、`fadeOut`、`slideIn`、`slideOut` 使用 tick，20 tick 约等于 1 秒。`fadeIn` 和 `fadeOut` 只控制透明度；`slideIn` 和 `slideOut` 只控制 `SLIDE_FROM_*` 的位移动画，`FADE` 模式会忽略它们。设置 `fadeIn: 0, fadeOut: 0, slideIn: 8, slideOut: 0` 可实现完全不透明地从屏幕外弹入。

`text` 必填。颜色字段推荐使用 `textColor`，同时兼容 `color`；支持 `#RRGGBB` 或 `#AARRGGBB`。`duration`、`fadeIn`、`fadeOut` 使用 tick，20 tick 约等于 1 秒。`width` 和 `height` 省略或设为 `0` 时自动适配；填写后分别固定气泡的最小宽度和最小高度，文字过多时仍会自动增高避免裁切。`maxWidth` 默认 `320`，用于自动宽度过长时换行。`textAlign` 支持 `LEFT`、`CENTER`、`RIGHT`，默认是 `LEFT`。`icon` 填物品 ID，例如 `minecraft:diamond`；`iconSize` 默认 `16`，`iconGap` 默认 `6`；`iconOffsetX`、`iconOffsetY`、`textOffsetX`、`textOffsetY` 默认都是 `0`，范围为 `-4096` 到 `4096`，只改变绘制位置，不参与自动宽高计算。`sound` 默认是 `minecraft:ui.button.click`，填写音效资源 ID即可更换；`soundVolume` 默认 `1.0`，范围 `0.0` 到 `2.0`；`soundPitch` 默认 `1.0`，范围 `0.5` 到 `2.0`。将 `sound` 设为空字符串或将 `soundVolume` 设为 `0` 可以关闭音效。普通 64×64 九宫格使用 `backgroundBorder:8`；如果 PNG 是 66×66，并在边框与中心之间保留 1px 黑色参考线，则使用 `backgroundBorder:8, backgroundGuide:1`，参考线不会参与拼接或显示。PNG 放在 `src/main/resources/assets/bubble_anyway/textures/gui/` 下，资源路径不写 `assets/`。
