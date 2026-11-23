## SilverStar (English)

A SlashBlade addon that welds a night sky onto a katana.

Minecraft **1.20.1 / Forge**, requires **SlashBlade: Resharped**.

### The blade

**SilverStar**: 75 base damage, 75 durability, built-in Power 20 / Unbreaking 3 / Sharpness 10.

The edge is not a texture but a slowly drifting star field; slash arcs, phantom swords, Judgement Cut ribbons and wave slashes all share it — every swing throws off a piece of the night sky.

Want the handle and scabbard cosmic too? Set `bladeStarRender` in `config/silverstar-client.toml`; it applies instantly.

### Slash Art: Paradise Falls

Charge, then drop Judgement Cuts on every enemy within 9 blocks (and slow them); afterwards teleport above (or behind) the lowest-health target and follow up with Aerial Cleave (or Void Slash).

### Special Effect: One Left (level 50+)

- Every 300 kills adds 15% of the hit as a second strike, no cap
- The second strike is half magic, half dragon's breath; neither is reduced by armor
- Every 3rd swing, the 4th hit doubles both the main hit and the second strike

### Special Effect: Star Eclipse (level 20+)

- Hits apply "star scars", up to 5, +4% attack damage each
- The 5th hit detonates for `max(15% of max health, your attack damage)`
- A scarred target's death explodes within 4 blocks and spreads scars to nearby enemies

### Recipe

```
Fox White (2000 kills)   Netherite Ingot   Fox Black (2000 kills)
Book of Vanishing        Iron Sword        Book of Binding
Netherite Ingot          Nihilbx (0 kills) Netherite Ingot
```

### Requirements

| Requires | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| SlashBlade: Resharped | 1.1.23 ~ 1.1.x |
| SJAP | 1.1.10 ~ 1.1.x |

Versions are pinned: the renderer is injected via mixins against 1.1.23 signatures. With an Iris/Oculus pack the cosmic layers fall back to vanilla materials.

### Credits

- Blade model from SlashBlade, modified **with the team's permission**; no third-party art is bundled
- Cosmic algorithm from the resource pack 「星空物品」 by 洛谔谔 (bilibili `BV1834162fUW`), **used with permission**
- Some star textures from Re-Avaritia (MIT)


# 1.0.0 — 首个正式版

Minecraft 1.20.1 / Forge 47。

## 内容

**刀：纯银星**

- 基础伤害 75、耐久 75，自带力量 20 / 耐久 3 / 锋利 10，腰间挂刀
- 刀技「天堂陨落」：蓄力后释放，对半径 9 格内目标释放次元斩并施加缓慢，随后瞬移到血量最低目标上方（或后方），释放空坠斩（或虚无刀界）
- 特殊效果「唯余其一」（需 50 级）：每 300 击杀，命中追加 15% 第二段伤害，无上限；第二段一半魔法一半龙息，都不吃护甲；每挥 3 刀，第 4 刀本体与第二段一起翻倍
- 特殊效果「星蚀」（需 20 级）：命中叠星痕（最多 5 层、每层 +4% 攻击力），第 5 次命中引爆 `max(目标血上限 15%, 面板攻击力)`；带痕目标死亡时按层数在 4 格内爆炸并传染星痕

**星空渲染**

- 刀身、刀光、幻影剑、次元斩风纹、斩击波共用一套流动星空，星点按 16×16 点阵字形铺开
- 刀柄、刀鞘是否铺星空可配置（`bladeStarRender`，默认关，改完立刻生效）

## 兼容

| 需要 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| SlashBlade: Resharped | 1.1.23 ~ 1.1.x（锁死） |
| SJAP | 1.1.10 ~ 1.1.x（锁死） |

## 授权

- 刀模型引自拔刀剑本体，经制作团队授权修改
- 星域算法出自资源包「星空物品」（洛谔谔），已获授权
- 部分星图贴图来自 Re-Avaritia（MIT）；其余代码 MIT
