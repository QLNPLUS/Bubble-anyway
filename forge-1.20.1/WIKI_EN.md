# Bubble Anyway Wiki

This document describes Bubble Anyway 1.1.0 for all supported loader branches.
Bubble Anyway is an API-first information overlay for Minecraft. Other mods,
server events, commands, and KubeJS can create customizable bubbles above most
vanilla and modded GUI content.

## Supported Versions

| Loader | Minecraft |
|---|---|
| Forge | 1.19.2 |
| Forge | 1.20.1 |
| NeoForge | 1.21.1 |
| NeoForge | 1.26.1.2 |
| Fabric | 1.20.1 |
| Fabric | 1.21.1 |

Install the JAR that matches both the Minecraft version and the loader.

## Quick Start

### Command

```text
/bubble show @a {"id":"welcome","text":"Welcome to the server\nHave fun!","anchor":"CENTER_TOP","y":18,"animation":"SLIDE_FROM_TOP","duration":100}
```

Use a theme without repeating the style fields:

```text
/bubble show theme @a bubble_anyway:default Welcome to the server
```

Clear bubbles with:

```text
/bubble clear
/bubble clear <targets>
```

Commands require permission level 2.

### Client KubeJS

```javascript
const BubbleAnyway = Java.loadClass(
  'com.bubbleanyway.kubejs.BubbleKubeJSBindings'
);

BubbleAnyway.showJson(JSON.stringify({
  id: 'local_notice',
  theme: 'bubble_anyway:default',
  text: 'Client KubeJS is ready\nThis bubble is local only.',
  icon: 'minecraft:diamond'
}));
```

This is local to the client and does not require a server command.

### Server KubeJS

```javascript
const BubbleServer = Java.loadClass(
  'com.bubbleanyway.kubejs.BubbleKubeJSServerApi'
);

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'welcome',
    theme: 'bubble_anyway:default',
    text: 'Welcome back!'
  }));
});
```

Server-side calls can target one player, a collection of players, or everyone
on the server.

## JSON Fields

Every field can be used in a bubble JSON object and, where meaningful, in a
theme definition. A theme may be partial. Missing values use the mod defaults.

| Field | Type | Default | Values / notes |
|---|---|---:|---|
| `id` | string | random UUID | Bubble ID. With `replace: true`, an existing bubble with the same ID is replaced. |
| `theme` | string | none | `namespace:path`, or a path using the `bubble_anyway` namespace. |
| `text` | string | empty | Text to display. Supports `\n`, wrapping, and formatting codes. |
| `message` | string | none | Compatibility alias for `text`. `text` wins when both exist. |
| `icon` | string | empty | Item ID such as `minecraft:diamond`, or a texture path when using `TEXTURE`. |
| `item` | string | empty | Compatibility alias for `icon`. |
| `iconType` | string | `AUTO` | `AUTO`, `ITEM`, `TEXTURE`, or `NONE`. |
| `iconSize` | integer | `16` | 8 to 64 GUI pixels. |
| `iconGap` | integer | `6` | Gap between icon and text. |
| `iconOffsetX` | integer | `0` | Icon adjustment in GUI pixels. |
| `iconOffsetY` | integer | `0` | Icon adjustment in GUI pixels. |
| `textOffsetX` | integer | `0` | Text adjustment in GUI pixels. |
| `textOffsetY` | integer | `0` | Text adjustment in GUI pixels. |
| `textColor` | color | `#FFFFFFFF` | `#RRGGBB`, `#AARRGGBB`, or an integer. |
| `color` | color | none | Compatibility alias for `textColor`. |
| `backgroundColor` | color | `#E6111720` | Used when no PNG background is selected. |
| `background` | resource path | empty | For example `bubble_anyway:textures/gui/background.png`. Do not include `assets/`. |
| `backgroundBorder` | integer | `0` | Edge size for nine-slice rendering. 0 disables nine-slice mode. |
| `backgroundGuide` | integer | `0` | Number of guide pixels skipped between slices. Use 1 for the built-in 66x66 guide-line images. |
| `sound` | sound ID | button click | Empty string disables the sound. |
| `soundVolume` | float | `1.0` | 0.0 to 2.0. |
| `soundPitch` | float | `1.0` | 0.5 to 2.0. |
| `x` | integer | `0` | Offset from the selected anchor. |
| `y` | integer | `0` | Offset from the selected anchor. |
| `width` | integer | `0` | 0 enables automatic width. |
| `height` | integer | `0` | 0 enables automatic height. Text is never intentionally clipped. |
| `maxWidth` | integer | `320` | Maximum automatic wrapping width. |
| `padding` | integer | `10` | Inner padding in GUI pixels. |
| `textAlign` | string | `LEFT` | `LEFT`, `CENTER`, or `RIGHT`. |
| `align` | string | none | Compatibility alias for `textAlign`. |
| `duration` | integer | `100` | Lifetime in ticks. 20 ticks are approximately one second. |
| `fadeIn` | integer | `8` | Fade-in duration in ticks. 0 disables fade-in. |
| `fadeOut` | integer | `12` | Fade-out duration in ticks. 0 disables fade-out. |
| `slideIn` | integer | `8` | Slide-in duration for `SLIDE_FROM_*` animations. |
| `slideOut` | integer | `12` | Slide-out duration for `SLIDE_FROM_*` animations. 0 disables slide-out. |
| `priority` | integer | `0` | Higher values are rendered first in the placement order and take queue priority. |
| `fontSize` | float | `1.0` | Overall text and bubble scale, from 0.5 to 4.0. |
| `scale` | float | `1.0` | Compatibility alias for `fontSize`. |
| `bold` | boolean | `false` | Bold text. |
| `italic` | boolean | `false` | Italic text. |
| `underlined` | boolean | `false` | Underlined text. |
| `strikethrough` | boolean | `false` | Strikethrough text. |
| `obfuscated` | boolean | `false` | Obfuscated text. |
| `shadow` | boolean | `true` | Whether text shadow is drawn. |
| `replace` | boolean | `true` | Replace a bubble with the same ID. |
| `anchor` | string | `CENTER_TOP` | One of the anchor values below. |
| `animation` | string | `FADE` | `FADE` or one of the four slide values below. |
| `textParts` | array | none | Text segments with independent styles. |
| `textStyles` | object | none | Default styles keyed by a `textParts` role. |

### Anchors

```text
TOP_LEFT       CENTER_TOP       TOP_RIGHT
CENTER_LEFT    CENTER            CENTER_RIGHT
BOTTOM_LEFT    CENTER_BOTTOM    BOTTOM_RIGHT
```

`TOP_CENTER` is accepted as an alias for `CENTER_TOP`, and `BOTTOM_CENTER`
is accepted as an alias for `CENTER_BOTTOM`. Enum values are case-insensitive;
spaces and hyphens are normalized to underscores.

`x` and `y` are anchor-relative GUI-pixel offsets. The final placement keeps a
screen margin. During a slide animation, the bubble may temporarily be outside
the screen.

### Animations

```text
FADE
SLIDE_FROM_LEFT
SLIDE_FROM_RIGHT
SLIDE_FROM_TOP
SLIDE_FROM_BOTTOM
```

`fadeIn` and `fadeOut` control opacity only. `slideIn` and `slideOut` control
movement only and are ignored by `FADE`. For a fully opaque bubble that slides
in from outside the screen, use:

```json
{
  "animation": "SLIDE_FROM_TOP",
  "fadeIn": 0,
  "fadeOut": 0,
  "slideIn": 8,
  "slideOut": 0
}
```

## Text and Icons

Manual line breaks use `\n`; automatic wrapping uses `maxWidth`. Multi-part
text supports different styles for a title and subtitle:

```json
{
  "textParts": [
    {"role":"title","text":"Quest complete"},
    {"role":"subtitle","text":"\nReward received"}
  ],
  "textStyles": {
    "title": {"color":"#FFFF55","bold":true,"scale":1.1},
    "subtitle": {"color":"#FFFFFF","italic":true,"scale":0.9,"shadow":false}
  }
}
```

Item icons participate in automatic size calculation and follow the bubble's
opacity and slide animation. PNG icons are supported natively:

```json
{
  "text": "Special quest icon",
  "icon": "my_mod:textures/gui/quest_icon.png",
  "iconType": "TEXTURE",
  "iconSize": 24
}
```

`AUTO` first tries an item ID and then a PNG texture path. The texture must be
available in the client resource packs.

## Nine-Slice Backgrounds

```json
{
  "background": "bubble_anyway:textures/gui/background.png",
  "backgroundBorder": 8,
  "backgroundGuide": 1,
  "padding": 10
}
```

`backgroundBorder` keeps the four corners and edge strips at their source size
and stretches only the center regions. A 66x66 image with 1-pixel black guide
lines uses `backgroundBorder: 8` and `backgroundGuide: 1`. Guide pixels belong
to no slice and are excluded from both texture sampling and final rendering.

The built-in backgrounds are:

```text
bubble_anyway:textures/gui/background.png
bubble_anyway:textures/gui/background_modern.png
```

For a custom texture, place the file under:

```text
src/main/resources/assets/<namespace>/textures/gui/<file>.png
```

The JSON resource path omits `assets/`.

## Themes

Themes are partial JSON definitions. The effective value order is:

```text
BubbleSpec defaults < theme definition < fields in the current bubble JSON
```

### Configuration themes

Use `config/bubble_anyway/themes.json`:

```json
{
  "themes": {
    "my_mod:warning": {
      "textColor": "#FFFFFFFF",
      "backgroundColor": "#D9A83232",
      "padding": 10,
      "anchor": "CENTER_TOP",
      "y": 18,
      "animation": "SLIDE_FROM_TOP",
      "duration": 100,
      "priority": 200,
      "shadow": false
    }
  }
}
```

The `themes` wrapper is recommended, but a direct theme-ID-to-definition map
is also accepted. A complete theme lets the server send only a theme ID and
text:

```json
{
  "id": "quest_done",
  "theme": "my_mod:warning",
  "text": "Quest complete!"
}
```

### Data-pack and resource-pack themes

Server data-pack themes use:

```text
data/<namespace>/bubble_anyway/themes/<theme_path>.json
```

Client resource themes use:

```text
assets/<namespace>/bubble_anyway/themes/<theme_path>.json
```

On supported server configurations, `/reload` reloads data-pack and server
configuration themes and synchronizes the current theme directory. Existing
bubbles keep their resolved style; new bubbles use the updated theme.

The client lookup order is local `config/bubble_anyway/themes.json`, local
resource themes, then an on-demand server theme response. Local themes take
priority over a server fallback with the same ID. PNG and sound resources are
not uploaded by a theme JSON; they must already exist on the client.

## Java API

Other mods can trigger bubbles directly from their own event handlers:

```java
import com.bubbleanyway.api.BubbleServerApi;

BubbleServerApi.showJson(player,
    "{\"id\":\"quest_complete\",\"text\":\"Quest complete!\"}");

BubbleServerApi.showTheme(player, "my_mod:warning", "Quest complete!");
```

The server API includes:

```text
show(ServerPlayer, BubbleSpec)
show(Collection<? extends ServerPlayer>, BubbleSpec)
showJson(ServerPlayer, String)
showJson(Collection<? extends ServerPlayer>, String)
showAll(MinecraftServer, BubbleSpec)
showAllJson(MinecraftServer, String)
showTheme(ServerPlayer, String, String)
showTheme(Collection<? extends ServerPlayer>, String, String)
showThemeJson(ServerPlayer, String, String)
showThemeJson(Collection<? extends ServerPlayer>, String, String)
showAllTheme(MinecraftServer, String, String)
showAllThemeJson(MinecraftServer, String, String)
clear(ServerPlayer)
clear(Collection<? extends ServerPlayer>)
clearAll(MinecraftServer)
```

## KubeJS API

Server-side class:

```text
com.bubbleanyway.kubejs.BubbleKubeJSServerApi
```

Client-side class:

```text
com.bubbleanyway.kubejs.BubbleKubeJSBindings
```

Both expose text, JSON, theme, theme override, and clear operations. The old
`BubbleKubeJSServerBindings` name remains as a compatibility forwarding class
where it is available.

## Toast Integration

The optional Toast integration supports:

- vanilla advancement frames: Task, Goal, and Challenge;
- vanilla recipe-unlock Toasts;
- FTB Quests completion and reward Toasts;
- item icons and supported PNG icons from third-party Toasts.

The client TOML file is:

```text
config/bubble_anyway/client.toml
```

Example:

```toml
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
```

`fallback = "ORIGINAL"` keeps an unrecognized Toast. `fallback = "IGNORE"`
hides an unrecognized supported-type Toast. Successfully converted Toasts
enter the same spatial queue as normal bubbles, so they are not constrained by
the vanilla Toast slot count.

## Queue, Layering, Pause, and Exit

There is no rigid global maximum bubble count. The renderer calculates the
final rectangle for each candidate. If the current anchor area cannot fit the
candidate, it stays pending until an active bubble expires. Queue admission is
priority-aware and preserves entry order among equal priorities.

Each bubble commits its background before its icon and text, and later bubbles
are rendered as later layers. This prevents text or icons from remaining on top
of a background that should cover them.

During pause, active bubble timers and pending promotion stop. Leaving a world
clears both active and pending bubbles, so notifications do not continue after
the world has been closed.

NeoForge 1.21.1 renders after the vanilla `SAVING_INDICATOR` HUD layer, which
is after chat and the hotbar. Other loaders use their corresponding high-
priority HUD/screen hooks. A mod that replaces the framebuffer or deliberately
draws after the final overlay can still cover Bubble Anyway.

## Diagnostics

Diagnostics are disabled by default. Enable them only when investigating a
loader, resource, Toast, or rendering issue:

```toml
[debug]
diagnosticsEnabled = true
showTestBubble = true
```

`diagnosticsEnabled` logs theme validation, render callback activity, Toast
conversion, item/texture icon conversion, active count, and pending count.
`showTestBubble` adds a diamond-icon test bubble and requires diagnostics to be
enabled. After testing, set both values back to `false`.

The repository includes an automated launcher:

```powershell
.\tools\run-bubble-diagnostics.ps1 -TimeoutSeconds 150
```

It temporarily enables diagnostics in the configured local instances, starts
each client, waits for a world report, captures logs, and restores the original
configuration. Forge 1.19.2 has no supported Quick Play argument in the test
launcher, so it receives a startup-only check.

## Example Files

- [Forge 1.20.1 README](README.md)
- [Configuration themes](examples/config/bubble_anyway/themes.json)
- [Data-pack theme](examples/datapack/data/my_mod/bubble_anyway/themes/quest_notice.json)
- [Resource-pack theme](examples/resourcepack/assets/my_mod/bubble_anyway/themes/quest_notice.json)
- [Server KubeJS example](examples/kubejs/server_scripts/bubble_anyway_example.js)
- [Client KubeJS example](examples/kubejs/client_scripts/bubble_anyway_client_example.js)
- [New-features client test](examples/kubejs/client_scripts/bubble_anyway_new_features.js)
- [New-features server test](examples/kubejs/server_scripts/bubble_anyway_new_features_server.js)
- [Toast configuration](examples/config/bubble_anyway/client.toml)
- [中文 Wiki](WIKI.md)

## Compatibility Note

Bubble Anyway is designed to render above normal HUD and GUI content. A mod
that draws after the overlay pass, replaces the screen framebuffer, or uses a
custom rendering pipeline can still draw over it and may require a dedicated
compatibility integration.

## License

All Rights Reserved. See the repository license for usage permissions.
