## 1.5.0

- Added clickable bubble controls with multiple buttons, layouts, hover/pressed states, and textured nine-slice button backgrounds.
- Added Java and KubeJS click events exposing the player, bubble ID, control ID, and custom control data.
- Added Builder APIs for bubbles and controls, compact control JSON, and theme-based control definitions and overrides.
- Controls only capture mouse input while a GUI screen owns the cursor; normal game camera movement is unaffected.
- Themes are requested on demand instead of fully synchronized at login.

## 1.4.0

- Added permanent bubbles with `duration: -1` and same-ID removal requests that play the original exit animation.
- Added configurable `lineSpacing` for multi-line bubble text and automatic height calculation.
- Synchronized the new JSON and network payload fields across the supported loaders.

## 1.3.0

- Fixed recipe unlock Toasts so their title and description use the selected Minecraft language.
- Updated all supported loader builds to version 1.3.0.

## 1.2.0

- Synchronized pause-screen render layers across the multi-loader builds.
- Added explicit below-pause and above-pause rendering for ordinary bubbles and converted Toasts.
- Updated network payload compatibility and built-in theme defaults.
- Expanded diagnostics for theme loading, Toast interception, icon conversion, and pause behavior.

## 1.1.0

- Added optional diagnostics and automated startup/world smoke-test support.
- Fixed rapid multi-bubble admission and layered rendering behavior.
- Added Fabric advancement and recipe toast interception with item icons.

## 1.0.2

- Fixed textured bubble backgrounds so PNG and nine-slice materials fade in and out with the text.

## 1.0.1

- Fixed the built-in theme IDs to `bubble_anyway:default` and `bubble_anyway:modern`.

## 1.0.0

- Added Fabric 1.21.1 support with HUD and screen-render overlay hooks.
- Added command, Java server API, server KubeJS API, and client KubeJS API triggers.
- Added anchors, outside-to-inside slide animations, fade-in/fade-out, priorities, replacement, and clearing.
- Added automatic sizing, wrapping, text alignment, formatting, item icons, sounds, and nine-slice PNG backgrounds with guide pixels.
- Added independent X/Y offsets for icons and text, plus `textColor` JSON override support.
- Added client showcase and server-event examples.
- Bundled `background.png`, `background_modern.png`, and default theme presets.
