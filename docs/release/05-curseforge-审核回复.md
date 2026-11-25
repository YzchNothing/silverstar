# CurseForge 审核回复：衍生作品说明（短版）

审核原话（2026-11-24）：

> 衍生作品只有在原始项目的许可证为开源时才能提交。
> 如果是，请在描述中注明出处并附上原始内容的链接，并清楚说明你的项目与原作有何不同。
> 如果原始项目是基于 ARR 授权的，除非你得到创作者的许可，否则我们无法接受你的项目。

## 结论

两个前置都是 **MIT**（GitHub API 查证），满足"开源"这一条，只需在描述里补一句话说明衍生关系，不需要授权文件：

| 原始项目 | 许可证 |
|---|---|
| SlashBlade: Resharped（拔刀剑：重锋） | MIT |
| SlashBlade: Japanese Addon Pack Reshaped | MIT |

## 贴进 Description 的两行（英文）

**Derivative work.** This is an addon for **SlashBlade: Resharped** (MIT, https://www.curseforge.com/minecraft/mc-mods/slashblade-resharped): it adds content on top of the original API and does not modify, patch or redistribute the original mod.

**Assets.** Blade model is SlashBlade's own model, referenced at runtime and not bundled (used with permission). Star algorithm adapted from the resource pack「星空物品」by 洛谔谔 (used with permission). Some star textures from Re-Avaritia (MIT). All other code MIT.

## 中文对应

**衍生作品说明**：本模组是拔刀剑：重锋（MIT）的附属，只在原作 API 之上追加内容，不修改、不注入、不重新分发原作本体。

完整描述见 `01-curseforge-描述.md`（短版，英文为主）。

## 顺手要补的一项

项目页 `Environment` 显示 **Not Set**，文件里写的是 Client and server，建议补上。
