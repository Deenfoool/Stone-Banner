# Stone & Banner

Stone & Banner is a work-in-progress total-conversion mod for Minecraft Java Edition 1.20.1 on Forge. It is designed around a third-person action-RPG camera, selectable WASD or mouse movement, real-time combat, settlement management, and terrain-driven resources.

The product direction and development stages are documented in [ROADMAP.md](ROADMAP.md).

## Development requirements

- 64-bit Java 17
- Minecraft 1.20.1
- Forge 47.4.23

## Useful commands

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## Current prototype

- Forge project foundation and dedicated client configuration.
- Action, tactical, and hybrid control modes.
- In-game mode cycling with `V` (rebindable in Minecraft controls).
- Third-person perspective enforcement.
- Smooth mouse-wheel camera zoom from 2 to 24 blocks.
- Configurable camera height and smoothing.
- Camera collision uses Minecraft's block-aware zoom check.
- Independent camera yaw and pitch while holding the middle mouse button.
- WASD movement is transformed relative to the camera direction.
- Tactical mode opens a free world cursor without pausing the game.
- Tactical raycasts select blocks under the real cursor instead of the central crosshair.
- Left click issues a direct movement command; right click stops it.
- The tactical overlay shows the hovered block and current movement state.
- Tactical movement uses a bounded A* route over walkable blocks.
- Routes can climb and descend one block, avoid basic hazards, and rebuild when the player is stuck.
- Route nodes and the final destination are rendered directly in the world.
- The tactical cursor resolves entities in front of blocks and shows their names.
- Hovered and selected entities receive different world-space outlines.
- Clicking a hostile mob issues an approach-and-attack command.
- Clicking a villager approaches and interacts; other entities are safely selected.
- Navigation uses collision-surface heights for bottom slabs and stairs.
- Water nodes support swimming up and down, with a higher route cost than dry ground.
- Closed wooden doors can be planned through and are opened on approach.
- World-cursor projection uses the camera's updated orientation vectors and rendered FOV.
- A small world-space marker shows the exact ray impact point before clicking.
- Camera settings persist in `config/stonebanner-client.toml`.

The pathfinder now has prototype handling for stairs, bottom slabs, water and wooden doors. Complex
waterlogged shapes, iron doors, trapdoors and ladders still need dedicated handling. Combat currently
delegates one contextual attack to vanilla Minecraft; the custom real-time combat system and settlement
simulation are later roadmap stages.
