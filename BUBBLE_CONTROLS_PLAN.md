# Bubble Anyway 控件与交互 API 计划

> 状态：仅规划，尚未实现
>
> 分析日期：2026-09-17
>
> 适用分支：Forge 1.19.2、Forge 1.20.1、NeoForge 1.21.1、NeoForge 1.26.1.2、Fabric 1.20.1、Fabric 1.21.1

## 1. 目标与范围

为气泡增加按钮等交互控件，并提供一套可被其他 mod 和 KubeJS 使用的公共交互事件 API。

本期明确纳入：

- `BUTTON` 控件和多个按钮布局，例如“确定”“取消”；
- 控件主题，包括文本样式、默认/hover/pressed/disabled 外观和材质；
- `BubbleSpec.Builder`、`BubbleControl.Builder` 和控件布局 Builder；
- 主题中定义稳定控件，气泡调用时只传动态内容；
- 客户端点击包只传 `bubbleId + controlId`；
- 服务端 Java 点击事件；
- KubeJS 服务端和客户端点击事件；
- 取消玩家登录/加入服务器时的全量主题定义同步，继续使用缺失主题按需请求；
- 保持现有 JSON、命令、Java API 和旧 KubeJS API 兼容。

本期明确不纳入：

- 服务器主题版本号、哈希、manifest 或 `not modified` 协议；
- 额外的限流、压缩、批量合并和新的强制总大小限制；
- action 注册表或由 Bubble Anyway 直接决定业务动作；
- 模板变量替换，例如 `${player.name}`；
- 复选框、图片、进度条和输入框的实际实现。

第一期的数据结构为后续控件类型预留扩展，但只实现按钮的绘制、命中和点击事件。

### 1.1 关键命名决定

本期不新增独立的 opaque `bubbleInstanceToken`。网络字段统一命名为 `bubbleId`，其值就是现有 `BubbleSpec.id()`；控件字段统一命名为 `controlId`，其值就是控件在气泡内的 `id`。

这样点击含义直接明确为“玩家在什么气泡中按了什么按钮”。交互气泡应使用玩家范围内唯一的 `bubbleId`；未指定 ID 时继续使用现有 UUID 生成逻辑。

KubeJS 事件统一使用：

```javascript
event.player
event.bubble
event.control
```

其中 `event.bubble.id` 对应 `bubbleId`，`event.control.id` 对应 `controlId`。

## 2. 现有 mod 架构分析

### 2.1 代码组织

仓库没有共享的 common 源码目录，而是维护六套加载器/版本源码树。核心类在各分支中基本同构，但 GUI 事件、网络注册和 Minecraft API 调用存在加载器差异。

主要参考实现是：

- `forge-1.20.1/src/main/java/com/bubbleanyway/data/BubbleSpec.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/client/BubbleOverlay.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/data/BubbleThemeManager.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/client/BubbleThemeClientCache.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/network/BubblePayload.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/network/BubbleThemePayload.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/network/BubbleThemeSyncPayload.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/api/BubbleServerApi.java`
- `forge-1.20.1/src/main/java/com/bubbleanyway/kubejs/BubbleKubeJSServerApi.java`

### 2.2 当前气泡生命周期

当前流程大致如下：

1. Java API、命令或 KubeJS 生成文本/JSON；
2. 服务端通过 `BubbleSpec.fromJson` 解析和限制字段；
3. 无 theme 时发送完整 `BubblePayload`；
4. 有 theme 时，服务端校验 theme 并发送 `BubbleThemePayload`，内容是 theme ID 加本次 overrides JSON；
5. 客户端从本地主题、服务器主题缓存或按需请求中解析最终 `BubbleSpec`；
6. `BubbleOverlay` 负责排队、按优先级提升、计算布局、绘制和动画；
7. 气泡结束、被替换或收到 remove 请求后从活动列表移除。

当前 `BubbleOverlay` 已经有可复用的布局基础：

- 图标会占用文本左侧的一列空间；
- 最小高度会考虑文本高度和图标高度；
- 位置计算使用最终缩放后的气泡矩形；
- 同一 anchor 下会自动堆叠；
- 绘制顺序与优先级、进入顺序有关。

因此控件应加入 `BubbleLayout` 的统一计算流程，而不是额外创建一个独立的 Screen 控件树。

### 2.3 当前主题系统

当前主题系统支持：

- 数据包主题；
- 配置文件主题；
- 客户端资源主题；
- 服务端主题同步；
- 客户端主题缓存；
- 缺少主题时的按需请求；
- 调用时的顶层字段覆盖。

当前 `BubbleThemeManager.mergeJson` 是顶层合并：overrides 中出现的字段会覆盖 theme 中同名字段，嵌套对象不会自动深度合并。这一点对未来的 `controls` 很重要：不能直接依赖通用顶层合并来修改单个按钮，应该定义明确的 `controlOverrides` 规则。

当前主题定义可以在调用时减少重复样式传输，但还没有：

- 控件数据结构；
- 动态参数模板替换；
- 服务端/客户端点击事件；
- 服务端到客户端以外的点击 payload；
- 点击控件的生命周期记录。

## 3. 控件数据模型

建议在 `BubbleSpec` 中增加不可变控件列表：

```java
List<BubbleControl> controls()
```

建议的 JSON 形式：

```json
{
  "id": "quest_confirm_123",
  "theme": "my_mod:confirm_dialog",
  "text": "是否领取任务奖励？"
}
```

主题 `my_mod:confirm_dialog`：

```json
{
  "controls": {
    "layout": "HORIZONTAL",
    "gap": 6,
    "align": "CENTER",
    "items": [
      {
        "id": "confirm",
        "type": "BUTTON",
        "text": "确定",
        "closeOnPress": true
      },
      {
        "id": "cancel",
        "type": "BUTTON",
        "text": "取消",
        "closeOnPress": true
      }
    ]
  }
}
```

`BubbleControl` 建议包含：

- `id`：气泡内唯一标识；
- `type`：第一期为 `BUTTON`；
- `text`：显示文本；
- `enabled`：是否可点击；
- `width`、`height`：固定尺寸，`0` 表示按内容计算；
- `closeOnPress`：点击后是否请求移除当前气泡；
- `data`：控件关联数据，作为点击事件的一部分暴露给第三方 mod 和 KubeJS；Bubble Anyway 不解释其业务含义；
- 控件自身的颜色、背景、边框和悬停样式。

### 3.1 Builder API

现有 `BubbleSpec` 已经有较多字段和多个兼容构造器。控件功能不再继续增加超长构造函数，新增 Builder 作为推荐的 Java API；旧构造器和 JSON 解析保持兼容。

建议 API 形态：

```java
BubbleSpec spec = BubbleSpec.builder()
        .id("dialog_001")
        .text("是否接受这个任务？")
        .icon("minecraft:book")
        .controls(BubbleControls.builder()
                .layout(BubbleControls.Layout.HORIZONTAL)
                .gap(6)
                .align(BubbleControls.Align.CENTER)
                .add(BubbleControl.button("confirm", "确定"))
                .add(BubbleControl.button("cancel", "取消"))
                .build())
        .build();
```

按钮样式和事件信息可以继续链式配置：

```java
BubbleControl confirm = BubbleControl.builder("confirm")
        .type(BubbleControl.Type.BUTTON)
        .text("确定")
        .style("primary")
        .enabled(true)
        .closeOnPress(true)
        .build();
```

主题调用继续使用现有的 `showTheme`/`showThemeJson` API；Builder 用于构造完整的 `BubbleSpec` 和控件定义，不在 `BubbleSpec` 中增加一个未参与当前解析流程的 `theme` 字段。

Builder 的职责边界：

- `BubbleSpec.Builder` 负责气泡自身属性和控件容器；
- `BubbleControls.Builder` 负责布局方向、间距、对齐和控件顺序；
- `BubbleControl.Builder` 负责控件显示属性、样式引用和 `controlId`；
- Builder 的 `build()` 统一执行默认值、空值处理和范围校正；
- 控件 `id` 为空、重复或包含非法值时在构建阶段抛出明确异常；
- Builder 生成不可变对象，避免发送后被调用方修改；
- Builder 不负责执行动作，也不保存 Java/KubeJS 回调；
- Builder 不区分服务端按钮和客户端按钮，事件在哪一端产生由气泡来源决定。

如果保留 `BubbleSpec` 的静态简单工厂，可以提供：

```java
BubbleSpec.builder().text("...").build();
BubbleControl.button("ok", "确定");
```

但不建议为每个字段增加大量重载工厂方法。

控件容器建议包含：

- `layout`：`HORIZONTAL` 或 `VERTICAL`；
- `gap`：控件间距；
- `align`：`LEFT`、`CENTER` 或 `RIGHT`；
- `items`：控件数组。

### 3.2 控件主题

控件主题放在 bubble theme 中，控件实例通过 `style` 引用命名样式。建议支持四种客户端状态：

- `normal`：默认状态；
- `hover`：鼠标悬停状态；
- `pressed`：鼠标按下状态；
- `disabled`：不可点击状态。

每种状态可以配置：

- 文本颜色、字号、粗体、斜体、阴影等文本样式；
- 背景颜色；
- 背景材质；
- 九宫格边框和 guide；
- 内边距；
- 按下时的 X/Y 偏移；
- 点击或悬停声音。

示例：

```json
{
  "controls": {
    "styles": {
      "primary": {
        "normal": {
          "background": "my_mod:textures/gui/button.png",
          "backgroundBorder": 4,
          "textColor": "#FFFFFFFF",
          "bold": true
        },
        "hover": {
          "background": "my_mod:textures/gui/button_hover.png",
          "textColor": "#FFFFFF55"
        },
        "pressed": {
          "background": "my_mod:textures/gui/button_pressed.png",
          "offsetY": 1
        },
        "disabled": {
          "backgroundColor": "#FF555555",
          "textColor": "#FFAAAAAA"
        }
      }
    },
    "items": [
      {"id": "confirm", "type": "BUTTON", "text": "确定", "style": "primary"},
      {"id": "cancel", "type": "BUTTON", "text": "取消", "style": "primary"}
    ]
  }
}
```

样式解析优先级建议为：

```text
内置默认样式
< theme 控件默认样式
< 控件引用的命名样式
< 当前气泡的控件样式覆盖
```

按钮材质和气泡背景一样，不通过主题 JSON 自动上传，客户端必须能从模组、资源包或其他客户端资源中加载。

## 4. 布局规则

建议将气泡分为两个区域：

```text
+--------------------------------+
| icon | 文本区域                 |
|      | 多行文本                 |
|--------------------------------|
|          确定    取消           |
+--------------------------------+
```

### 4.1 Icon 与控件坐标

图标只参与上方 body 区域的布局，不直接改变按钮行的左侧起点。

建议计算方式：

```text
bodyHeight = max(textHeight, iconSize)
controlsY = padding + bodyHeight + controlGap
```

因此：

- icon 比文本高时，按钮整体向下移动，避免与 icon 重叠；
- icon 不会让按钮自动向右偏移；
- icon 可能改变气泡最终宽度，从而间接影响居中/右对齐时的屏幕位置；
- 按钮区域默认使用整个气泡内容宽度，而不是只使用文本列宽度。

### 4.2 多按钮

多个按钮应作为数组处理，默认水平排列：

- 依次计算每个按钮的宽度；
- 使用 `gap` 插入间隔；
- 总宽度不足时自动换行；
- 控件行按照 `align` 对齐；
- `VERTICAL` 时每个控件单独占一行；
- 控件区域高度参与最终气泡高度；
- 如果显式指定 `width`/`height`，必须使用稳定尺寸，避免动画期间命中区域变化。

### 4.3 命中测试

渲染和命中测试必须复用同一份 `RenderBubble`/布局结果，避免“看起来在按钮上，实际点不到”的偏差。

命中测试需要考虑：

- `scale`；
- anchor 和堆叠偏移；
- 当前屏幕尺寸；
- 滑入/滑出动画；
- 当前 alpha；
- `BELOW_PAUSE` 和 `ABOVE_PAUSE`；
- 重叠气泡的绘制顺序；
- disabled 控件不可点击但可以显示悬停样式的策略。

重叠时应从最上层气泡开始测试，命中第一个有效控件后停止传播。

## 5. 主题与动态参数

### 5.1 推荐的传输方式

稳定控件定义放在 theme 中，每次气泡只传：

- theme ID；
- 气泡 ID；
- 动态文本；
- 本次确实需要变化的字段；
- 必要的动作数据或服务端状态引用。

例如：

```json
{
  "theme": "my_mod:confirm_dialog",
  "id": "quest_confirm_123",
  "text": "是否领取 my_mod:first_quest 的奖励？"
}
```

### 5.2 控件覆盖

由于当前主题合并不是深度合并，建议使用显式的控件覆盖字段：

```json
{
  "theme": "my_mod:confirm_dialog",
  "text": "是否领取奖励？",
  "controlOverrides": {
    "confirm": {
      "text": "领取",
      "data": {
        "quest": "my_mod:first_quest"
      }
    }
  }
}
```

实现时应规定：

- 本期不由 Bubble Anyway 解释或注册 action；按钮点击事件只报告 `bubbleId` 和 `controlId`，并提供对应的 `bubble`、`control` 对象；
- 控件不存在时拒绝或记录警告，而不是静默创建未知控件；
- `controlOverrides` 只合并白名单字段；
- `data` 可以作为控件关联信息暴露给服务端/客户端事件，但本期不设计新的 token 存储层；
- 第三方 mod 自己决定收到点击事件后如何根据 `bubbleId`、`controlId` 和可选 data 执行业务逻辑。

当前 mod 支持调用时覆盖普通气泡字段，例如 `text`、`icon`、`priority` 等，但不支持 `${player.name}` 这类主题模板变量。控件计划不应把模板替换和控件动作混在一起，后续可以单独增加模板系统。

## 6. 网络传输分析

### 6.1 当前网络路径

#### 无 theme 的气泡

服务端解析 JSON 后，通过 `BubblePayload` 以二进制字段传输。它不是把原始完整 JSON 原样发送，但其中仍会发送：

- 文本；
- `textPartsJson`；
- 图标、背景、音效资源 ID；
- 位置、动画、颜色等固定字段。

当前结构的固定字段大约占一百多字节，实际大小主要由文本、`textPartsJson` 和资源 ID 决定。

#### 有 theme 的气泡

重复使用已缓存 theme 时，`BubbleThemePayload` 只发送：

- theme ID；
- overrides JSON。

如果只覆盖短文本，单次传输量会明显低于完整 `BubblePayload`。

客户端没有 theme 时，会发送 `BubbleThemeRequestPayload`，服务端再返回一次该 theme 定义。之后由客户端缓存。

#### 当前最大的传输风险

当前登录和主题重新加载流程会调用 `BubbleNetwork.syncThemes(...)`，而 `BubbleThemeSyncPayload.fromCurrentThemes()` 会构造当前所有主题的完整 JSON 映射，再发送给目标玩家。

因此，当前风险排序是：

| 场景 | 当前行为 | 风险 |
|---|---|---|
| 普通短文本 | 二进制气泡包 | 低 |
| 已缓存 theme + 短 overrides | theme ID + 少量 JSON | 很低 |
| 首次使用一个 theme | 一次性传输完整 theme | 通常低，取决于主题大小 |
| 玩家登录 | 发送全部服务器主题 | 中到高，取决于主题数量和大小 |
| `/reload` | 向在线玩家同步全部主题 | 中到高，且可能集中爆发 |
| `showAll` 大量玩家 | 每个玩家都收到一份 | 按玩家数线性放大 |
| 高频大文本/大 textParts | 每次事件都发送动态内容 | 可能造成持续带宽和解析压力 |

### 6.2 大 JSON 是否会造成网络拥堵

结论：**少量、低频的定制 JSON 通常不会造成明显拥堵；大量、高频、面向多人广播的大 JSON 会造成实际压力。**

需要区分三类成本：

1. **网络带宽**：每个目标玩家都会收到一份数据，广播会线性放大；
2. **序列化/解析**：服务端和客户端都需要创建字符串、解析 JSON、构造对象；
3. **主线程压力**：主题解析和客户端布局会进入游戏线程，过大的 JSON 或连续大量气泡可能造成短时间卡顿。

Minecraft 连接层可能对网络包进行压缩，但压缩不能消除 JSON 的解析、对象创建、广播复制和队列压力。压缩也不能解决一个主题定义过大或事件触发过于频繁的问题。

目前代码中的长度上限也值得注意：

- 普通包中的文本和 `textPartsJson` 使用 `32767` 字符限制；
- theme overrides 使用 `32767` 字符限制；
- 单个同步主题允许达到 `1_000_000` 字符；
- 同步主题数量允许达到 `4096`；
- 当前没有明显的“所有主题总大小”上限。

这些上限更像协议容纳上限，不代表应该鼓励使用如此大的数据。尤其是“4096 个主题乘以较大 JSON，并在登录时同步全部”的组合，需要优先调整。

## 7. 建议的优化顺序

### P0：取消登录时的全量主题定义同步

建议把主题同步改为“按需请求”为主：

1. 登录时不发送所有主题 JSON；
2. 客户端收到使用某主题的气泡后，如果本地没有该主题，再请求该主题；
3. 服务端只返回被请求的主题；
4. 客户端缓存已请求的主题；
5. `/reload` 的现有同步行为本期保持不变，不增加版本/哈希协议。

这样可以保留当前已有的按需请求机制，同时消除全量同步造成的峰值。

### P1：点击事件只传气泡 ID 和控件 ID

推荐动作协议：

- 客户端点击时只发送 `bubbleId + controlId`；
- 服务端事件根据当前玩家和这两个字段报告点击；
- Bubble Anyway 不解释、注册或执行第三方业务动作；
- 第三方 mod 可以在事件监听器中根据 ID 决定业务逻辑；
- 本期不引入额外的 opaque 实例令牌，`bubbleId` 直接使用现有 `BubbleSpec.id()`。

这样可以让点击协议保持简单，并减少点击包大小。服务端仍应确认该玩家当前收到过对应气泡、控件存在且可点击；这属于基本一致性校验，不引入新的动作权限系统。

### P2：保留 JSON API

JSON 仍然适合作为 Java/KubeJS 的公共输入格式，因为它易读、易写、兼容性好。网络层已经在无 theme 场景下把主要字段编码为二进制，因此第一阶段没有必要立即设计一套完全不同的公共 API。

建议顺序：

1. 公共 API 继续支持 JSON；
2. Java API 推荐使用 Builder；
3. theme 场景只传 theme ID 加动态 overrides；
4. 控件点击只传 `bubbleId + controlId`；
5. 暂不新增压缩或更紧凑的控件编码。

本期不新增应用层 JSON 压缩或新的紧凑二进制格式。JSON 继续作为 Java/KubeJS 的公共输入格式，网络包仍使用当前各加载器的协议方式。

### 暂缓：其他网络和性能优化

以下内容本期保留为后续选项，不修改现有行为：

- 主题版本号、哈希、manifest 和增量同步；
- 额外限流、批量合并和新的强制总大小限制；
- 应用层 JSON 压缩或全新紧凑二进制格式；
- 相同气泡的频率限制和事件去重；
- opaque `bubbleInstanceToken`、服务端动作注册表和防重放系统。

## 8. Java API 计划

### 8.1 控件构造

保留现有构造器和 JSON API，新增 builder 或工厂方法，避免继续增加超长构造函数：

```java
BubbleSpec spec = BubbleSpec.builder()
        .id("quest_confirm_123")
        .text("是否领取任务奖励？")
        .build();
```

如果使用完整定义：

```java
BubbleControl confirm = BubbleControl.button("confirm", "确定")
        .closeOnPress(true)
        .build();
```

### 8.2 Java 点击事件

建议提供公共事件类，供其他 mod 作为 library 使用：

```java
// 通过对应加载器的事件总线注册监听器。
eventBus.addListener(event -> {
    ServerPlayer player = event.player();
    if (event.bubble().id().equals("dialog_001")
            && event.control().id().equals("confirm")) {
        // 执行业务逻辑
    }
});
```

事件对象建议提供：

- `player()`：触发点击的服务端玩家；
- `bubble()`：本次点击对应的不可变气泡对象或公开视图；
- `control()`：本次点击对应的不可变控件对象或公开视图；
- `bubbleId()`：`bubble().id()` 的便捷访问；
- `controlId()`：`control().id()` 的便捷访问；
- `data()`/`dataJson()`：控件关联数据；
- `setCanceled(boolean)` 或等价取消机制：允许其他 mod 阻止 Bubble Anyway 的默认点击后行为，例如 `closeOnPress`。

Java 事件只负责报告点击，不负责根据 action 字符串自动调用第三方代码。其他 mod 可以使用 `bubbleId` 和 `controlId` 实现任务、对话框、确认框、奖励领取等业务。

建议公共 API 命名为 `BubbleServerClickEvent` 和 `BubbleClientClickEvent`，底层可以共享一个不依赖加载器的 `BubbleClickContext`，但具体事件注册方式服从各加载器事件总线。

## 9. KubeJS API 计划

服务端点击事件：

```javascript
BubbleEvents.serverClick(event => {
  if (event.bubble.id == 'dialog_001'
      && event.control.id == 'confirm') {
    event.player.tell('已确认');
  }
});
```

客户端点击事件：

```javascript
BubbleEvents.clientClick(event => {
  if (event.bubble.id == 'dialog_001'
      && event.control.id == 'cancel') {
    // 执行客户端逻辑
  }
});
```

事件字段规范：

- `event.player`：服务端事件提供服务端玩家；客户端事件提供客户端玩家对象；
- `event.bubble`：点击时的气泡公开视图，至少包含 `id` 和 `text`；
- `event.control`：点击时的控件公开视图，至少包含 `id`、`type`、`text` 和 `data`；
- `event.bubble.id` 与 `event.control.id` 是跨 Java/KubeJS 的稳定字段；
- 事件回调异常必须被捕获并记录，不能破坏网络处理线程或后续事件分发。

服务端 KubeJS 事件应由 `BubbleKubeJSServerApi` 或对应的 KubeJS 事件组注册；客户端事件应由 `BubbleKubeJSBindings` 或客户端事件组注册。现有的显示/清除方法和 `BubbleKubeJSServerBindings` 兼容转发继续保留。

## 10. 网络 payload 计划

新增客户端到服务端的 `BubbleClickPayload`，最小字段建议为：

```text
bubbleId
controlId
```

客户端不提交 action、业务处理结果或额外 data。服务端收到 `bubbleId + controlId` 后，根据当前玩家当前可见的气泡状态创建并发布 `BubbleServerClickEvent`。

为了确认点击对应当前玩家收到的气泡，需要增加轻量的玩家侧交互状态记录，记录当前可点击气泡 ID 和控件 ID。该状态不是 opaque token，也不改变网络字段；它只用于基本的一致性检查和清理。

交互状态在以下操作时同步更新或清理：

- 气泡过期；
- remove；
- replace；
- clear；
- 玩家退出；
- 世界切换；
- 点击后默认行为需要关闭且 `closeOnPress=true`。

服务端点击校验：

1. `bubbleId` 属于当前玩家当前可点击的气泡；
2. 气泡仍然有效；
3. 控件存在且 enabled；
4. 控件 ID 与气泡 ID 匹配；
5. 点击事件在正确的服务端/客户端方向分发。

## 11. 多加载器实施顺序

建议先在 `forge-1.20.1` 完成参考实现：

1. `BubbleControl`、控件容器和 JSON 解析；
2. `BubbleLayout` 扩展和命中测试；
3. Forge 鼠标事件适配；
4. Forge 双向网络和服务端点击事件；
5. Java/KubeJS API；
6. 主题和缓存优化；
7. 移植到 Forge 1.19.2；
8. 移植到 NeoForge 两个版本；
9. 移植到 Fabric 两个版本；
10. 六个分支分别构建、启动和执行交互测试。

共享逻辑应尽可能保持同名、同字段、同 JSON 行为。加载器差异集中在：

- 鼠标输入事件；
- 客户端到服务端 payload 注册；
- 服务端 payload 注册；
- GUI 绘制 API；
- mod 初始化和玩家生命周期事件。

## 12. 测试计划

### 布局测试

- 无 icon、短文本、单按钮；
- 有 icon 且 icon 高于文本；
- 两个按钮“确定/取消”；
- 三个及以上按钮自动换行；
- 水平、垂直和不同对齐方式；
- 固定尺寸和自动尺寸；
- 缩放、长文本和多行文本；
- TOP、CENTER、BOTTOM 各 anchor；
- 多气泡堆叠和重叠。

### 交互测试

- HUD 上点击；
- 背包、机器 GUI 和第三方 Screen 上点击；
- 暂停界面下方和上方两个 layer；
- 滑入/滑出期间点击；
- disabled 控件；
- 关闭后继续点击；
- 重复点击、未知 `bubbleId` 和未知 `controlId`；
- 玩家退出、换世界和 `/reload`。

### 传输测试

- 无 theme 的旧 JSON 仍然可用；
- 已缓存 theme 只发送少量 overrides；
- 首次 theme 请求只返回单个主题；
- `/reload` 不再广播无关主题；
- `showAll` 在不同在线人数下记录数据量；
- 六个版本分支均能离线构建。

## 13. 验收标准

实现完成后应满足：

- 旧版无控件气泡的 JSON、Java API、命令和 KubeJS 行为不变；
- theme 可以定义多个按钮；
- icon 不会导致按钮错误地从文本列起点开始偏移；
- 按钮区域正确参与自动高度计算；
- 控件支持 theme 样式以及 normal/hover/pressed/disabled 状态；
- 客户端点击只提交 `bubbleId + controlId`；
- Java 和 KubeJS 都能收到服务端/客户端点击事件；
- 事件对象提供统一的 `player`、`bubble`、`control` 命名；
- theme 重复使用时不重复发送完整定义；
- 玩家登录时不再无条件广播全部主题定义；
- `/reload` 的现有行为保持兼容；
- 六个支持分支通过构建和基础运行测试。

## 14. 本次结论

当前 mod 的普通短消息和已缓存 theme 消息不太可能单独造成网络拥堵。真正需要优先优化的是：

1. 登录时的全量主题同步；
2. 面向大量玩家的高频 `showAll`；
3. 大型主题和 overrides；
4. 连续生成大量气泡时的服务端 JSON 解析和客户端布局压力。

因此推荐的技术路线是：

> 主题保存稳定控件定义，气泡只发送动态字段；登录时不再全量同步主题，缺失主题时按需请求；点击只发送 `bubbleId + controlId`；服务端和客户端通过统一事件暴露 `player`、`bubble`、`control`；Java API 使用 Builder，同时保留现有 JSON 和旧 API 兼容。
