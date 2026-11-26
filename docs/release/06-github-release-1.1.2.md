## SilverStar 1.1.2

Starfield rendering now survives shader packs. With Iris / Oculus enabled, the star layers are recorded during the normal pass and drawn right after the pack's final composite — so blade, slash arcs, phantom swords, sword waves, judgement cut wisps and the lock-on ring all keep their cosmic look in-game.

### Changes
- Under shaders the slash colour follows the blade: silver for SilverStar, gold for GoldenStar (previously a fixed cyan).
- GoldenStar's sword wave and phantom sword dimmed so bloom no longer turns them into a full-screen flash.
- Ember's slash brightness raised by 25%.
- Phantom sword now uses the same star scale, tint and brightness as the slash arc.

### Fixed
- With shaders on, only the first slash of a pair was visible; the rest sampled an empty texture area and came out fully transparent.

### Compatibility
Minecraft 1.20.1 · Forge 47.x · Java 17 · SlashBlade: Resharped 1.1.23+ · SlashBlade Addon 1.1.10+

`silverstar-1.1.2.jar` · 890210 bytes · SHA-256 `0EE05F5232CE50C01DE4C3A97CE14B37E6E9476163DAE35EB486E6641D17AE40`
