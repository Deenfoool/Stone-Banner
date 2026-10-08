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
