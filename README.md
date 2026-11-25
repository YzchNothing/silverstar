# SilverStar / 纯银星

Minecraft **1.20.1 · Forge** 的拔刀剑（SlashBlade: Resharped）附属 mod。

两把刀、两个刀技、四个特殊效果：

| 刀 | 刀技 | 特殊效果 |
|---|---|---|
| **纯银星**（伤害 75 / 耐久 75） | 天堂陨落 | 唯余其一、星蚀 |
| **金色星**（伤害 70 / 耐久 70） | 余烬 | 余温、碎金 |

刀身、刀光、幻影剑、次元斩风纹、斩击波全部采用**无尽贪婪（寰宇支配之剑）风格的星空渲染**：纯银星是冷白星海，金色星是同一片星空里正在燃的那一颗。

- 前置：SlashBlade: Resharped `1.1.23` ~ `1.1.x`、SlashBlade Addon (SJAP) `1.1.10` ~ `1.1.x`
- 制作：**GoldenStar**（程序、着色器、美术、文档）｜ **纯银星 / YzchNothing**（项目所有者、设计、测试与发布）
- 版本：1.1.0 ｜ 许可证：MIT
- 下载：[GitHub Releases](https://github.com/YzchNothing/silverstar/releases)

## 配置

`config/silverstar-client.toml`（改完即时生效）

| 键 | 默认 | 说明 |
|---|---|---|
| `bladeStarRender` | `false` | 刀柄、刀鞘是否也铺星空 |
| `armRenderMode` | `0` | 左臂星空壳：0 第 1 格有刀且不在主手；1 快捷栏有刀；2 一律 |
| `armColorMode` | `0` | 左臂颜色（仅 1/2 生效）：0 自动、1 青、2 金 |

## 授权与致谢

- **SlashBlade: Resharped** — 玩法全部寄宿于它；模型通过 `render.model` 引用它自带的 `sange.obj`，本 mod 的 jar 内不含其美术文件。模型使用**已获拔刀剑制作团队授权**，仅限拔刀剑附属使用。
- **SJAP** — 配方里的「死念」「豆腐『金刚』」出自它。
- **星空着色器** — 算法与点阵字形出自资源包「星空物品」（作者 **洛谔谔**，B 站 `BV1834162fUW`），已获作者授权；星域底图参考 **Re-Avaritia**（MIT）。

代码除上述引用外均为本项目自写。

## AI 声明

本模组的代码、着色器、贴图与文档由 **AI 编程助手（DeepSeek）辅助生成**，作者负责设计、审查、测试与发布决策。第三方内容的使用均已获授权，见上节。
