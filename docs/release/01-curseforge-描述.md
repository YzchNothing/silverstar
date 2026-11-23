# CurseForge 项目描述（直接粘贴）

> 用法：CurseForge 新建项目时，「Description」分栏里按下面两段粘贴；中文在前、英文在后。
> Markdown 语法 CurseForge 支持。

---

## 纯银星 · SilverStar

一把把星空焊在刀身上的拔刀剑附属。

For Minecraft **1.20.1 / Forge**，需要 **SlashBlade: Resharped**。

### 这把刀

**纯银星** —— 基础伤害 75、耐久 75，自带 力量 20 / 耐久 3 / 锋利 10。

它的刀刃不是贴图，是一片会缓慢漂移的星域：星点按 16×16 的点阵字形铺开，每一层都在动。
刀光、幻影剑、次元斩的风纹、斩击波全都是同一套星空——所以你挥出去的每一刀，都是从刀上甩出去的一片夜空。

如果想让整把刀（刀柄、刀鞘也是）都变成星域，去 `config/silverstar-client.toml` 把 `bladeStarRender` 打开。

### 刀技：天堂陨落

起手沿用拔刀剑的第一刀动作，随后半径 **9 格**内的敌人全部落下一轮次元斩。
范围内没有目标时不会空放，省得在空地上浪费一次冷却。

### 特殊效果一：唯余其一

被围住的时候最有用。

- 周围每有一个敌人，第二段伤害 +15%，最多 +90%
- 每击杀一次攒一层「裁定」，每层再 +2%
- 攒到 20 层进入「终局」，10 秒内必暴

### 特殊效果二：星蚀

打人叠「星痕」，最多 5 层：

- 每层让你对那个目标多打 **+4%**
- 第 5 层引爆，造成 `max(目标血上限的 15%, 你的面板攻击力)`——所以后期装了伤害膨胀的整合包也不会变成添头
- 带星痕的目标死亡时会爆炸，并把星痕传染给周围

### 合成

工作台：

```
白狐(2000 杀)   下界合金锭   黑狐(2000 杀)
消失诅咒附魔书    铁剑      绑定诅咒附魔书
下界合金锭      死念(0 杀)  下界合金锭
```

「白狐」「黑狐」是 `slashblade:fox_white` / `fox_black`，都要 **2000 击杀**；「死念」是 SJAP 的 `slashblade_addon:nihilbx`；两本书分别是**消失诅咒**与**绑定诅咒**。

### 前置与兼容

| 需要 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x |
| SlashBlade: Resharped | 1.1.23 ~ 1.1.x |
| SlashBlade Addon (SJAP) | 1.1.10 ~ 1.1.x |

- 拔刀剑的版本范围是**锁死**的：本 mod 的星空渲染靠 mixin 逐个对准 1.1.23 的方法签名，对方改了签名就会启动失败。
- 开光影（Oculus / Iris）时星空层会让位给原版材质，只保留配色；关掉光影即恢复。

### 授权

- 刀模型引用自 SlashBlade，**经拔刀剑制作团队授权**修改后使用，本 mod 的 jar 内不含对方的美术文件
- 星域渲染算法出自资源包「星空物品」（作者 猪猪三号，B 站 `BV1834162fUW`），**已获作者授权**
- 部分星图贴图来自 Re-Avaritia（MIT）
- 其余代码 MIT

---

## SilverStar (English)

A SlashBlade addon that welds a night sky onto a katana.

For Minecraft **1.20.1 / Forge**, requires **SlashBlade: Resharped**.

### The blade

**SilverStar** — 75 base damage, 75 durability, with Power 20 / Unbreaking 3 / Sharpness 10 built in.

The edge is not a texture: it is a slowly drifting star field, drawn as a 16×16 grid of glyphs, every layer moving.
The slash arcs, the phantom swords, the wind ribbons around Judgement Cut and the wave slashes all use the same cosmic renderer — every strike throws a piece of the night sky off the blade.

Want the whole weapon (handle and scabbard included) to turn cosmic too? Set `bladeStarRender = true` in `config/silverstar-client.toml`.

### Slash Art: Paradise Falls

Starts with SlashBlade's normal first swing, then drops a round of Judgement Cuts on every enemy within **9 blocks**.
It does not fire into thin air when there is no target.

### Special Effect: One Left

Best when you are surrounded.

- +15% second-hit damage per nearby enemy, capped at +90%
- Every kill adds one stack of "verdict", +2% each
- At 20 stacks you enter "terminal": guaranteed crits for 10 seconds

### Special Effect: Star Eclipse

Hits apply "star scars", up to 5:

- +4% damage against that target per scar
- The 5th scar detonates for `max(15% of the target's max health, your attack damage)` — it scales with the target, so it stays relevant in late-game modpacks
- A scarred target that dies explodes and spreads scars to nearby enemies

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
| SlashBlade Addon (SJAP) | 1.1.10 ~ 1.1.x |

- The SlashBlade version range is pinned on purpose: the cosmic renderer is injected via mixins against 1.1.23 method signatures.
- With an Iris/Oculus shader pack active the cosmic layers fall back to vanilla materials (colour tint only); disable the pack to get them back.

### Credits & permissions

- Blade model referenced from SlashBlade, modified **with permission from the SlashBlade team**; no third-party art is bundled in this jar
- Cosmic shader algorithm from the resource pack 「星空物品」 by 猪猪三号 (bilibili `BV1834162fUW`), **used with the author's permission**
- Some star textures from Re-Avaritia (MIT)
- All other code MIT
