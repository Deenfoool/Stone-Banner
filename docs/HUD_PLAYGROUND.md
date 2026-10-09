# HUD Playground (Browser)

This folder is a static GitHub Pages site. It mirrors the alpha.49 Forge HUD layout while running a standalone sandbox with mock NPCs, terrain, movement, commands, time controls, camera, work overlays, construction, inspector, hotbar, and settings.

**This is not the actual Minecraft HUD running in a browser.** It does not connect to Forge or the server; resources, NPC jobs, path movement, and building effects are simulated solely for UX demonstration.

HUD source references:
- src/main/java/dev/stonebanner/client/hud/StoneBannerHudLayout.java
- src/main/java/dev/stonebanner/client/hud/StoneBannerHudRenderer.java
- src/main/java/dev/stonebanner/client/screen/TacticalControlScreen.java
- CONTROLS.md

## Publish
Open repository Settings > Pages > Build and deployment > Deploy from a branch > main /docs > Save. No GitHub Actions.

Once Pages is enabled, URL: https://deenfoool.github.io/Stone-Banner/

## Controls
- Tab: switch Hero / Orders; V: switch WASD / Mouse.
- Orders: LMB select NPC, Shift+LMB multiselect, left-drag select region, RMB move, Alt+RMB queue movement, Space stop.
- Ctrl+1-9 save group, Alt+1-9 recall group, 1-9 hotbar.
- Hero/WASD: WASD move. Hero/Mouse: LMB ground to move.
- Wheel: zoom, middle mouse drag: pan, Home: center camera on hero.
- B: build tools, K: crafting tab, F6/F7/F8: overlays; Esc: close dialog or tool.
- All bottom tabs, inspector sub-tabs, settings, minimap, speed and work categories are clickable.

This prototype is intentionally dependency-free and does not modify any Forge game code.

## Icons and terrain art

The 2026-10-08 visual update adds the self-hosted SVG icon library at `docs/assets/hud-icons.svg` and the 32px pixel terrain, buildings, tree and citizen atlas at `docs/assets/terrain-atlas.svg`. `docs/assets/hud-art.js` hydrates icon symbols and paints tiles over the previous placeholder shapes, keeping the existing click targets and actions intact. No internet asset calls or Mojang textures are required; sprites have an automatic primitive fallback if unavailable.

## Compact Forge HUD parity (October 9, 2026)

`docs/assets/hud-layout.js` mirrors the compact `StoneBannerHudLayout.java` geometry, including its narrow-screen corner placement and short-rail minimap. The browser applies it on each HUD refresh and viewport resize. Styles reside in `docs/assets/hud-compact.css`, loaded after legacy site styles. The inspector auto-collapses when it cannot fit.

Run `node docs/tests/hud-layout.test.cjs` to catch layout constant drift and geometry regressions. Browser data, NPC actions, resources, world simulation remain illustrative and are not Minecraft/Forge runtime; Java HUD and saved worlds remain untouched.
