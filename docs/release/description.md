# Aliya's Toolkit

[English](#aliyas-toolkit) | [中文](#中文说明)

**Aliya's Toolkit** is a quality-of-life mod for Minecraft 1.21.1 on NeoForge. It brings hotbar management, chain mining, quick building, and blueprint copying together in one toolkit. The goal is simple: spend less time opening menus and repeating the same clicks, and more time playing.

## What you can do

- Swap your hotbar with a chosen inventory row without opening the inventory.
- Keep important hotbar items stocked automatically.
- Break connected blocks in one go, including large-block presets.
- Build lines, rectangles, circles, spheres, and other shapes from two clicks.
- Record a structure into a blueprint box, preview it as a ghost, and build it somewhere else.
- Manage everything from one in-game settings screen.

## Requirements

| Item | Requirement |
| --- | --- |
| Minecraft | **1.21.1** |
| Mod loader | **NeoForge 21.1.265 or newer** (21.1.x) |
| Java | **21** |
| Installation | **Client and server** for multiplayer; singleplayer needs the client only |
| Other mods | None required |
| Current version | **1.21.1-1.1.1** |

## First steps

1. Install NeoForge for Minecraft 1.21.1.
2. Place the mod file in your `mods` folder. For multiplayer, install it on both the client and the server.
3. Launch the game and enter a world.
4. Press **Alt + X** to open **Aliya's Toolkit Settings**.
5. Use the five tabs to set up your item whitelist, chain-mining presets, quick-build presets, and blueprint options.

All keys work only while you are in-game and can be changed in **Options → Controls → Key Binds → Aliya's Toolkit**.

## Hotbar and inventory

### Swap the hotbar with an inventory row

Press **X** to exchange the nine hotbar slots with a chosen row of your inventory. The default row is the bottom inventory row, and you can choose another row in the settings screen. You do not need to open the inventory, and the swap does not trigger automatic refill.

### Automatic refill

When a whitelisted item disappears from your hotbar — because it was used up, dropped, or a tool broke — the mod moves a matching stack from your inventory back into the same hotbar slot.

- The whitelist has nine slots. Open **Alt + X → Automatic Refill**, left-click with an item to add it, or right-click to clear a slot.
- **Exact Match** can require the same item type and the same item data, such as enchantments and custom names. Durability is always ignored, so a new pickaxe can replace a broken one.
- Refill only works while you are playing and no inventory, container, or chat screen is open.

## Chain mining

Tap **Z** to toggle chain mining. The toggle is applied when you release the key. While it is active, breaking a block also breaks connected blocks of the same type.

- **Chain Limit** controls how many blocks can be broken at once. The default is 64; the range is 1–256.
- **Massive Blocks** presets cover common materials such as stone, dirt, and netherrack. Each preset defines a cross-section and a number of layers in the direction you are facing.
- **Allow Other Blocks** lets the same shape break other breakable blocks in the area. Turn it off to break only the block type you started with.
- Drops are gathered at the origin and merged where possible, so the ground does not fill with scattered items.
- Hold **Z** and scroll to change the massive-block preset.

## Quick build

Tap **C** to toggle quick build. The toggle is applied when you release the key. Hold **C** and scroll to change the current preset. Before the first point is selected, a green target outline follows your crosshair so you can see that quick build is active.

1. Right-click the first corner to set the start point. A preview shows the planned shape and the number of blocks needed.
2. Right-click a second time to set the end point. The mod checks the block in your main hand and the available materials.
3. In survival mode, blocks are taken from your inventory and hotbar. In creative mode, no materials are used.

Quick build supports point-to-point and center-to-point placement, one-, two-, and three-dimensional shapes, and line, rectangle, circle, and sphere shapes. Each can be solid, hollow, or a frame.

A single build can place up to **4096 blocks**, and the target can be up to **40 blocks** away. The shape is checked again when the build starts, so an invalid target simply does not build.

## Blueprint build

### Enter the blueprint mode

Hold a blueprint box in your main hand and press **Ctrl + right-click**.

- An **empty box** enters **draw mode**.
- A box that **already contains a blueprint** enters **build mode**.
- The entry prompt shows **Blueprint Build: Draw Mode** or **Blueprint Build: Build Mode**.
- In build mode, if the box has no building materials, press **Tab** to return to draw mode. Recording a new blueprint will replace the old one.
- A box that contains building materials stays in build mode until its items or blueprint are cleared.

### Draw a blueprint

Before the first point is selected, a pale-yellow target outline follows your crosshair so you can see that draw mode is active.

- Right-click two positions to **expand** the selection. The current selection is orange, and the new preview is pale yellow.
- Left-click two positions to **shrink** the selection. The preview is pale red.
- A later right-click selection can add back an area that was previously removed; selections are applied in operation order.
- You can add or remove several selections in a row.
- Hold **Shift** while choosing a position to snap it to the outer face of the block you are pointing at.
- Press **Ctrl + left-click** anywhere to record the structure and collect its materials (cut). The mod reads the area again, collects the required block items, container contents, and fluid buckets into the blueprint box, and removes the source blocks.
- Press **Ctrl + right-click** anywhere to record the blueprint only (copy). The source blocks stay in place, and no materials are put into the box.
- Blueprint boxes themselves are filtered out of the recording, so a blueprint cannot collect another blueprint box by accident.
- Container contents are recorded as part of the material cost. When the structure is built, those contents are restored.
- Both recording actions exit draw mode.

### Build a blueprint

- A ghost preview appears as soon as you enter build mode, even if you do not have enough materials.
- Scroll to move the ghost closer or farther away.
- Press **V** to rotate the ghost by 90 degrees around the vertical axis.
- Right-click to pin the ghost in place so you can walk around and inspect it; left-click to unpin it.
- While the ghost is pinned, press **Ctrl + right-click** to check materials and build.
- If the materials are sufficient, the required blocks, container contents, and buckets are taken from the blueprint box and the structure is built. If not, a message is shown and blueprint mode exits.
- The ghost is slightly easier to see and only draws visible block faces; the blueprint outline remains fully visible.

The ghost searches for a valid placement position within a radius of **8 blocks** around the indicator. Boxes, shulker boxes, beds, and other animated blocks are shown with their real appearance in the preview.

### Collect fluids automatically

This option is **on by default** and can be changed in **Alt + X → Blueprint Build**.

When recording, the mod finds fluid sources that can be picked up with a bucket, such as water and lava.

- In **cut mode** (**Ctrl + left-click**), it checks for empty buckets in your inventory, hotbar, and offhand. If enough are available, they are consumed and the filled buckets become part of the blueprint's material list. If not, the fluid source is skipped with a message, and the rest of the blueprint is still recorded.
- In **copy mode** (**Ctrl + right-click**), fluid sources are included in the blueprint without consuming empty buckets or adding filled buckets. Bring the required buckets when you build.

Turn the option off if you want fluid sources to be ignored completely.

## Blueprint boxes

Blueprint boxes are chest-like storage blocks that hold a blueprint and the materials needed to build it. They come in seven tiers:

| Blueprint box | Storage slots | How to obtain |
| --- | --- | --- |
| Blueprint Box | 27 (3×9) | Chest + 8 paper |
| Copper Blueprint Box | 45 (5×9) | Blueprint Box + 8 copper ingots |
| Iron Blueprint Box | 63 (7×9) | Copper Blueprint Box + 8 iron ingots |
| Gold Blueprint Box | 81 (9×9) | Iron Blueprint Box + 8 gold ingots |
| Obsidian Blueprint Box | 108 (12×9) | Gold Blueprint Box + 8 obsidian |
| Diamond Blueprint Box | 135 (15×9) | Obsidian Blueprint Box + 8 diamonds |
| Netherite Blueprint Box | 162 (18×9) | Smithing Table: Diamond Blueprint Box + netherite ingot + netherite upgrade template |

To open the blueprint box you are holding, press **Alt + right-click**. The same interface can be opened by right-clicking a placed blueprint box.

- The screen shows five rows at a time; use the scroll bar or mouse wheel to reach the rest.
- An empty box shows disabled slots. After a blueprint is recorded, ghost placeholders show which items are needed and how many.
- **Clear** returns the stored items and removes the blueprint. **Quick Take** moves the stored items to your inventory while keeping the blueprint. **Store All** moves matching items from your inventory into the box.
- Blueprint boxes work with hoppers and comparators like a chest. They always stay single and cannot be placed inside another blueprint box.
- All blueprint boxes float in water. Copper and higher tiers resist fire and lava; obsidian and higher tiers resist explosions; netherite drops also survive lava and are moved to a safe place if they fall into the void.
- Renaming a blueprint box in an anvil keeps its contents and blueprint. Breaking a placed box keeps its items, blueprint, and stored contents.

## Settings and multiplayer

Press **Alt + X** to open the settings screen. The five tabs are:

- **Item Switching** — choose which inventory row is swapped with the hotbar.
- **Automatic Refill** — turn Exact Match on or off and edit the nine-slot whitelist.
- **Chain Mining** — set the chain limit, massive-block presets, direction lock, and whether other blocks may be broken.
- **Quick Build** — choose, create, or delete presets and set the operation, dimension, shape, and fill.
- **Blueprint Build** — toggle automatic fluid collection and see the current blueprint controls. The key names in the hints follow your actual key bindings.

Your whitelist and settings are stored per player. They are saved with your player data and survive death and respawn.

For multiplayer, install the mod on **both the client and the server**. Each player keeps their own settings. Feature switches are also available in the mod's global configuration, so a server owner can disable individual tools.

## Default controls

| Action | Default key |
| --- | --- |
| Open Aliya's Toolkit settings | **Alt + X** |
| Swap hotbar with inventory row | **X** |
| Toggle chain mining | Tap **Z** |
| Change massive-block preset | Hold **Z** and scroll |
| Toggle quick build | Tap **C** |
| Change quick-build preset | Hold **C** and scroll |
| Open the blueprint box in your hand | **Alt + right-click** |
| Blueprint draw: record and collect (cut) | **Ctrl + left-click** |
| Blueprint build: enter, record copy, or build | **Ctrl + right-click** |
| Blueprint build: switch draw/build mode | **Tab** (box must have no items) |
| Blueprint draw: snap to a block face | Hold **Shift** |
| Blueprint build: rotate the ghost | **V** (before pinning) |
| Blueprint build: pin or unpin the ghost | Right-click / left-click |
| Exit chain mining / quick build / blueprint build | `` ` `` |
| Blueprint build: change ghost distance | Scroll |

All keys can be rebound in the controls menu. Chain mining, quick build, and blueprint build are mode-like features; only one of them can be active at a time.

## Notes

- This release is for **Minecraft 1.21.1** and **NeoForge 21.1.x**.
- The mod has been tested only in a **vanilla-like environment**: singleplayer worlds. Other mods, large modpacks, and dedicated multiplayer servers are not tested yet.
- Back up important worlds before using blueprint copying on a large build, and try the features in a test world first.
- Blueprint copying records blocks, containers and their contents, and bucketable fluid sources. Very large structures can require a large number of materials and a suitable open area.

## License

This mod is released under the **MIT License**. You are welcome to include it in modpacks.

## A note from the author

This mod was made with **1% inspiration and 99% AI**. It is a small personal toolkit shared in the hope that it makes building and mining a little smoother. If something behaves unexpectedly, please reproduce it in a minimal vanilla-like environment and include the steps in your report.

---

# 中文说明

[English](#aliyas-toolkit) | [中文](#中文说明)

**Aliya的工具包** 是一个面向 Minecraft 1.21.1 / NeoForge 的便利工具模组，把快捷栏管理、连锁破坏、快速建造和蓝图复制整合在一起。它想解决的问题很简单：少开几次背包，少做重复操作，把时间留给真正想玩的内容。

## 主要功能

- 不用打开背包，就能把快捷栏和指定的背包行交换。
- 白名单物品从快捷栏消失后，自动从背包补回。
- 一次破坏相连的同类方块，并可使用“大量方块”形状预设。
- 两次选点，快速建造直线、矩形、圆形、球形等形状。
- 用蓝图箱记录建筑，以虚影预览，并在别处重新建造。
- 通过一个游戏内设置界面管理所有功能。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | **1.21.1** |
| 模组加载器 | **NeoForge 21.1.265 或更新的 21.1.x** |
| Java | **21** |
| 安装位置 | 多人游戏需要**客户端和服务端**都安装；单人游戏只需客户端 |
| 其它模组 | 无需安装 |
| 当前版本 | **1.21.1-1.1.1** |

## 快速开始

1. 为 Minecraft 1.21.1 安装 NeoForge。
2. 把模组文件放入 `mods` 文件夹。多人游戏请同时在客户端和服务端安装。
3. 进入游戏并打开一个世界。
4. 按 **Alt + X** 打开 **Aliya的工具包 设置**。
5. 在左侧五个页面中设置物品白名单、连锁破坏预设、快速建造预设和蓝图选项。

所有快捷键只在游戏内生效，都可以在 **选项 → 控制 → 按键绑定 → Aliya's Toolkit** 中修改。

## 快捷栏与背包

### 快捷栏交换

按 **X**，把快捷栏的九个格子和指定的一行背包互换。默认交换最下面一行，可以在设置中改选其它行。交换不需要打开背包，也不会触发自动补充。

### 自动补充

白名单物品因为用光、丢弃或工具损坏而从快捷栏消失时，模组会把背包里的同类物品移回原来的快捷栏格。

- 白名单共有九个格子。打开 **Alt + X → 自动补充**，左键拿起物品设置，右键清空。
- **完全一致** 开启后，要求物品类型、附魔、自定义名称等数据一致；耐久值始终不参与比较，所以坏掉的镐可以用新镐补上。
- 打开背包、箱子或聊天等界面时不会触发自动补充。

## 连锁破坏

短按 **Z** 切换连锁破坏，松开按键时生效。开启后，挖掉一个方块会同时破坏周围相连的同类方块。

- **连锁数量** 控制一次最多破坏多少格，默认 64，范围 1–256。
- **大量方块** 预设适用于石头、泥土、下界岩等常见材料，可以设置横截面和沿朝向前方延伸的层数。
- **允许其它方块** 开启时，形状范围内的其它可破坏方块也会一起破坏；关闭后只破坏与起点相同的方块。
- 掉落物会集中到起点并尽量合并，不会散落一地。
- 按住 **Z** 滚动滚轮可以切换大量方块预设。

## 快速建造

短按 **C** 切换快速建造，松开按键时生效；按住 **C** 滚动滚轮可以切换预设。第一次选点前，目标格会跟随准星显示绿色轮廓，便于确认快速建造已开启。

1. 第一次右键设置起点，界面会显示规划轮廓和所需方块数量。
2. 第二次右键设置终点，模组会检查主手方块和可用材料。
3. 生存模式会消耗背包和快捷栏中的方块；创造模式不消耗材料。

快速建造支持点到点、中心到点，一维、二维、三维，以及直线、矩形、圆形、球形等形状；每种形状可以选择实心、空心或框架。

一次最多建造 **4096 个方块**，目标点最远 **40 格**。建造开始前会再次检查目标区域，位置不合法时不会强行建造。

## 蓝图建造

### 进入蓝图模式

主手拿着蓝图箱，按 **Ctrl + 右键**：

- **空蓝图箱** 进入 **绘制模式**。
- **已有蓝图** 进入 **建造模式**。
- 进入后原有提示会显示 **蓝图建造：绘制模式** 或 **蓝图建造：建造模式**。
- 建造模式下，如果箱子里没有材料，可以按 **Tab** 回到绘制模式；记录的新蓝图会覆盖旧蓝图。
- 箱子里有材料时只能留在建造模式，直到清空材料或蓝图。

### 绘制蓝图

第一次选点前，目标格会跟随准星显示淡黄色轮廓，便于确认当前处于绘制模式。

- 右键两个位置**扩选**，已确定区域显示为橙色，新增预览显示为浅黄色。
- 左键两个位置**缩选**，预览显示为浅红色。
- 之后右键扩选可以重新覆盖之前删除的区域；选区按操作顺序生效。
- 可以连续多次添加或删除选区。
- 选点时按住 **Shift**，可以把点吸附到指向方块的外侧面。
- 按 **Ctrl + 左键** 记录结构并收取材料（剪切）：模组会重新读取区域，把方块物品、容器内容和流体桶收进蓝图箱，并清除源方块。
- 按 **Ctrl + 右键** 只记录蓝图（复制）：源方块保持不变，也不会向蓝图箱放入材料。
- 蓝图箱本身会被过滤，避免把另一个蓝图箱错误地收进蓝图。
- 容器里的内容会作为材料需求记录，建造后会放回原来的容器和槽位。
- 两种记录方式都会退出绘制模式。

### 建造蓝图

- 进入建造模式后会立即显示虚影，即使材料不足也会显示。
- 滚动滚轮可以调整虚影远近。
- 按 **V** 让虚影绕竖直轴旋转 90°。
- 右键固定虚影，方便走近查看；左键解除固定。
- 固定后按 **Ctrl + 右键** 检查材料并建造。
- 材料足够时，会从蓝图箱扣除方块、容器内容和流体桶，然后开始建造；材料不足时会提示并退出蓝图模式。
- 虚影透明度略微提高，只绘制玩家可见的方块面；蓝图轮廓线仍然正常显示。

虚影会在指示器周围半径 **8 格**的范围内寻找合法位置。箱子、潜影盒、床等方块在预览中会显示真实外观。

### 自动收集流体

默认开启，可在 **Alt + X → 蓝图建造** 中关闭。

记录蓝图时，模组会识别可以用桶接取的流体源头，例如水和岩浆。

- **剪切模式**（**Ctrl + 左键**）：检查背包、快捷栏和副手是否有足够的空桶。空桶足够时消耗空桶，把装满的桶作为蓝图材料放进蓝图箱；空桶不足时提示并跳过该流体源头，但其它方块仍然会被记录。
- **复制模式**（**Ctrl + 右键**）：把流体源头记录进蓝图，但不消耗空桶，也不放入装满的桶；建造时需要自行准备对应的桶。

关闭该选项后，流体源头不会参与蓝图。

## 蓝图箱

蓝图箱是类似箱子的储物方块，用来保存蓝图和建造材料。共有七个等级：

| 蓝图箱 | 容量 | 获取方式 |
| --- | --- | --- |
| 蓝图箱 | 27（3×9） | 箱子 + 8 张纸 |
| 铜制蓝图箱 | 45（5×9） | 蓝图箱 + 8 个铜锭 |
| 铁制蓝图箱 | 63（7×9） | 铜制蓝图箱 + 8 个铁锭 |
| 金制蓝图箱 | 81（9×9） | 铁制蓝图箱 + 8 个金锭 |
| 黑曜石蓝图箱 | 108（12×9） | 金制蓝图箱 + 8 个黑曜石 |
| 钻石蓝图箱 | 135（15×9） | 黑曜石蓝图箱 + 8 个钻石 |
| 下界合金蓝图箱 | 162（18×9） | 锻造台：钻石蓝图箱 + 下界合金锭 + 下界合金升级模板 |

主手拿着蓝图箱按 **Alt + 右键** 可以打开界面；右键放在世界里的蓝图箱也可以打开同样的界面。

- 界面一次显示五行，使用右侧滚动条或滚轮查看剩余槽位。
- 没有蓝图时槽位是禁用的；记录蓝图后会显示半透明占位和每格需要的数量。
- **清空蓝图** 会把物品放回背包并移除蓝图；**一键取出** 会取出物品但保留蓝图；**一键存入** 会把背包中符合条件的物品放进箱子。
- 蓝图箱可以像普通箱子一样配合漏斗和比较器使用；始终是单箱，不能放进另一个蓝图箱。
- 所有蓝图箱在水中都会上浮。铜制及以上防火、防岩浆；黑曜石及以上防爆炸；下界合金掉落物还能在岩浆中保留，并从虚空回到附近安全位置。
- 铁砧改名会保留内容和蓝图；破坏已放置的蓝图箱也会保留物品、蓝图和容器内容。

## 设置与多人游戏

按 **Alt + X** 打开设置界面，左侧有五个页面：

- **物品切换**：选择与快捷栏交换的背包行。
- **自动补充**：开关“完全一致”，编辑九格白名单。
- **连锁破坏**：设置连锁数量、大量方块预设、方向锁定和是否允许其它方块。
- **快速建造**：选择、新建或删除预设，设置操作、维度、形状和填充方式。
- **蓝图建造**：开关自动收集流体，并查看绘制、建造、缩放、旋转、打开箱子等操作提示；提示中的按键会跟随你的实际按键绑定。

白名单和所有设置都按玩家保存，死亡和重生后仍然保留。

多人游戏中，客户端和服务端都需要安装本模组，每位玩家的设置互相独立。服务器主人也可以通过全局配置单独关闭某个功能。

## 默认按键

| 操作 | 默认按键 |
| --- | --- |
| 打开 Aliya的工具包 设置 | **Alt + X** |
| 快捷栏与背包行交换 | **X** |
| 开关连锁破坏 | 短按 **Z** |
| 切换大量方块预设 | 按住 **Z** 滚动滚轮 |
| 开关快速建造 | 短按 **C** |
| 切换快速建造预设 | 按住 **C** 滚动滚轮 |
| 打开主手蓝图箱 | **Alt + 右键** |
| 蓝图绘制：记录并收取（剪切） | **Ctrl + 左键** |
| 蓝图建造：进入、记录复制或建造 | **Ctrl + 右键** |
| 蓝图建造：切换绘制/建造模式 | **Tab**（箱子必须没有物品） |
| 蓝图绘制：吸附方块外侧面 | 按住 **Shift** |
| 蓝图建造：旋转虚影 | **V**（固定之前） |
| 蓝图建造：固定/解除固定虚影 | 右键 / 左键 |
| 退出连锁破坏 / 快速建造 / 蓝图建造 | `` ` `` |
| 蓝图建造：调整虚影距离 | 滚轮 |

所有按键都可以在控制菜单中修改。连锁破坏、快速建造和蓝图建造属于模式类功能，同一时间只能开启一个。

## 注意事项

- 本版本面向 **Minecraft 1.21.1** 和 **NeoForge 21.1.x**。
- 目前只在**接近原版的环境**中测试：单人游戏。其它模组、大型整合包和专用多人服务器尚未测试。
- 在重要建筑上使用蓝图复制前，请先备份世界，并建议先在测试世界中试用。
- 蓝图会记录方块、箱子等容器及其内容，以及可用桶收集的流体源头。大型建筑可能需要大量材料，也需要足够开阔的建造空间。

## 许可

本模组使用 **MIT License**，欢迎放入整合包。

## 作者说明

本模组由 **1% 灵感 + 99% AI** 制作，是一个希望让建造和挖矿轻松一点的小工具。如果遇到异常，请尽量在接近原版的最小环境中复现，并在反馈中写清操作步骤。




