## 1.1.2

### Added
- Starfield now also renders while a shader pack (Iris / Oculus) is active. The star layers are recorded during the normal pass and drawn right after the shader pack finishes its final composite, so the blade, slash arcs, phantom swords, sword waves, judgement cut wisps and the lock-on ring all keep their starfield look in-game with shaders on.

### Changed
- Under shaders the slash colour follows the blade itself (silver for SilverStar, gold for GoldenStar) instead of a fixed cyan.
- GoldenStar's sword wave and phantom sword brightness lowered, so a shader pack's bloom no longer blows them out into a full-screen flash.
- Ember's slash brightness raised by 25%.
- Phantom sword now shares the slash arc's star scale, tint and brightness instead of its own purple variant.

### Fixed
- Under shaders only the first slash of a pair was visible; the others sampled an empty area of the texture and ended up fully transparent.

### Compatibility
- Requires SlashBlade: Resharped 1.1.23+ and SlashBlade Addon 1.1.10+.
- Minecraft 1.20.1 / Forge 47.x / Java 17.
