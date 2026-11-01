# Aliya's Toolkit

A compact quality-of-life mod for the hotbar and inventory, built for **Minecraft 1.21.1** on **NeoForge**.

[Features](#features) · [Compatibility](#compatibility--requirements) · [Usage](#usage) · [Configuration](#configuration) · [Building](#building-from-source) · [中文说明](#中文说明)

---

## Compatibility / Requirements

| Item | Version |
| --- | --- |
| Minecraft | **1.21.1** |
| Mod loader | **NeoForge 21.1.265** or any newer 21.1.x |
| Java | **21** |
| Environment | **Client + server** (see [Multiplayer](#multiplayer)) |
| Build tooling | Gradle 8.10.2, ModDevGradle 2.0.148, Parchment mappings 2024.11.17 |

- Mod id: `aliyastoolkit`
- Namespace: `aliyastoolkit:`
- Tested on: Minecraft 1.21.1 / NeoForge 21.1.265 (single player + integrated server)

## Features

### 1. Swap the hotbar with the last inventory row
Press the swap key (default **X**) to exchange the nine hotbar slots with the last row of the main inventory (slots 27-35), without opening the inventory.

### 2. Automatic refill from the inventory
When an item in the hotbar disappears — dropped, used up (e.g. the last block was placed), a tool broke, or any other reason — and the inventory still contains the same item, that stack is moved back into the empty slot immediately.

- Only items that are in the player's **whitelist** are refilled.
- Not triggered while a GUI is open (so reorganising the inventory never fights with the feature).
- Not triggered right after using the hotbar swap.

### 3. Chain mining
Hold or toggle the chain key (default **Z**) and break a block: every connected block of the same type around it is broken too.

- The starting block is the one under the crosshair (normal block interaction/attack reach).
- From there the game looks at the 3×3×3 area around it, then continues from every matching block it found (26-way connectivity), until nothing matches or the **chain limit** (default 64, range 1-256) is reached.
- If more than the limit is found, the blocks closest to the starting block are kept.
- Blocks that generate in huge amounts (stone, cobblestone, granite, netherrack, dirt, ... — see the `aliyastoolkit:chain_vertical_only` block tag) use a **preset** instead: a cross-section (up/down/left/right, each 0-4) that is extruded forward by a length (1-8). For example up 2, down 2, left 0, right 1 gives a 2×5 cross-section. With the defaults (all 0, length 1) only the targeted block is broken. The shape is exact, so the chain limit does not truncate it.
- **Allow other blocks** (default ON): inside that shape, blocks that are *not* identical to the targeted block are broken as well. Unbreakable blocks (bedrock, barrier, ...) and fluids (water, lava, ...) are always skipped. When OFF, only blocks identical to the targeted block are broken.
- **Preset switch key** (default **Alt + Z**, configurable): hold it and scroll the mouse wheel — down switches to the next preset, up to the previous one (wrapping). The preset number and its parameters are shown as a vanilla action-bar message, which fades out automatically.
- **Lock direction** (default OFF): ON always extrudes along the horizontal facing (even when looking up/down); OFF follows the look direction, so you can also mine straight up or down. The cross-section's "left/right/up/down" are relative to the player.
- **Sneaking**: while sneaking the extrusion follows your look direction and may also go **45° diagonally up/down**, using the same forward-facing cross-section. This is independent of the lock direction; with lock direction OFF, straight up/down remains available as well.
- While the chain key is active, the **outer surface of the chained blocks** is outlined. Only contour lines are drawn (edges where two faces are flat/180° and edges inside the chain are skipped, so only 90° / 270° corners show up), and the color tells you the depth: **bright white where the line is directly visible, dimmer white for parts hidden behind blocks** — so you can tell at a glance what is in front and what is behind.
- The block under the crosshair is highlighted with a **thicker pure-white frame**: while chain mining is active the vanilla black selection box is replaced (otherwise that black line is drawn on top of our outline and makes the targeted block look dark).
- All drops (and experience) are collected at the position of the first block and merged into as few stacks as possible (each stack follows the item's normal max stack size). Every chained block costs tool durability exactly like mining it by hand.
- Without the chain key active nothing changes; with the feature disabled in the mod config nothing changes either.

### 4. Aliya's Toolkit settings screen
Press **Alt + X** (configurable) to open the screen:

- **Left side** (automatic refill): the *Exact Item Match* toggle, followed by the player's 3×3 **item whitelist**.
  Pick up an item from the inventory, **left click** a whitelist cell to store that item there (the item on the cursor is not consumed — put it back afterwards). **Right click** a cell to clear it. Everything is saved automatically.
- **Right side** (chain mining), grouped by feature:
  - Trigger mode (**Hold** / **Toggle**, default Toggle) and chain limit (1-256, default 64; hold Shift for steps of 16);
  - *Massive blocks*: a **preset selector** (`<` / `>` / name), **New** (copies the current preset) and **Del** (at least one preset always remains), plus the preset contents: up/down/left/right (0-4), length (1-8) and the lock-direction toggle. Left click a `+`/`-` style field to increase it, right click to decrease it.
- Close button in the top-right corner.

The whitelist and the settings (including all presets) are attached to the **player**, so in multiplayer every player has their own values, they are stored with the player data and they survive death/respawn.

### 5. On-screen hints
- Switching presets with the preset key shows the preset number and its shape/length/lock values in the **action bar** (vanilla text overlay), which fades out automatically.
- Switching the selected hotbar slot uses vanilla's built-in **selected item name** display; the mod does not add a second prompt.


## Usage

| Action | Default key | Change it in |
| --- | --- | --- |
| Swap hotbar ↔ last inventory row | `X` | Options → Controls → Key Binds → *Aliya's Toolkit* |
| Chain mining (hold or toggle) | `Z` | same place |
| Switch massive-block preset | Hold `Alt + Z` and scroll (down = next, up = previous) | same place |
| Open the Aliya's Toolkit settings screen | `Alt + X` | same place |

All keys only work in-game (not while a screen is open).

## Configuration

Open **Mods → Aliya's Toolkit → Config**, or edit `config/aliyastoolkit-common.toml`. Only the feature switches live here (they are global); every fine-grained setting is per player and is changed in the in-game **Aliya's Toolkit settings screen**.

| Option | Default | Description |
| --- | --- | --- |
| `enableHotbarSwap` | `true` | Enable the hotbar swap key. |
| `enableAutoRefill` | `true` | Enable automatic refill of whitelisted items. |
| `enableChainMining` | `true` | Enable chain mining. |

Per-player settings (in the in-game screen):

| Setting | Default | Description |
| --- | --- | --- |
| Exact Item Match | `ON` | `ON`: items must be identical (item + all data components such as enchantments and custom names) to count as the same item. Durability is ignored, so a broken pickaxe can be replaced by a fresh one. `OFF`: only the item type is compared. |
| Trigger Mode | `Toggle` | `Hold`: chain only while the chain key is held. `Toggle`: press once to enable, press again to disable. |
| Chain Limit | `64` | Maximum number of blocks broken by one connected chain (1-256, including the targeted block). Does not apply to the exact "massive blocks" shape. |
| Presets | 1 preset: up/down/left/right `0`, length `1`, lock direction `OFF` | Up to 16 presets for the "massive blocks" shape. Left click a value to increase it, right click to decrease it. |
| Shape Up/Down/Left/Right | `0` (range 0-4) | Cross-section extension relative to the targeted block and the player's facing. |
| Length | `1` (range 1-8) | How many layers the cross-section is extruded forward. |
| Lock Direction | `OFF` | `ON`: horizontally forward normally, 45° up/down while sneaking. `OFF`: straight up/down is also possible normally; while sneaking all directions (forward / 45° up / 45° down / straight up / straight down) are available. |
| Allow Other Blocks | `ON` | Inside the massive-block shape, other breakable blocks are broken too. Unbreakable blocks and fluids are always skipped. |


## Multiplayer

Inventory changes are always performed **on the server**; the client only sends a request. Therefore the mod has to be installed on **both the client and the server** for the features to work.

Network channels are registered as *optional*: a client with the mod can still join a server without it (and vice versa) — the keys simply do nothing, the connection is never refused.

## Building from source

Requires **JDK 21** (the Gradle wrapper is included).

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat build

# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

- Output jar: `build/libs/aliyastoolkit-<version>.jar`
- Run the development client: `gradlew runClient`
- Run the development server: `gradlew runServer`
- Data generation: `gradlew runData`

## License

All Rights Reserved (see `gradle.properties`, `mod_license`).

The project was bootstrapped from the [NeoForge MDK](https://github.com/NeoForged/MDK) template; the template files themselves are covered by `TEMPLATE_LICENSE.txt` (MIT).

---

## 中文说明

**Aliya's Toolkit** 是一个面向快捷栏/背包的轻量工具模组，适用于 **Minecraft 1.21.1 + NeoForge**。

### 适用范围

| 项目 | 版本 |
| --- | --- |
| Minecraft | **1.21.1** |
| 模组加载器 | **NeoForge 21.1.265** 及以上（21.1.x） |
| Java | **21** |
| 安装位置 | **客户端 + 服务端**（见下方说明） |

### 功能

1. **快捷栏与背包最后一行交换** —— 按 `X`（默认，可改键），把快捷栏 9 格与背包最后一排（槽位 27-35）互换，不用打开背包。
2. **自动补充** —— 快捷栏里的物品因丢弃、用光（如放下最后一个方块）、工具耐久归零等原因消失时，如果背包里有**同样的白名单物品**，会立刻把那一堆移回该格。
   - 只对**白名单**里的物品生效；
   - 打开任何界面（背包/箱子/聊天…）时不会触发；
   - 使用「快捷栏交换」后不会误触发。
3. **连锁破坏** —— 按住/切换 `Z`（默认，可改键）后挖方块，会把周围相连的同种方块一起破坏。
   - 以准星指向的方块为起点（距离和原版挖掘/交互距离一致）；
   - 先在它周围的 3×3×3 范围内找相同方块，再以找到的方块继续找，直到找不到或达到**连锁数量**（默认 64，范围 1-256）；超出时优先保留离起点近的；
   - 石头、圆石、花岗岩、下界岩、泥土这类大量生成的方块（见 `aliyastoolkit:chain_vertical_only` 方块标签）改用**预设**：横截面（上/下/左/右，各 0-4）沿朝前方向延伸若干层（长度 1-8）。例如 上2 下2 左0 右1 得到 2×5 的横截面；默认全 0、长度 1 时只挖指向的那一格。形状是精确范围，不受“连锁数量”截断；
   - **允许其它方块**（默认开）：在这个形状范围里，除了与指向方块相同的方块，其它可破坏的方块也会一并尝试破坏；不可破坏的方块（基岩、屏障等）和流体（水、岩浆等）始终跳过。关掉则只破坏与指向方块相同的方块；
   - **切换预设快捷键**（默认 **Alt + Z**，可改键）：按住它滚动鼠标滚轮，向下滚切换到下一个预设、向上滚切换到上一个（循环）；切换后用**原版动作栏文字**显示预设编号和参数，随后自动淡出；
   - **锁定方向**（默认关）：开——始终沿水平朝向延伸（抬头/低头也一样）；关——跟随视线，可以朝上/朝下延伸。横截面的上下左右以玩家朝向为基准；
   - **潜行**时跟随视线，还可朝**正斜向上 / 正斜向下（45°）**延伸，横截面仍是“朝向前方”时的形状，不受锁定方向影响；锁定方向关时还会额外允许朝正上/正下；
   - 连锁激活时，只给连锁方块的**外表面轮廓**画描边（隔着方块也能看到）：只画 90°/270° 的转角线，两个面平直相接（180°，例如并排的两个方块之间）以及连锁区域内部的棱都不画；
   - 描边颜色区分前后：**直接看得见的部分是白色，被方块挡住的部分是暗一点的白色**，一眼就能看出哪条线在前面、哪条在后面；
   - 指向的方块用**更粗的纯白框**标出：连锁激活期间会替换原版的黑框（原版黑框在描边之后绘制，会盖住白线让指向方块显得发暗）；
   - 掉落物都收集到最开始那个方块的位置，并**合并成尽量少的堆**（单堆上限遵循游戏规定的最大堆叠数量），经验也集中在同一处；每个方块都会像手动挖一样正常扣耐久；
   - 不按快捷键时完全不影响原版玩法；模组配置里关掉连锁破坏后也不会触发。
4. **Aliya's Toolkit 配置界面** —— 按 `Alt + X`（可改键）打开：
   - **左侧（自动补充）**：`完全一致` 开关在物品白名单上方；下方是 3×3 物品白名单。从背包拿起物品 → **左键**点白名单格：把该物品记入这一格（手上的物品不会消耗，可放回原处）；**右键**点白名单格：清空并自动保存；
   - **右侧（连锁破坏）**：触发方式、连锁数量；**大量方块**一组里有预设选择（`<` / `>` / 名称）、`新建`（复制当前预设）、`删除`（至少保留 1 个），以及当前预设的内容：上/下/左/右（0-4）、长度（1-8）、锁定方向。数值类按钮**左键 +1、右键 -1**；
   - 右上角有关闭按钮。
   - 白名单和设置（含所有预设）都**绑定在玩家身上**：多人游戏每人一份，随玩家数据保存，死亡重生后保留。

### 屏幕提示

- 用预设快捷键切换预设时，用**原版动作栏文字**显示预设编号 + 形状/长度/锁定方向，随后自动淡出；
- 切换快捷栏物品时直接使用**原版自带的选中物品名称**提示，本模组不再额外画提示。

### 使用

| 操作 | 默认按键 | 修改位置 |
| --- | --- | --- |
| 快捷栏 ↔ 背包最后一行交换 | `X` | 选项 → 控制 → 按键绑定 → *Aliya's Toolkit* |
| 连锁破坏（按住/切换） | `Z` | 同上 |
| 切换大量方块预设 | 按住 `Alt + Z` 滚动滚轮（下滚下一个，上滚上一个） | 同上 |
| 打开 Aliya's Toolkit 配置界面 | `Alt + X` | 同上 |

所有按键只在游戏内生效（打开界面时不会触发）。

### 配置

「模组列表 → Aliya's Toolkit → 配置」或 `config/aliyastoolkit-common.toml`。这里**只保留功能开关**（全局）；具体参数（连锁触发方式、连锁数量、物品完全一致等）都是按玩家保存的，在游戏内的 **Aliya's Toolkit 配置界面**里修改。

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `enableHotbarSwap` | `true` | 启用快捷栏交换快捷键 |
| `enableAutoRefill` | `true` | 启用自动补充 |
| `enableChainMining` | `true` | 启用连锁破坏 |

玩家设置（游戏内界面）：

| 设置 | 默认 | 说明 |
| --- | --- | --- |
| 物品完全一致 | 开 | 开启：物品类型 + 全部数据组件（附魔、自定义名称等）都要一致；关闭：只比较物品类型。**耐久值始终不参与比较**，这样镐子用坏后能用一把新镐补上。 |
| 触发方式 | 切换 | 按住：按住连锁快捷键时连锁；切换：按一下开启，再按一下关闭。 |
| 连锁数量 | 64 | 一次连通连锁最多破坏的方块数（1-256，包含指向的那个方块）；“大量方块”的精确形状不受它截断。 |
| 预设 | 1 个：上下左右 `0`、长度 `1`、锁定方向关 | “大量方块”形状最多可保存 16 个预设；数值左键 +1、右键 -1。 |
| 形状 上/下/左/右 | `0`（0-4） | 以指向的方块为基准、以玩家朝向描述的横截面延伸格数。 |
| 长度 | `1`（1-8） | 横截面沿朝前方向延伸的层数。 |
| 锁定方向 | 关 | 开：平时水平朝前，潜行时可朝前/正斜上/正斜下；关：平时也可朝正上/正下，潜行时前/正斜上/正斜下/正上/正下都可选。 |
| 允许其它方块 | 开 | 大量方块的形状范围里，其它可破坏的方块也一并破坏；不可破坏的方块和流体始终跳过。 |


### 多人游戏

所有物品栏改动都在**服务端**执行，客户端只负责发请求，因此**客户端和服务端都需要安装**本模组。网络通道是"可选"的：装了模组的客户端可以正常进入没装模组的服务器（此时按键无效果，不会掉线）。

### 构建

需要 **JDK 21**：在项目根目录执行 `gradlew build`，产物在 `build/libs/`。
