# Greenhouse Gardener

Greenhouse Gardener is a MineColonies add-on for colonies that need to grow crops with biome-specific requirements. The mod adds a custom Greenhouse building whose worker maintains artificial biome zones inside the building footprint. Those zones let colony farms satisfy crop biome checks without requiring the colony to be physically built in every needed natural biome.

## Intended Gameplay

The player builds a MineColonies Greenhouse. The building exposes a module for selecting which crop fields inside the building should be assigned to which biome, and which tagged materials the worker may consume to maintain those biome changes.

The Greenhouse worker pulls approved materials from the colony warehouse. Those materials represent the environmental systems required to alter and sustain the local growing conditions, such as heat, humidity, soil additives, cooling, or magical equivalents provided by datapacks and modpacks.

When a greenhouse biome is applied, the building records the natural biome for each affected position before changing it. If the Greenhouse is deconstructed, downgraded, or loses responsibility for an area, the stored natural biome state must be restored.

## Building Levels

The greenhouse capacity is:

| Level | Fields | Supported Biomes |
| --- | ---: | ---: |
| 1 | 1 | 1 |
| 2 | 2 | 1 |
| 3 | 3 | 2 |
| 4 | 4 | 3 |
| 5 | 4 | 4 |

### Modules

The building module UI should support:

- Selecting the target biome for each unlocked field slot.
- Selecting allowed power material tags.
- Showing whether each field is active, pending materials, applying, or restored.
- Showing current worker/material status.

### Worker and Materials

Add a Greenhouse worker job that periodically:

- Reads target biome assignments from the building.
- Finds acceptable climate modification materials in the warehouse using the selected item tags.
- Uses these materials to modify the climate in the specific field area.
