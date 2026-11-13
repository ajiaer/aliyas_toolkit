# Aliya's Toolkit / Aliya的工具包

[English](#english-at-a-glance) | [中文](#中文说明)

---

# English at a glance

**Aliya's Toolkit** is a Minecraft 1.21.1 / NeoForge quality-of-life mod that combines inventory switch, chain mining, quick building and blueprint copying into one toolkit.

> **Made with 1% inspiration + 99% AI.**
>
> **Tested only in vanilla conditions:** Minecraft 1.21.1 + NeoForge, single player / integrated server. Other mod combinations and dedicated multiplayer servers are not tested.

## Why it is useful

- **Spend less time in the inventory**: swap the hotbar with a chosen inventory row, and automatically refill whitelisted hotbar items.
- **Spend less time repeating actions**: chain mining with massive-block presets, and quick building with point-to-point / center-to-point, line / rectangle / circle / sphere presets.
- **Take structures with you**: a blueprint box records block states, block entities, container contents and bucketable fluid sources, then previews and rebuilds the structure elsewhere.
- **One settings screen**: `Alt + X` opens per-player settings for whitelist, chain presets, quick-build presets and blueprint options.
- **Server authoritative**: swaps, mining, building, recording and material consumption are validated again on the server.

## Requirements

| Item | Version |
| --- | --- |
| Minecraft | **1.21.1** |
| Mod loader | **NeoForge 21.1.265+** (21.1.x) |
| Java | **21** |
| Environment | **Client + server** |
| Tested on | Vanilla-only: Minecraft 1.21.1 + NeoForge, single player / integrated server |
| Version | **1.21.1-1.1.1** |

## Feature summary

1. **Hotbar switching** (`X`): swap the hotbar with a chosen inventory row, or cycle the three inventory rows and the hotbar in forward/reverse order, without opening the inventory. Switching does not trigger auto-refill.
2. **Automatic refill**: whitelisted hotbar items are moved back from the inventory when they disappear. Optional exact item/component matching; durability is ignored.
3. **Chain mining** (`Z` toggle): break connected identical blocks, with a configurable chain limit and a massive-block preset (cross-section + layers + direction). Drops are collected and merged at the origin.
4. **Quick build** (`C` toggle): a green target outline follows your crosshair before the first point; mark two points, choose operation / dimension / shape / fill, and build with the block in your main hand. Up to 4096 blocks, 40-block reach, server validation.
5. **Blueprint build**: hold any blueprint box and press `Ctrl + right click`. An empty blueprint enters draw mode; an existing blueprint enters build mode. The entry prompt names the current mode as **Blueprint Build: Draw Mode** or **Blueprint Build: Build Mode**, and the target outline follows your crosshair before the first point. Draw with right/left clicks, snap points to block faces with `Shift`, press `Ctrl + left click` to record and collect materials (cut), or `Ctrl + right click` to record the blueprint only (copy). Preview with a ghost, pin/unpin with right/left click, and build with `Ctrl + right click`.
6. **Blueprint boxes**: 7 tiers from 27 to 162 slots, vanilla chest model and lid animation, 5×9 scrollable screen, ghost placeholders, material checking, hopper/comparator support, and per-tier fire/explosion/void protection.
7. **Auto Collect Fluids** (default ON): in cut mode, bucketable fluid sources are recorded and collected into filled buckets if enough empty buckets are available; otherwise a message is shown and fluids are skipped without cancelling the blueprint. In copy mode, fluid sources are recorded without consuming buckets or adding filled buckets.
8. **Per-player settings**: open with `Alt + X`; all values are saved with player data and survive death / respawn.

## Default keys

| Action | Default key |
| --- | --- |
| Open Aliya's Toolkit settings | `Alt + X` |
| Switch hotbar row / cycle | `X` |
| Toggle chain mining | Tap `Z` (applies on release) |
| Cycle massive-block preset | Hold `Z` and scroll |
| Toggle quick build | Tap `C` (applies on release) |
| Cycle quick-build preset | Hold `C` and scroll |
| Open the blueprint box in your main hand | `Alt + right click` |
| Blueprint draw: record and collect (cut) | `Ctrl + left click` |
| Blueprint build: enter / record copy / build | `Ctrl + right click` |
| Blueprint build: switch draw / build mode | `Tab` (box must have no items) |
| Blueprint draw: snap to block face | Hold `Shift` |
| Blueprint build: rotate ghost | `V` (before pinning) |
| Blueprint build: pin / unpin | Right click / left click |
| Exit chain mining / quick build / blueprint build | `` ` `` |
| Blueprint build: change ghost distance | Scroll |

All keys only work in-game and can be rebound in **Options → Controls → Key Binds → Aliya's Toolkit**.

## Build

Requires **JDK 21** (the Gradle wrapper is included):

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

- Output: `build/libs/aliyastoolkit-1.21.1-1.1.1.jar`
- Development client: `gradlew runClient`
- Development server: `gradlew runServer`
- Data generation: `gradlew runData`

Version format: **`<Minecraft version>-<mod version>`**, e.g. `1.21.1-1.1.1`.

## Disclosure

- **1% inspiration + 99% AI**: design, code, documentation and localization were produced with AI assistance.
- **Vanilla-only testing**: Minecraft 1.21.1 + NeoForge, single player / integrated server. Other mods, dedicated multiplayer servers and large modpacks are untested. Please reproduce issues in a minimal vanilla-like environment first.

## License

MIT License. See [LICENCE](LICENCE). Built from the [NeoForge MDK](https://github.com/NeoForged/MDK) template.

---

# 中文说明

> 一个把物品栏切换、连锁挖矿、快速建造、蓝图复制建造整合到一起的 Minecraft 工具模组。
>
> **1% 灵感 + 99% AI 制作。**
>
> **仅在原版条件下测试**：Minecraft 1.21.1 + NeoForge，单人游戏 / 内置服务器。未测试其它模组组合，也未测试专用多人服务器。

## 摘要

Aliya's Toolkit 不是“再加一个快捷键”的小模组，而是一套围绕**建造与物品管理**的工具链：

- **少开背包**：快捷栏与背包指定行一键互换；白名单物品从快捷栏消失后自动补回。
- **少做重复劳动**：连锁破坏带“大量方块”形状预设，快速建造带点到点 / 中心到点、直线 / 矩形 / 圆 / 球等预设。
- **把建筑带走**：蓝图箱可以记录完整结构——方块状态、方块实体、容器内容、流体源头——再以虚影预览的方式在别处复制建造。
- **一套界面管全部**：`Alt + X` 打开 Aliya's Toolkit 设置页，白名单、连锁预设、快速建造预设、蓝图设置都按玩家独立保存。
- **服务端权威**：所有交换、破坏、建造、蓝图记录与材料扣除都在服务端重新校验，客户端只负责预览和发请求。

适合建筑玩家、整合包作者，以及喜欢“少开背包、少重复劳动”的生存玩家。

## 适用范围

| 项目 | 版本 |
| --- | --- |
| Minecraft | **1.21.1** |
| 模组加载器 | **NeoForge 21.1.265** 及以上（21.1.x） |
| Java | **21** |
| 安装位置 | **客户端 + 服务端**（设计上需要双端安装） |
| 测试条件 | **仅原版环境**：Minecraft 1.21.1 + NeoForge，单人游戏 / 内置服务器 |
| 当前版本 | **1.21.1-1.1.1** |

- 模组 id：`aliyastoolkit`
- 命名空间：`aliyastoolkit:`
- 构建产物：`build/libs/aliyastoolkit-1.21.1-1.1.1.jar`

## 快速开始

1. 将模组安装到客户端和服务端（单人游戏只需放入 `mods`）。
2. 进入游戏后按 `Alt + X` 打开 **Aliya的工具包 设置**。
3. 在左侧选择功能页，按需调整白名单、连锁数量、建造预设或蓝图设置。
4. 所有快捷键都可以在 **选项 → 控制 → 按键绑定 → Aliya's Toolkit** 中修改。

默认按键一览：

| 操作 | 默认按键 |
| --- | --- |
| 打开 Aliya的工具包 设置 | `Alt + X` |
| 快捷栏切换（指定行 / 循环） | `X` |
| 连锁破坏开关 | 短按 `Z`（松开时生效） |
| 切换大量方块预设 | 按住 `Z` 滚动滚轮 |
| 快速建造开关 | 短按 `C`（松开时生效） |
| 切换快速建造预设 | 按住 `C` 滚动滚轮 |
| 打开主手蓝图箱界面 | `Alt + 右键` |
| 蓝图绘制：记录并收取（剪切） | `Ctrl + 左键` |
| 蓝图建造：进入 / 记录复制 / 建造 | `Ctrl + 右键` |
| 蓝图建造：切换绘制 / 建造模式 | `Tab`（箱子必须没有物品） |
| 蓝图绘制：吸附方块外侧面 | 按住 `Shift` |
| 蓝图建造：旋转虚影 | `V`（固定之前） |
| 蓝图建造：固定 / 解除固定 | 右键 / 左键 |
| 退出连锁破坏 / 快速建造 / 蓝图建造 | `` ` `` |
| 蓝图建造：调整虚影距离 | 滚轮 |

所有按键只在游戏内生效（打开任意界面时不会触发）。

## 核心功能

### 1. 快捷栏与背包行切换

- **指定行模式**（默认）：按 `X`（可改键），把快捷栏 9 格与「交换行」选中的背包行（1 = 最上面一行，3 = 最后一行；默认 3，槽位 27-35）两两互换。
- **循环模式**：按 `X` 让背包三行和快捷栏按顺序轮换。正序为第一行 → 第二行 → 第三行 → 快捷栏 → 第一行；倒序方向相反。
- 切换在服务端完成，不需要打开背包。
- 切换造成的空格**不会触发自动补充**，避免刚换完行就被补满。

### 2. 自动补充

- 快捷栏物品因丢弃、用光、工具耐久归零等原因消失时，如果背包里有**白名单里的相同物品**，会立刻把那一堆移回原来的快捷栏格。
- 只对白名单物品生效；打开背包 / 箱子 / 聊天等界面时不会触发。
- **完全一致**开关：开启时要求物品类型和全部数据组件（附魔、自定义名称等）一致；关闭时只比较物品类型。**耐久值始终不参与比较**，所以坏掉的镐子可以用新镐补上。

### 3. 连锁破坏

- 短按 `Z`（可改键）切换连锁状态，**松开按键时生效**；开启后挖方块会把周围相连的同种方块一起破坏，再按一次关闭。
- 与快速建造、蓝图建造等“模式类”功能互斥：同一时间最多开启一个。
- 普通方块：以准星指向方块为起点，在周围 3×3×3 内寻找相同方块并继续扩散，直到达到**连锁数量**（默认 64，范围 1-256，包含起点）；超出时优先保留离起点近的。
- **大量方块**（石头、圆石、花岗岩、下界岩、泥土等）使用预设：横截面（上 / 下 / 左 / 右各 0-4）+ 层数（1-8）沿朝前方向延伸；默认全 0、层数 1 时只挖指向的一格。
- **允许其它方块**（默认开）：形状范围内除指向方块外的其它可破坏方块也一并破坏；不可破坏方块和液体始终跳过。关掉后只破坏与指向方块相同的方块。
- 掉落物集中到起点并尽量合并成少堆，经验也集中在同一处；每个方块正常扣耐久。
- 连锁激活时只画外表面转角描边：直接可见部分为白色，被挡住部分为暗白色；指向方块用更粗的白色框标出。
- 按住 `Z` 滚动滚轮切换预设，到两端停住不循环；切换时用原版动作栏显示预设名称和参数。

### 4. 快速建造

- 短按 `C`（可改键）切换快速建造（松开时生效）；按住 `C` 滚动滚轮切换建造预设。
- 开启后、第一次选点前，目标格会跟随准星显示绿色轮廓，便于确认快速建造已经开启。
- 与连锁破坏、蓝图建造互斥。
- 右键第一次确定起点，显示绿色规划轮廓、辅助线，以及所需方块数量 / 背包已有数量。
- 右键第二次确定终点：检查主手可放置方块和数量（生存模式优先消耗背包，再快捷栏；创造模式不检查也不消耗），足够则立刻建造。
- 支持点到点 / 中心到点，一维 / 二维 / 三维，直线 / 矩形 / 圆 / 球，实心 / 空心 / 框架，并自动处理形状约束。
- 第一次选点时按原版放置规则记录方块状态（楼梯朝向与上下半、半砖上下半、原木朝向等），之后所有方块沿用该状态；建造完成后按原版 `updateShape` 规则重新计算连接。
- 一次最多 4096 个方块，目标点离玩家最远 40 格；服务端会重新计算范围并校验距离和方块数量。

### 5. 蓝图建造

#### 入口与两种模式

- 手持任意等级的蓝图箱，按 `Ctrl + 右键`（可改键）进入蓝图建造。
- **没有蓝图**：自动进入**模式 1 · 绘制**。
- **已有蓝图**：进入**模式 2 · 建造**。
- 进入后原有提示会显示当前模式：**蓝图建造：绘制模式** 或 **蓝图建造：建造模式**。
- 模式 2 下如果蓝图箱**没有物品**，可以按 `Tab`（可改键）切回绘制模式；之后记录的蓝图会覆盖当前蓝图。
- 蓝图箱里**有物品**时只能处于建造模式，无法切换，除非先清空物品或蓝图。

#### 模式 1 · 绘制

- 右键两个位置**扩选**，动态预览为**浅黄色**，已确定选区为**浅橙色**。
- 进入绘制模式后、第一次选点前，目标格会跟随准星显示淡黄色轮廓，便于确认当前模式已开启。
- 左键两个位置**缩选**，动态预览为**浅红色**；浅黄 / 浅红都优先于浅橙色显示。
- 之后右键扩选可以重新覆盖之前删除的区域；选区按操作顺序生效。
- 可多次叠加、多次删除。
- 按住吸附键（默认 `Shift`，可改键）时，选点会吸附到指向方块的外侧面，和快速建造的选点规则一致。
- 按 `Ctrl + 左键` 任意位置记录蓝图并收取材料：客户端/服务端重新读取结构，把方块物品、容器内容和流体桶收进蓝图箱，并清除源方块，相当于剪切。
- 按 `Ctrl + 右键` 任意位置只记录蓝图（复制）：源方块保持不变，也不向蓝图箱放入材料；两种方式都会退出蓝图建造。
- 绘制/记录时会**过滤蓝图箱**（方块和容器里的蓝图箱物品都不会被收集），并提示跳过数量。
- 选区里如果有箱子等容器，容器内容会作为材料需求记录；剪切时先清空并移除方块实体，**不会额外爆一份内容物**。

#### 模式 2 · 建造

- 进入后立即显示建造虚影，即使材料不足也会显示。
- 虚影默认在视线前方 4 格；滚轮调整距离，`V`（可改键）绕 Y 轴旋转 90°。
- 右键固定虚影，可以走近查看；左键解除固定。
- 固定后按 `Ctrl + 右键` 检查材料并建造：
  - 材料足够：扣除蓝图箱里的方块物品、容器内容和流体桶，按虚影位置建造；
  - 材料不足：提示并退出蓝图建造。
- 虚影透明度略微提高，只绘制玩家可见的方块面；蓝图轮廓线仍然正常显示。
- 玩家与虚影的碰撞**只在刚放出虚影时检测一次**，之后不会再把虚影从玩家身边推开，方便一眼看到完整结构。
- 寻找合法放置位置的范围为指示器周围半径 **8 格**的球；找不到位置时不在预览阶段反复提示。
- 箱子、潜影盒、床等 `ENTITYBLOCK_ANIMATED` 方块在虚影中使用真实 BlockState 渲染；相邻方块隐藏面会剔除，实体方块先写深度再半透明绘制。

#### 自动收集流体

- 默认开启，可在 `Alt + X → 蓝图建造` 页面切换。
- 开启时，**剪切模式**（`Ctrl + 左键`）记录蓝图会识别可用桶接取的流体源头（水、岩浆等），检查背包 / 快捷栏 / 副手的空桶数量：
  - 足够：消耗空桶，把对应装满的桶作为材料放入蓝图箱；
  - 不足：提示并跳过流体源头，但仍创建其它方块的蓝图，不取消记录。
- **复制模式**（`Ctrl + 右键`）只把流体源头记录进蓝图，不消耗空桶，也不放入装满的桶；之后建造时需要自行准备。
- 关闭时保持旧行为：流体源头不参与蓝图。

### 6. 蓝图箱

- **打开界面**：主手拿着蓝图箱按 `Alt + 右键`（可改键），等同于右键世界里的蓝图箱方块；清空蓝图 / 一键取出 / 一键存入、滚动都可用。
- 等级与容量（均为 9 列）：

| 等级 | 容量 | 合成 / 升级 |
| --- | --- | --- |
| 蓝图箱 | 27（3×9） | 木箱 + 8 纸 |
| 铜制蓝图箱 | 45（5×9） | 蓝图箱 + 8 铜锭 |
| 铁制蓝图箱 | 63（7×9） | 铜制 + 8 铁锭 |
| 金制蓝图箱 | 81（9×9） | 铁制 + 8 金锭 |
| 黑曜石蓝图箱 | 108（12×9） | 金制 + 8 黑曜石 |
| 钻石蓝图箱 | 135（15×9） | 黑曜石 + 8 钻石 |
| 下界合金蓝图箱 | 162（18×9） | 锻造台：钻石箱 + 下界合金锭 + 下界合金升级模板 |

- 方块直接继承原版 `ChestBlock` / `ChestBlockEntity`，使用原版 `ChestRenderer`，拥有箱子形状、朝向、比较器、开盖音效和翻盖动画；蓝图箱强制保持单箱，不会合并成双箱，也不能放进另一个蓝图箱。
- 界面最多显示 5 行 × 9 列，列与玩家背包对齐；超过 5 行时右侧有滚动条，可用滚轮或拖动。主面板使用原版 `generic_54.png`，右侧扩展区放滚动条和按钮，左右之间没有黑缝。
- 没有蓝图时槽位使用合成器同款禁用覆盖层；记录蓝图后显示半透明幽灵占位和每格需求量。占位只接受对应物品，并遵守每格上限。
- 槽位悬停显示物品说明：空占位显示占位物品和需求量，已放入物品显示物品本身说明。
- 容器内容物也作为材料成本：例如蓝图里有一个装着 3 个钻石的箱子，需要额外准备 3 个钻石；建造完成后放回原容器原槽位。
- 漏斗可以像普通箱子一样抽取，插入会检查物品和每格上限；比较器按「已填数量 / 总需求数量」输出 0-15 红石强度。
- 掉落物保护：
  - 所有蓝图箱在水中向上漂浮；
  - 铜制及以上方块本体防火 / 防岩浆；
  - 黑曜石及以上方块和掉落物防爆炸；
  - 下界合金掉落物防火 / 防岩浆、在岩浆中漂浮，掉入虚空会传送到附近安全位置。
- 铁砧改名会保留内容 / 蓝图；破坏方块会保留物品、蓝图和容器内容（创造模式也会生成掉落物）。

### 7. Aliya的工具包 设置界面

按 `Alt + X`（可改键）打开：

- 左侧 5 个功能页：`物品切换` / `自动补充` / `连锁破坏` / `快速建造` / `蓝图建造`。
- **物品切换**：切换模式、指定行和循环方向设置。
- **自动补充**：完全一致开关 + 3×3 白名单；从背包拿起物品左键设置，右键清空。
- **连锁破坏**：连锁数量、大量方块预设、锁定方向、允许其它方块。
- **快速建造**：预设选择、新建 / 删除、操作 / 维度 / 形状 / 填充。
- **蓝图建造**：自动收集流体开关，以及绘制 / 建造 / 缩放 / 旋转 / 打开箱子等操作提示；提示中的按键跟随实际按键绑定。
- 白名单和所有设置都绑定在玩家身上：多人游戏每人一份，随玩家数据保存，死亡重生后保留。

### 8. 配置与多人游戏

**全局配置**（`config/aliyastoolkit-common.toml` 或模组列表 → 配置）只有功能开关：

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `enableHotbarSwap` | `true` | 启用快捷栏交换 |
| `enableAutoRefill` | `true` | 启用自动补充 |
| `enableChainMining` | `true` | 启用连锁破坏 |
| `enableQuickBuild` | `true` | 启用快速建造 |
| `enableCopyPaste` | `true` | 启用蓝图建造 |

**多人游戏**：

- 所有物品栏 / 方块改动都在服务端执行，设计上要求**客户端与服务端都安装**。
- 网络通道注册为可选：装了模组的客户端可以进入没装模组的服务器（按键无效，不会掉线），反之亦然。
- 玩家白名单和设置保存在玩家数据中，每人独立。
- **当前仅在原版单人游戏 / 内置服务器条件测试，专用多人服务器未测试。**

### 9. 构建与版本

需要 JDK 21（项目自带 Gradle wrapper）：

```bash
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

- 产物：`build/libs/aliyastoolkit-1.21.1-1.1.1.jar`
- 开发客户端：`gradlew runClient`
- 开发服务端：`gradlew runServer`
- 数据生成：`gradlew runData`

版本号规则：**`<Minecraft 版本>-<模组版本>`**，例如 `1.21.1-1.1.1`。

### 10. 制作说明与测试范围

- **本项目由 1% 灵感 + 99% AI 制作。**
- 功能设计、代码、文档和本地化均在 AI 协助下完成；发布前请自行按整合包环境复核。
- **仅在原版条件下测试**：Minecraft 1.21.1 + NeoForge 21.1.265，单人游戏 / 内置服务器。
- 未测试其它模组组合、专用多人服务器、跨版本迁移和大型整合包环境；如遇到兼容问题，请优先在接近原版的最小环境中复现。

## 许可

本项目使用 **MIT License**，完整文本见根目录的 [LICENCE](LICENCE)。

项目基于 [NeoForge MDK](https://github.com/NeoForged/MDK) 模板创建。
