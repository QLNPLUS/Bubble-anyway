## 1.3.0

- Fixed recipe unlock Toasts so their title and description use the selected Minecraft language.
- Updated all supported loader builds to version 1.3.0.

## 1.2.0

- Synchronized pause-screen render layers across the multi-loader builds.
- Added explicit below-pause and above-pause rendering for ordinary bubbles and converted Toasts.
- Updated network payload compatibility and built-in theme defaults.
- Expanded diagnostics for theme loading, Toast interception, icon conversion, and pause behavior.

## 1.1.0

- Added configurable pause-screen render layers. Ordinary bubbles stay below the pause screen; converted Toast bubbles stay above it.
- Added optional diagnostics and automated startup/world smoke-test support.
- Fixed rapid multi-bubble admission and layered rendering behavior.
- Added advancement, recipe, and FTB Quests toast conversion support.

## 1.0.2

- Fixed textured bubble backgrounds so PNG and nine-slice materials fade in and out with the text.

## 1.0.1

- Fixed the built-in theme IDs to `bubble_anyway:default` and `bubble_anyway:modern`.

## 1.0.0

- Added Forge 1.20.1 support with a high-priority GUI overlay layer.
- Added command, Java server API, server KubeJS API, and client KubeJS API triggers.
- Added anchors, outside-to-inside slide animations, fade-in/fade-out, priorities, replacement, and clearing.
- Added automatic sizing, wrapping, text alignment, formatting, item icons, sounds, and nine-slice PNG backgrounds with guide pixels.
- Added independent X/Y offsets for icons and text, plus `textColor` JSON override support.
- Added client showcase and server-event examples.
- Added reusable partial themes from datapacks and config/bubble_anyway/themes.json, reloaded by `/reload`.
- Added local-first theme resolution, on-demand server fallback, theme-based Java/KubeJS APIs, and `/bubble show theme`.
- Bundled `background.png`, `background_modern.png`, and default theme presets.
