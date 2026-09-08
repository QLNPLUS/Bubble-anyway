# Bubble Anyway

Universal high-priority information bubbles for Minecraft commands, server
events, and KubeJS. The project contains loader-specific source trees for the
supported Minecraft versions.

## Included Versions

- `forge-1.19.2`
- `forge-1.20.1`
- `neoforge-1.21.1`
- `neoforge-1.26.1.2`
- `fabric-1.20.1`
- `fabric-1.21.1`

Each version includes the built-in nine-slice backgrounds and default theme
configuration. Themes are copied to the instance config directory on first
startup and existing user files are preserved.

## Build

Run the following from the desired version directory:

```powershell
.\gradlew.bat build --offline
```

The NeoForge 1.26.1.2 development setup may require the existing offline
artifact fallback when Minecraft artifacts are not available from the network.

## License

All Rights Reserved. See [LICENSE](LICENSE).
