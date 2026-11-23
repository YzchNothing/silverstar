# SilverStar / 纯银星

Minecraft **1.20.1 · Forge** 的拔刀剑（SlashBlade: Resharped）附属 mod。

新增一把刀「纯银星」、一个刀技「天堂陨落」、两个特殊效果「唯余其一」「星蚀」。
刀身、刀光、幻影剑、次元斩风纹、斩击波全部是流动的星空渲染。

- 前置：SlashBlade: Resharped `1.1.23` ~ `1.1.x`、SlashBlade Addon (SJAP) `1.1.10` ~ `1.1.x`
- 作者：GoldenStar ｜ 版本：1.0.0 ｜ 许可证：MIT
- 下载：见 [Releases](../../releases)

## 内容

| 项目 | 说明 |
|---|---|
| 刀 | 基础伤害 75，耐久 75，自带 力量 20 / 耐久 3 / 锋利 10 |
| SA 天堂陨落 | 半径 9 格内全体落下一轮次元斩；范围内没有目标时不空放 |
| SE 唯余其一 | 周围每个敌人 +15% 伤害（最多 +90%）；每击杀攒一层裁定，20 层进入「终局」 |
| SE 星蚀 | 命中叠「星痕」（最多 5 层，每层 +4%）；第 5 层引爆，伤害取 `最大生命 15%` 与 `面板攻击力` 的较大值 |
| 配置 | `config/silverstar-client.toml` 的 `bladeStarRender`（默认 `false`）：是否让刀身也铺星空 |

开光影（Oculus / Iris）时星空层会让位给原版材质，只保留配色。

## 合成

```
白狐(2000 杀)   下界合金锭   黑狐(2000 杀)
消失诅咒附魔书    铁剑       绑定诅咒附魔书
下界合金锭      死念(0 杀)   下界合金锭
```

## 构建

```sh
gradlew.bat build     # Windows
./gradlew build       # Linux / macOS
```

产物在 `build/libs/`。发布材料（CurseForge / MC百科 文案、图标脚本）在 `docs/release/`。

## 授权与致谢

- **SlashBlade: Resharped** — 玩法全部寄宿于它；模型通过 `render.model` 引用它自带的 `sange.obj`，本 mod 的 jar 内不含其美术文件。模型使用**已获拔刀剑制作团队授权**，仅限拔刀剑附属使用。
- **SJAP** — 配方里的「死念」出自它。
- **星空着色器** — 算法与点阵字形出自资源包「星空物品」（作者 **猪猪三号**，B 站 `BV1834162fUW`），已获作者授权；星域底图参考 **Re-Avaritia**（MIT）。

代码除上述引用外均为本项目自写。
