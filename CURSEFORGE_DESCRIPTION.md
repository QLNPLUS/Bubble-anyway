# Bubble Anyway

Bubble Anyway is a universal information notification overlay for Minecraft.
It lets commands, server events, KubeJS, and client scripts display clear,
customizable message bubbles above most vanilla and modded GUI screens.

## Features

- Display multiple bubbles at the same time, with priority, replacement, IDs,
  and per-player clearing.
- Trigger bubbles from commands, server-side APIs, server events, KubeJS, or
  client-side KubeJS scripts.
- Render over most HUD and GUI screens through a high-priority overlay layer.
- Automatically size bubbles from their text, line breaks, wrapping, and item
  icons. Fixed minimum width and height are also supported.
- Use nine anchors: `TOP_LEFT`, `CENTER_TOP`, `TOP_RIGHT`, `CENTER_LEFT`,
  `CENTER`, `CENTER_RIGHT`, `BOTTOM_LEFT`, `CENTER_BOTTOM`, and `BOTTOM_RIGHT`.
- Use `FADE` or slide-in animations from the left, right, top, or bottom edge
  of the screen.
- Customize text color, size, alignment, bold, italic, underline, strikethrough,
  obfuscated text, shadow, wrapping, and line breaks.
- Add a Minecraft item icon with configurable size, gap, and independent X/Y
  offsets for both the icon and text.
- Use custom background colors or PNG textures with nine-slice scaling.
- Play a configurable sound when a bubble appears. The default is the vanilla
  button click sound.
- Define reusable themes so a server can send a theme ID and text instead of
  repeating every visual setting in each message.
- Include two built-in nine-slice backgrounds:
  `bubble_anyway:textures/gui/background.png` and
  `bubble_anyway:textures/gui/background_modern.png`.

## Supported Versions

| Loader | Minecraft |
| --- | --- |
| Forge | 1.19.2 |
| Forge | 1.20.1 |
| NeoForge | 1.21.1 |
| NeoForge | 1.26.1.2 |
| Fabric | 1.20.1 |
| Fabric | 1.21.1 |

All included targets are version `1.0.0`. Install the file matching both your
Minecraft version and mod loader.

## Command Example

```text
/bubble show @a {"theme":"bubble_anyway:defualt","text":"Welcome to the server\nHave fun!"}
```

The legacy JSON form is also supported:

```text
/bubble show @a {"text":"Server restart in 5 minutes","anchor":"CENTER_TOP","y":18,"animation":"SLIDE_FROM_TOP","duration":100,"priority":200}
```

Clear bubbles with:

```text
/bubble clear
```

## Client KubeJS

```javascript
const BubbleAnyway = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSBindings');

BubbleAnyway.showJson(JSON.stringify({
  id: 'local_notice',
  theme: 'bubble_anyway:defualt',
  text: 'Client KubeJS is ready\nThis bubble is local only.'
}));
```

Client KubeJS bubbles do not require a server command or server event.

## Server KubeJS

```javascript
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'welcome',
    theme: 'bubble_anyway:defualt',
    text: 'Welcome back!'
  }));
});
```

Server-side calls send the notification to the selected player, a collection
of players, or everyone on the server.

## JSON Example

```json
{
  "id": "quest_complete",
  "theme": "bubble_anyway:morden",
  "text": "Quest complete!\nYou received a diamond.",
  "icon": "minecraft:diamond",
  "iconSize": 16,
  "iconGap": 6,
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

When `theme` is present, unspecified fields are loaded from the local theme.
Explicit JSON fields override the theme for that bubble.

## Themes

Default themes are copied to `config/bubble_anyway/themes.json` on first
startup. Existing user configuration is preserved. A theme can contain any
supported bubble field, so a complete theme only needs a theme ID and text at
call time.

Forge targets also support server data-pack themes and server-side theme
synchronization. Other loader targets use the packaged/client configuration
theme system.

Example configuration:

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
      "fadeIn": 8,
      "fadeOut": 12,
      "duration": 100,
      "priority": 200,
      "shadow": false
    }
  }
}
```

Use `/reload` on supported server configurations to reload theme data. The
client also reads local theme configuration without requiring the full theme
definition in every bubble packet.

## Nine-Slice Backgrounds

Set `background` to a texture resource path and set `backgroundBorder` to the
edge size in pixels:

```json
{
  "background": "bubble_anyway:textures/gui/background.png",
  "backgroundBorder": 8,
  "backgroundGuide": 1,
  "padding": 10
}
```

`backgroundBorder` keeps the four corners and four edge strips at their native
size while stretching only the center regions. `backgroundGuide` excludes
reference-line pixels from every slice and from the final rendering. This is
useful for a 66x66 source image containing 1-pixel guide lines between the
usable nine-slice regions.

Custom textures can be supplied by a resource pack or another mod. The PNG
resource must be available on the client.

## Important Compatibility Note

Bubble Anyway is designed to render above normal HUD and GUI content. A mod
that draws directly after the overlay pass, replaces the screen framebuffer,
or uses a custom rendering pipeline can still draw over it. Such screens may
require loader- or mod-specific integration.

## License

All Rights Reserved. See the repository license for usage permissions.
