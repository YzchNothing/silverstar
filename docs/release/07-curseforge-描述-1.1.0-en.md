**SilverStar** is an addon for **SlashBlade: Resharped** that turns a blade into a drifting star field.

Two katanas, two sword arts, four sword effects, and a client-side cosmic renderer for the blade, the slash arc, the phantom swords, the left arm and the judgement-cut ring.

## Blades

| Blade | Damage | Durability | Sword art |
|---|---|---|---|
| **Silver Star** | 75 | 75 | Paradise Falls |
| **GoldenStar** | 70 | 70 | Ember |

Both come with built-in enchantments. GoldenStar also has a crafting recipe.

## Sword arts

**Paradise Falls** (Silver Star) — in two stages. First it sweeps a 9-block radius around you and calls a judgement cut onto every lockable target inside, pinning them in place for 2 seconds. Then, after a short pause, it picks whichever of those targets is lowest on health: if there is headroom above it, you blink 4 blocks up and drop onto it with an aerial slash; if it is boxed in, you blink behind it and follow up with a void slash.

**Ember** (GoldenStar) — three golden slash arcs, three follow-up hits of dragon's breath damage, and the target burns for 4 seconds. The arc direction follows the yaw of the entity that cast it, so it reads the same facing north or east.

## Sword effects

**One Left** (level 50) — a blade that remembers its kills. Every 300 kills on the blade turns 15% of your hit into a second, separate strike, with no cap. That second strike is split in half: one half is a guaranteed 2.5× critical magic hit, the other half is dragon's breath — neither is reduced by armour. Every fourth swing doubles both the main hit and the second strike.

**Star Eclipse** (level 20) — every hit stacks a scar on the target, up to 5. Each scar adds 4% attack damage to the hit that applies it. Five hits in a row detonate: an untyped magic burst for whichever is higher, 15% of the target's max health or your panel attack. When a scarred target dies it explodes for scar count × 8% of its own max health within 4 blocks and passes half its scars on to everything nearby.

**Afterglow** (level 1) — every hit leaves a mark for 6 seconds. Striking a marked target again adds 35% of panel damage and refreshes the mark. Killing a marked target heals you 30% of max health and marks every other hostile target within 4 blocks.

**Gold Shards** (level 30) — three star-shard follow-up hits land on the target at ticks 1, 3 and 5, 20% of panel damage each. On top of that, the hit itself gets an execute bonus that scales with how hurt the target already is: 10% at full health, 50% at half, 90% when nearly dead.

None of these effects check whether a target can burn or whether a status effect applied — they track their own state, so they work the same on mobs from other mods.

## Client rendering

The star field is a shader, not a texture swap: the blade, the slash arc, the judgement-cut wind ring and the left-arm shell each run their own pass, and GoldenStar has its own set of gold shaders so the two blades never tint each other. Configurable in `config/silverstar-client.toml`:

- `bladeStarRender` — also render the handle and scabbard as cosmic
- `armRenderMode` / `armColorMode` — the left-arm star shell and which blade's colour it uses

## Requires

- Minecraft 1.20.1, Forge 47.x
- [SlashBlade: Resharped](https://github.com/0999312/SlashBlade_Resharped) 1.1.23+
- [SlashBlade: Japanese Addon Pack](https://github.com/0999312/SlashBlade-Japanese-Addon-Pack-Reshaped) 1.1.10+
- [RenderBlender](https://github.com/Nova-Committee/RenderBlender)

## Derivative work

This is an addon for **SlashBlade: Resharped** ([GitHub](https://github.com/0999312/SlashBlade_Resharped), MIT). It only adds content on top of the original mod: two blades, two sword arts, four sword effects, two recipes and a client-side render layer. It does not modify the original mod's files, does not redistribute the original mod, and does not change any existing weapon.

## Assets and credits

- Blade model: SlashBlade's own model, referenced at runtime from the installed mod — not bundled. Used with the SlashBlade team's permission.
- Star algorithm: adapted from a bilibili resource pack, used with the author's permission ([video BV1834162fUW](https://www.bilibili.com/video/BV1834162fUW)).
- Some star textures: [Re-Avaritia](https://github.com/Nova-Committee/Re-Avaritia) (MIT).
- Source: https://github.com/YzchNothing/silverstar — MIT.
