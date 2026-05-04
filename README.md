# Greenhouse Gardener

Greenhouse Gardener is a MineColonies add-on for colonies that need to grow crops with biome-specific requirements. The mod adds a custom Greenhouse building whose worker maintains artificial biome zones inside the building footprint. Those zones let colony farms satisfy crop biome checks without requiring the colony to be physically built in every needed natural biome.

The current project skeleton was adapted from Warehouse Workshop. It already contains the NeoForge/MineColonies project setup, a `greenhousegardener` mod id, an early Greenhouse hut block registration, and a placeholder building module/window. Several resources and class names still use Warehouse Workshop terminology and should be renamed or replaced as the implementation moves from prototype to feature work.

## Intended Gameplay

The player builds a MineColonies Greenhouse. The building exposes a module for selecting which crop fields inside the building should be assigned to which biome, and which tagged materials the worker may consume to maintain those biome changes.

The Greenhouse worker pulls approved materials from the colony warehouse. Those materials represent the environmental systems required to alter and sustain the local growing conditions, such as heat, humidity, soil additives, cooling, or magical equivalents provided by datapacks and modpacks.

When a greenhouse biome is applied, the building records the natural biome for each affected position before changing it. If the Greenhouse is deconstructed, downgraded, or loses responsibility for an area, the stored natural biome state must be restored.

## Building Levels

The planned greenhouse capacity is:

| Level | Fields | Supported Biomes |
| --- | ---: | ---: |
| 1 | 1 | 1 |
| 2 | 2 | 1 |
| 3 | 3 | 2 |
| 4 | 4 | 3 |
| 5 | 4 | 4 |

The first implementation target should be level 1: one managed field and one selected biome. Higher levels should extend the same data model instead of introducing separate code paths.

## Current Skeleton

Important files already present:

| Area | Current file | Notes |
| --- | --- | --- |
| Mod entry point | `src/main/java/com/deathfrog/greenhousegardener/GreenhouseGardenerMod.java` | Registers the Greenhouse hut block and injects building modules on load completion. |
| Hut block | `src/main/java/com/deathfrog/greenhousegardener/core/blocks/huts/BlockHutGreenhouse.java` | Greenhouse hut block shell. |
| Building registry holder | `src/main/java/com/deathfrog/greenhousegardener/core/colony/buildings/ModBuildings.java` | Declares `GREENHOUSE_ID`, but the `greenhouse` `BuildingEntry` still needs real registration/wiring. |
| Module producer | `src/main/java/com/deathfrog/greenhousegardener/core/colony/buildings/modules/BuildingModules.java` | Registers a `biome_settings` module producer, currently backed by `WorkshopModule`. |
| Building module | `src/main/java/com/deathfrog/greenhousegardener/core/colony/buildings/modules/WorkshopModule.java` | Placeholder copied from Warehouse Workshop. This should become a persistent greenhouse biome module. |
| Module view | `src/main/java/com/deathfrog/greenhousegardener/api/colony/buildings/moduleviews/GreenhouseBiomeModuleView.java` | Client view shell for the biome module. |
| Module window | `src/main/java/com/deathfrog/greenhousegardener/core/client/gui/modules/WindowBiomeModule.java` | Opens the layout for the biome UI. |
| UI layout | `src/main/resources/assets/warehouseworkshop/gui/layouthuts/layoutbiomemodule.xml` | Namespace is still `warehouseworkshop`; the window currently looks for this under `greenhousegardener`, so this resource path needs to move. |
| Mod metadata | `src/main/resources/META-INF/neoforge.mods.toml` | Description still describes Warehouse Workshop crafting and should be updated. |

Adjacent `../warehouseworkshop` source remains useful as a reference for:

- NeoForge custom payload registration.
- Serverbound settings messages and clientbound settings refreshes.
- MineColonies building module injection.
- BlockUI module window patterns.
- Warehouse inventory interaction patterns.

Those pieces should be adapted carefully. Greenhouse state is building-owned colony state, not per-player crafting settings.

## Proposed Architecture

### Greenhouse Building

Create a real MineColonies building type for the greenhouse instead of injecting the biome module into the existing warehouse. The building should own:

- The greenhouse level.
- Managed field slots derived from the building level.
- Assigned biome per field slot.
- Allowed material tags per biome or greenhouse slot.
- Current work orders/tasks for the worker.
- Stored natural biome snapshots for every modified position.

The Greenhouse hut block should resolve to the Greenhouse `BuildingEntry`, and the building entry should include the biome settings module by default.

### Biome State Storage

The core persistent model should live on the building module or building class. A suggested model:

- `GreenhouseBiomeModule`
  - `Map<Integer, FieldBiomeAssignment>` keyed by field index.
  - `Map<BlockPos, ResourceKey<Biome>> naturalBiomes`.
  - `Map<BlockPos, ResourceKey<Biome>> appliedBiomes`.
  - `Set<TagKey<Item>> allowedPowerTags`.
  - Dirty/reconciliation markers for positions that need apply/restore work.

Store biome ids as resource locations in NBT. Avoid storing registry integer ids, because they are not stable across worlds or mod lists.

Before changing any position, capture its current biome if it is not already stored. On restoration, use the stored natural biome and then remove the snapshot entry only after the world has been successfully restored.

### Biome Application Service

Add a server-side service responsible for all biome mutation:

- Resolve the building's current footprint and field zones.
- Calculate which positions belong to each field.
- Capture original biomes for new positions.
- Apply requested biomes in bounded batches.
- Restore positions that are no longer managed.
- Mark affected chunks for save/sync as required by the Minecraft/NeoForge biome storage APIs.

This service should be isolated behind a small interface so the worker AI, building lifecycle hooks, and tests do not each learn the low-level biome mutation details.

### Worker and Materials

Add a Greenhouse worker job that periodically:

- Reads target biome assignments from the building.
- Finds acceptable power materials in the warehouse using the selected item tags.
- Requests missing materials through MineColonies request systems when warehouse stock is insufficient.
- Consumes materials at a configured rate per field, per biome transition, or per maintenance interval.
- Calls the biome application service when materials are available.

The first version can consume one configured tagged item per biome application cycle. Later versions can move to richer recipes or datapack-defined costs.

### Player UI

The building module UI should support:

- Selecting the target biome for each unlocked field slot.
- Selecting allowed power material tags.
- Showing whether each field is active, pending materials, applying, or restored.
- Showing current worker/material status.

For level 1, keep the UI intentionally small: one field selector, one biome selector, and one material tag selector. The higher-level UI can reuse the same field-row component.

### Lifecycle Restoration

Restoration should happen whenever greenhouse control ends:

- Building deconstruction.
- Building downgrade that reduces field/biome capacity.
- Field assignment removal.
- Biome assignment change.
- Failed validation where a previously managed area is no longer part of the current building footprint.

Restoration should be idempotent. If a position has no stored natural biome, the code should skip it rather than guessing.

## Implementation Path

### Phase 1: Clean Skeleton and Register the Building

- Rename Warehouse Workshop leftovers:
  - `WorkshopModule` to `GreenhouseBiomeModule`.
  - `WorkshopPlayerSettings` to a greenhouse-specific settings/state class or remove it if not needed.
  - `WarehouseWorkshopModClient` to `GreenhouseGardenerModClient`.
  - `assets/warehouseworkshop/...` to `assets/greenhousegardener/...`.
- Update `neoforge.mods.toml` description and any translation keys.
- Register the Greenhouse building entry and ensure `BlockHutGreenhouse#getBuildingEntry()` returns a bound entry.
- Attach `biome_settings` to the Greenhouse building rather than the warehouse.
- Add a minimal hut UI module that opens without missing resources.

Exit criteria: the mod loads, the greenhouse hut block is registered, the building entry resolves, and the biome module UI opens.

### Phase 2: Persistent Greenhouse Biome Module

- Replace the placeholder module with persistent building-owned data.
- Serialize/deserialize field assignments, selected biome ids, selected power tags, natural biome snapshots, and applied biome records.
- Serialize compact view data for the client.
- Add serverbound/clientbound settings messages based on the Warehouse Workshop networking pattern.

Exit criteria: a selected biome/material tag survives world reload and is visible when reopening the building UI.

### Phase 3: Level 1 Biome Application

- Define how the level 1 field zone is discovered from the Greenhouse structure.
- Implement the biome application service for one field and one biome.
- Capture natural biome snapshots before mutation.
- Apply the selected biome in safe batches.
- Add a manual/admin or UI-triggered apply path before worker automation, so the biome mutation behavior can be tested directly.

Exit criteria: one field inside a level 1 Greenhouse changes to the selected biome and restores correctly when the assignment is cleared or the building is removed.

### Phase 4: Worker Job and Warehouse Materials

- Add the Greenhouse worker/job.
- Use MineColonies warehouse lookup/request patterns to locate items matching selected tags.
- Consume materials when applying or maintaining a biome.
- Report missing materials through the building UI and/or MineColonies request system.

Exit criteria: the worker can maintain the level 1 greenhouse biome using warehouse materials selected by tag.

### Phase 5: Levels 2-5

- Generalize field zone detection to multiple field slots.
- Enforce the level capacity table.
- Add UI rows for unlocked fields.
- Support multiple target biomes at levels 3-5.
- Restore no-longer-unlocked fields on downgrade.

Exit criteria: every building level supports the planned field/biome count and downgrade/deconstruction leaves the world in its original biome state.

### Phase 6: Datapack and Modpack Support

- Move material cost definitions to data files.
- Support biome groups or tags where useful.
- Add config options for material consumption rate, batch size, and whether maintenance is one-time or recurring.
- Document datapack examples.

Exit criteria: pack makers can define which materials power which biome changes without Java changes.

## Open Design Questions

- Should biome power materials be selected globally per building, per field, or per target biome?
- Should applying a biome be a one-time construction cost, recurring maintenance, or both?
- Should the greenhouse change only biome data, or should it also optionally place supporting blocks such as glass, heaters, humidifiers, water, fans, or soil amendments?
- How should field zones be discovered: explicit structure markers, MineColonies field registrations, fixed offsets in the schematic, or player-selected positions?
- Which crop mods are the first compatibility targets, and how do they check biome requirements?

## Development Notes

This project targets Minecraft `1.21.1`, NeoForge `21.1.222`, and MineColonies snapshot `1.1.1305` based on the current Gradle files. Local MineColonies runtime dependency resolution expects an adjacent `../Minecolonies/build/libs/minecolonies-1.1.1305-1.21.1-snapshot.jar`.

Useful commands:

```powershell
./gradlew compileJava
./gradlew processResources
./gradlew runClient
```

Treat `build/` and `runs/` output as generated local state. The source of truth for the mod is under `src/main/java` and `src/main/resources`.
