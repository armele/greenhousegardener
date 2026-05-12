# Greenhouse Gardener

Greenhouse Gardener is a MineColonies add-on for colonies that need to grow crops with biome-specific requirements. The mod adds a custom Greenhouse building whose worker maintains artificial biome zones for designated fields. Those zones let colony farms satisfy crop biome checks without requiring the colony to be physically built in every needed natural biome.

## Intended Gameplay

The player builds a MineColonies Greenhouse. The building provides a module for selecting which crop fields can be climate controlled - altering the biome to allow growth of biome-specific crops. Biome modification requires that material be provided that enable that modification.

The Greenhouse worker orders approved materials from the colony warehouse. Those materials represent the environmental systems required to alter and sustain the local growing conditions by adjusting the temperature and humidity.

When a greenhouse biome is applied, the building records the natural biome for each affected position before changing it. If the Greenhouse is deconstructed, or loses responsibility for an area, the stored natural biome state must be restored.

## Greenhouse Building

The greenhouse capacity is:

| Level | Fields |
| --- | ---: |
| 1 | 1 |
| 2 | 2 |
| 3 | 3 |
| 4 | 4 |
| 5 | 4 |

### Modules
The building module UI supports selecting the target biome conditions for each maintained field slot and designating what blocks will be used for biome conditioning. 

Biome conditioning takes place along two axes: temperature and humidity. Eligible blocks for these four changes (temperature up, temperature down, humidity up, humidity down) are defined by datapack CCU value files, and selectable in the module UI.

### Field Logic
The farmer continues to work climate controlled fields as normal.  To designate a field as climate controlled:
- A climate control hub must be placed under the field block.
- A valid greenhouse roof must be created (all field positions covered, and made of at least 75% glass or other valid roof materials).
- The hub must remain in place. Removing it causes the greenhouse to release ownership of that field and restore its biome overlay.
- The climate-controlled field must be claimed by the Greenhouse so the horticulturist knows to maintain it.
- A field can only be claimed by one greenhouse at a time.

## Worker and Materials
Adds a Horticulturist job that periodically:

- Reads target biome assignments from the building.
- Finds acceptable climate modification materials in the warehouse using the selected item tags.
- Uses these materials to modify (initial cost) and maintain (lower ongoing cost) the climate in the specific field area.

### Worker Skills
The horticulturists' primary skill improves their efficiency when using climate control blocks.  Their secondary skill improves their work speed.

## Research
New research is introduced (found in the University like any other research) which unlocks the building, improves the number of maintainable biome variations, and improves the efficiency of the maintenance.

## New Crops and Food Recipes
New crops are:
- Cucumbers (temperate biomes)
- Spinach (temperate biomes)
- Broccoli (cold biomes)

Over a dozen new food recipes are available - look them up in your JEI!

## Greenhouse Design Guide for Style Builders

This section is for MineColonies style builders creating greenhouse schematics that should work cleanly with Greenhouse Gardener. The short version: build real MineColonies fields, put a Climate Control Hub directly below each field anchor, cover the whole crop footprint with a valid roof, and leave space between fields that may use different climates.

### Required Field Layout

Each climate-controlled crop area must be a registered MineColonies farm field. Greenhouse Gardener reads the field anchor and the field's north, south, east, and west radii from MineColonies, then uses that footprint as the real crop area.

For each controlled field:

- Place a Climate Control Hub directly below the field anchor block.
- The field anchor is the position returned by the MineColonies Field block (the scarecrow).

The Climate Control Hub is waterloggable and emits light, so it can be hidden under irrigated or decorative field centers if the block above remains the actual field anchor.

### Biome Conditioning Area

Biome conditioning is applied to the field footprint from MineColonies, plus a hidden buffer. Minecraft resolves block biomes from quart biome cells with smoothed lookup, so Greenhouse Gardener expands the conditioned region by 4 blocks on the horizontal X/Z edges before writing biome cells. This is intentional: it ensures crops on the outermost field blocks resolve to the conditioned biome.

Builder implications:

- Do not design two differently conditioned fields immediately beside each other.
- Leave at least 4 blocks of horizontal spacing between the crop footprints of fields that may use different climates.
- More spacing is safer when decorative layouts make the field edges hard to read.
- Adjacent or overlapping fields that always share the same target climate are not a problem.
- The hidden 4-block biome buffer is not charged as extra conversion area and is not part of roof validation; it only protects crop biome lookup at the field edges.

If a style places several fields in one room, make their crop footprints visually clear enough that players can understand which hub and roof area belongs to each field.

### Roof Requirements

The Horticulturist validates the roof before converting or maintaining a modified field. The check scans every X/Z column in the MineColonies field footprint, from one block above field height up to 20 blocks above field height.

A compatible roof must satisfy both conditions:

- Every field column must have some roof-like cover within 20 blocks above the field. Air-only columns are treated as holes.
- At least the configured percentage of field columns must contain a block tagged as `greenhousegardener:greenhouse_roof`. The default requirement is 75 percent.

Roof-like cover can be an untagged solid or sturdy ceiling block, but untagged cover only prevents a hole failure. It does not count toward the tagged greenhouse-roof percentage. Domum Ornamentum blocks can count as tagged greenhouse roof material when one of their component materials is tagged appropriately.

Practical style guidance:

- Put the greenhouse roof directly over the entire field footprint, including corners.
- Keep the roof within 20 blocks above the field anchor Y level.
- Use tagged roof materials for most of the ceiling, not just trim or decoration.
- If using mixed decorative roofing, keep at least 75 percent of the field columns tagged, or raise the ratio only if the pack's config also changes.

### Worker Access

The Horticulturist physically walks to the field anchor and to each roof-inspection corner before conversion or maintenance proceeds. Build paths and doors so the worker can reach:

- The Greenhouse hut block and storage.
- Each field anchor.
- The four corners of each field footprint at field height.

Avoid sealed display-only greenhouses where the worker can see the field but cannot path to the anchor or inspection corners.

### Maintenance And Reversion

Climate-changed fields require maintenance. If the worker cannot maintain a field because the roof is invalid or climate materials are unavailable, the field can eventually revert to its natural biome. The default missed-maintenance window is 5 colony days.

When a field reverts to its natural biome the seed will be unset from that farm field.

The horticulturist will not try to do anything with a reverted field until the next colony day.

### Field Positioning

The horticulturist doesn't have the technology to accomplish precision biome modification. There's always a bit of area around the field that will be affected by this conditioning as well.  This means if you have two fields with different conditioned biomes too close together, your field biomes will "fight" with eachother - causing wasted CCU and ineffective crop growth.  Your horticulturist will warn you of this situation.  To avoid it, make sure your conditioned fields are about 8 blocks apart, at least - or share the same temperature and humidity settings.

## Style Guidance
For reliable schematics:

- Include enough storage access for climate materials.
- Do not depend on temporary scaffold blocks as roof coverage.
- Keep the Climate Control Hub protected from accidental replacement by style upgrades.
- Keep fields and hubs stable across building levels unless the intended design deliberately removes a field.

### Quick Compatibility Checklist

- MineColonies farm field exists and is registered.
- `greenhousegardener:climatecontrolhub` is directly below the field anchor.
- Worker can path to the field anchor and all four field corners.
- Every crop-footprint column has roof-like cover within 20 blocks above field height.
- At least 75 percent of crop-footprint columns use `greenhousegardener:greenhouse_roof` tagged material, unless the pack config changes the requirement.
- Fields that may have different climates are separated by at least 4 blocks between crop footprints.
- Field footprint, roof footprint, and visible room design all agree, so players can tell what area is being conditioned.

## Datapack Customization for Modpack Authors

Greenhouse Gardener exposes climate materials through datapack JSON. Pack authors can add new files instead of editing the mod's built-in data.

### Climate CCU Values

Climate Control Unit values live in:

```text
data/<namespace>/greenhouse_climate_items/*.json
```

Each file may define any of the four climate directions. Values are CCU per consumed item.

```json
{
  "replace": false,
  "temp_up": {
    "minecraft:coal": 18,
    "minecraft:lava_bucket": 220
  },
  "temp_down": {
    "minecraft:snowball": 1,
    "minecraft:blue_ice": 220
  },
  "humid_up": {
    "minecraft:water_bucket": 55
  },
  "humid_down": {
    "#minecraft:wool": 11,
    "minecraft:sponge": 55
  }
}
```

The direction keys are:

- `temp_up`: heating materials.
- `temp_down`: cooling materials.
- `humid_up`: humidifying materials.
- `humid_down`: drying materials.

Object keys may be item ids such as `minecraft:blue_ice`, or item tags prefixed with `#`, such as `#minecraft:wool`. Direct item values override tag values for that item. If multiple tags match an item, the highest matching tag value is used.

Files are merged across namespaces and packs. Use `"replace": false` or omit it to add or override individual entries. Use `"replace": true` only when intentionally replacing all previously loaded climate item values.

### Climate Remainders

Climate remainders live in:

```text
data/<namespace>/greenhouse_climate_remainders/*.json
```

These define the item returned after a climate material is consumed, for items whose normal crafting remainder does not already handle it.

```json
{
  "consumed": "minecraft:kelp",
  "remainder": "minecraft:dried_kelp"
}
```

Built-in item crafting remainders are used automatically before this datapack mapping is checked. For example, bucket-like items that already expose a crafting remainder do not need a custom remainder file.

## Dependencies
Minecraft version 1.21.1
MineColonies (and it's dependencies) version 1.1.1305+
Neoforge 21.1.222+
