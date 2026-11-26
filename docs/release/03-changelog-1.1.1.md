# 1.1.1

Minecraft 1.20.1 / Forge 47。

## 修复

- **崩溃**：金色星的「碎金」在服务端 tick 里一边遍历待落星屑列表、一边往同一个列表里塞元素，抛 `ConcurrentModificationException`，导致**用金色星命中任何目标后的一两个 tick 内整局崩服**。改成「先算出本 tick 到期的、和还要继续倒计时的两批，再一次性替换列表」。

## 变化

- **纯银星 SE「唯余其一」**：需求等级 50 → **30**
- **纯银星 SE「唯余其一」**：第二段里的龙息伤害改为**玩家归属**（伤害类型仍是龙息，仍不吃护甲，但击杀算玩家）
- **纯银星配方**：三处下界合金锭 → 散华（`slashblade:sange`，不限击杀数）
- **金色星配方**：两处豆腐「金刚」→ 宝刀「付丧」结月（`slashblade:yuzukitukumo`，要求击杀 ≥ 2000）
  - 原因：豆腐「金刚」在 SJAP 里由 `compat.SBATofuCraftItems` 注册，**只有装了 TofuCraft 才存在**，不装豆腐工艺的整合包里那两格永远填不上，配方做不出来

## 兼容

Minecraft 1.20.1 / Forge 47.x / SlashBlade: Resharped 1.1.23+ / SJAP 1.1.10+
