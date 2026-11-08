# Aliya's Toolkit

**Aliya's Toolkit** 是一个面向快捷栏 / 背包的轻量工具模组，适用于 **Minecraft 1.21.1 + NeoForge**。

## 适用范围

| 项目 | 版本 |
| --- | --- |
| Minecraft | **1.21.1** |
| 模组加载器 | **NeoForge 21.1.265** 及以上（21.1.x） |
| Java | **21** |
| 安装位置 | **客户端 + 服务端**（见「多人游戏」） |
| 构建工具 | Gradle 8.10.2 · ModDevGradle 2.0.148 · Parchment 2024.11.17 |

- 模组 id：`aliyastoolkit`
- 命名空间：`aliyastoolkit:`
- 测试环境：Minecraft 1.21.1 / NeoForge 21.1.265（单人游戏 + 内置服务器）

## 功能

### 1. 快捷栏与背包指定行交换

- 按 `X`（默认，可在按键绑定里改），把快捷栏 9 格与背包里**「交换行」选中的那一行**（1 = 最上面一行，3 = 最后一行；默认 3，槽位 27-35）两两互换，不用打开背包。
- 交换在服务端完成；交换会让一些格子变空，但**不会触发自动补充**。

### 2. 自动补充

- 快捷栏里的物品因丢弃、用光（如放下最后一个方块）、工具耐久归零等原因消失时，如果背包里有**白名单里的相同物品**，会立刻把那一堆移回该格。
- 只对**白名单**里的物品生效。
- 打开任何界面（背包 / 箱子 / 聊天…）时不会触发。
- 使用「快捷栏交换」后不会误触发。
- **完全一致**：开启时物品类型 + 全部数据组件（附魔、自定义名称等）都要一致；关闭时只比较物品类型。**耐久值始终不参与比较**，所以坏掉的镐子可以用一把新镐补上。

### 3. 连锁破坏

- 短按 `Z`（默认，可改键）开关连锁：**松开按键时生效**；开启后挖方块会把周围相连的同种方块一起破坏；再短按一次关闭。
- **和快速建造等“模式类”功能互斥**：连锁破坏、快速建造以及以后新增的同类功能**同一时间最多开启一个**；开启其中一个会自动关闭另一个。快捷栏交换、自动补充不受影响。
- 以准星指向的方块为起点（距离和原版挖掘 / 交互距离一致）；先在它周围的 3×3×3 范围内找相同方块，再以找到的方块继续找，直到找不到或达到**连锁数量**（默认 64，范围 1-256，包含指向的那个方块）；超出时优先保留离起点近的。
- **大量方块**（石头、圆石、花岗岩、下界岩、泥土等，见 `aliyastoolkit:chain_vertical_only` 方块标签）改用**预设**：横截面（上 / 下 / 左 / 右，各 0-4）+ **层数**（1-8）沿朝前方向延伸。例如上 2 下 2 左 0 右 1、层数 3，会挖出 2×5×3 的范围；默认全 0、层数 1 时只挖指向的那一格。形状是精确范围，不受「连锁数量」截断。
- **允许其它方块**（默认开）：在这个形状范围里，除与指向方块相同的方块外，其它可破坏的方块也会一并尝试破坏；不可破坏的方块（基岩、屏障等）和液体方块（水、岩浆等）始终跳过，**预览描边里也不会出现它们**。关掉则只破坏与指向方块相同的方块。
- **切换预设**（需要先开启连锁破坏）：按住 `Z` 滚动鼠标滚轮（下滚下一个，上滚上一个）；到两端停住，**不会循环**。按住期间滚轮只用于切预设，松开 Z 不会切换连锁开关；切换后用**原版动作栏文字**显示预设名称、编号和参数，随后自动淡出。
- **锁定方向**（默认关）：开——始终沿水平朝向延伸（抬头 / 低头也一样）；关——跟随视线，可以朝上 / 朝下延伸。横截面的上下左右以玩家朝向为基准。
- **潜行**时跟随视线，还可朝**正斜向上 / 正斜向下（45°）**延伸，横截面仍是「朝向前方」时的形状，不受锁定方向影响；锁定方向关时还会额外允许正上 / 正下。
- 连锁激活时，只给连锁方块的**外表面轮廓**画描边（隔着方块也能看到）：只画 90° / 270° 的转角线，两个面平直相接（180°，例如并排的两个方块之间）以及连锁区域内部的棱都不画。
- 描边颜色区分前后：**直接看得见的部分是白色，被方块挡住的部分是暗一点的白色**，一眼就能看出哪条线在前面、哪条在后面。
- 指向的方块用**更粗的纯白框**标出：连锁激活期间会替换原版的黑框（原版黑框在描边之后绘制，会盖住白线让指向方块显得发暗）。
- 掉落物都收集到最开始那个方块的位置，并**合并成尽量少的堆**（单堆上限遵循物品的最大堆叠数量），经验也集中在同一处；每个方块都会像手动挖一样正常扣耐久。
- 不按快捷键时完全不影响原版玩法；模组配置里关掉连锁破坏后也不会触发。

### 4. 快速建造

- 短按 `C`（默认，可改键）开关快速建造（**松开按键时生效**）；按住 `C` 滚动鼠标滚轮切换建造预设（需要先开启快速建造；到两端停住，不会循环；松开不会切换开关）。
- **和连锁破坏等“模式类”功能互斥**：同一时间最多开启一个（以后新增的同类功能也一样）；快捷栏交换、自动补充不受影响。
- **右键第一次**确定第一个点：显示按预设规划的范围（**绿色轮廓**，深浅与连锁破坏同理：直接可见部分亮绿、被挡住部分暗绿）、从当前指示位置到实际规划位置的**辅助线**，动作栏还会持续显示**所需方块数量**与背包里已有的数量。
- **右键第二次**确定第二个点：检查主手是否为**可放置的方块**（所有建筑方块都允许：半砖、楼梯、栅栏、墙、玻璃板、地毯等）、数量（快捷栏 + 背包）是否足够（**创造模式不检查、也不消耗**）；不足会提示并取消规划点，足够则立刻建造；生存模式下**优先消耗背包里的方块**（先 9-35 格，再快捷栏 0-8 格）。
- 选点位置：优先取准星指向方块「靠近玩家的那一面」的空气格；该位置不可替换时退回方块本身（若可替换）；没有指向方块时（包括指向空中、超出触及距离的 MISS），取玩家身前约 2 格的位置。
- **朝向 / 状态**：第一次右键时按**原版放置规则**，把当时手持的方块“放在第一个点”上算出一个状态（楼梯的朝向与上下半、半砖的上下半、原木的朝向等），之后所有方块都用这个状态；之后再转动视角不会改变已记录的朝向（中途换成同种方块也一样）。放置完成后还会按原版的 `updateShape` 规则逐个重算连接（栅栏 / 墙 / 玻璃板 / 楼梯内外角 / 箱子合并等），所以不会把第一个点的连接照搬到旁边没有对应方块的位置。
- 建造预设包含 `操作`、`维度`、`形状`、`填充`，并带有约束：
  - 操作：`点到点`（两个点是对角的两个位置）/ `中心到点`（第一个点是中心，向两侧对称扩展）；
  - 维度：`一维` / `二维` / `三维`；
  - 形状：`直线` / `方形`（二维长方形、三维长方体）/ `圆形`（二维正圆、三维正球）；
  - 填充：`实心` / `空心` / `框架`（只有边）；
  - 约束：一维只能「直线 + 实心」；二维不能「框架」；三维圆形（球）不能「框架」。
- 吸附规则：
  - **一维直线**：第二个点在 6 个轴向 + 12 个 45° 正斜方向里，选和鼠标指向最接近的方向；正斜的判定范围很宽，稍微斜一点就能吸附到 45°；
  - **二维 / 三维「方形」**：允许长方形 / 长方体；两个点就是对角的两个位置（中心到点则是中心到顶点），各轴长度独立；
  - **二维「圆形」/ 三维「圆形」**：正圆 / 正球；第二个点吸附到「正方向位置」（只沿一个轴，中心到点则是中心到正方向）确定半径，再补齐成正方形 / 立方体取内切圆 / 球；
  - 距离相同（对称）时按玩家视角决定方向。
- 一次最多建造 4096 个方块，目标点离玩家最远 40 格；超出范围或不是可替换方块的位置会被跳过。
- 服务端会重新计算范围并校验距离、方块数量，避免客户端作弊。

### 5. 蓝图建造

- 短按 `V`（默认，可改键）开关蓝图建造；**和连锁破坏、快速建造等“模式类”功能互斥**：开启它会自动关闭其它模式，开启其它模式也会关闭它。旧的 `Ctrl + C` / `Ctrl + X` / `Ctrl + V` 快捷键已移除。
- **划定区域**：主手不能拿蓝图箱。右键一个方块记下第一个角，再右键另一个方块记下对角，两点围成的长方体就是一块要处理的区域；选好第一个点后会**实时预览**整个范围。可以重复多次，多块区域允许重叠（合并后一起去重处理）。左键取消正在划定的区域，或删除点击位置所在的已选区域。
- 已确定的选区用**黄色轮廓**，正在跟随准星的动态预览用**亮黄色轮廓**；描边规则与连锁破坏 / 快速建造一致（只画 90° / 270° 转角线，被挡住的部分暗一点）。动态预览时不再额外绘制第一个点的单格粗框。选不到方块时（指向空气、超出触及距离），取视线前方约 2 格的位置，和快速建造一致。
- **收进蓝图箱**：选区划好后，切换主手为**没有物品的蓝图箱**（可以已经存有蓝图，会被新蓝图覆盖），右键选区内的方块。客户端和服务端都会重新读取结构、校验容量；成功后选区里的方块消失，方块物品和容器内容全部收进箱子，并自动退出蓝图模式。
- **容量不足**：如果所需格子超过箱子容量，会提示「物品过多，请使用更高级的蓝图箱」，**不会退出蓝图模式**，可以换更大的箱子后再次右键。
- 一次最多处理 4096 个方块，操作位置离玩家最远 40 格；服务端会重新读取方块、校验距离和物品数量。记录时会保存完整结构：方块状态 + 方块实体数据 + 容器内容及其原槽位；旋转时方块朝向（楼梯、箱子、告示牌、原木等）也会一起旋转。

### 6. 蓝图箱

- 记录流程见上一节：`V` 选区 → 换成**没有物品的蓝图箱** → 右键选区收进箱子。记录会保存完整结构：方块状态 + 方块实体数据 + **容器里的物品及其原槽位**。
- **虚影预览建造**：手持带蓝图的蓝图箱，按 `Ctrl + 右键` 进入预览。虚影默认在视线前方 **8 格**；如果结构会碰到玩家，会自动推远到「刚好碰不到 + 2 格」。**滚轮**调整远近（向前拉远、向后拉近；预览期间不会切换快捷栏物品），**Tab** 绕 Y 轴顺时针旋转 90°，方块朝向会跟着旋转。箱子、潜影盒、床等 `ENTITYBLOCK_ANIMATED` 方块也会使用真实 BlockState 显示对应的方块模型虚影。虚影会检查所有目标方块：在指示器周围半径 **4 格**的球内寻找最近的、全部可替换且不会碰到玩家的位置；找不到时提示「附近没有能放下蓝图的位置」。
- **固定 / 建造**：第一次右键固定虚影，此时可以走近查看效果；左键取消固定，虚影重新跟随视角。固定后再次右键确认建造，按方块物品 + 容器内容精确消耗箱子里的材料；材料不足或距离过远不会开始。
- 等级与容量（都是 9 列）：
  - 蓝图箱：3×9（27），青金石色，木箱 + 8 纸；
  - 铜制蓝图箱：5×9（45），铜色，蓝图箱 + 8 铜锭；
  - 铁制蓝图箱：7×9（63），铁色，铜制 + 8 铁锭；
  - 金制蓝图箱：9×9（81），金色，铁制 + 8 金锭；
  - 黑曜石蓝图箱：12×9（108），哭泣的黑曜石色，金制 + 8 黑曜石；
  - 钻石蓝图箱：15×9（135），钻石色，黑曜石 + 8 钻石；
  - 下界合金蓝图箱：18×9（162），下界合金色，在**锻造台**用钻石蓝图箱 + 下界合金锭 + 下界合金升级模板升级。
- 方块直接继承原版 `ChestBlock` / `ChestBlockEntity`，并使用原版 `ChestRenderer`：因此有箱子的形状、朝向、比较器、开盖音效和**翻盖动画**；蓝图箱强制保持单箱，不会和相邻蓝图箱合并成双箱，也不允许把蓝图箱放进另一个蓝图箱。等级使用单独的原版箱子贴图，只把深色包边换成对应材质颜色，不给整个箱子染色，并保留原版黑色箱内壁。
- 界面最多显示 **5 行 × 9 列**，列与玩家背包列对齐；超过 5 行时右侧有**滚动条**，可用滚轮或拖动，滑块使用创造模式物品栏的样式，并配有原版槽位风格的滑槽。主面板直接使用原版 `generic_54.png` 箱子界面纹理，槽位、玩家背包和标题区域都由原版纹理绘制，右侧扩展区只保留滚动条和按钮，左右面板之间没有黑色竖缝。没有蓝图时所有格子使用**合成器同款禁用槽位覆盖层**；记录蓝图后显示**半透明幽灵占位**和每格需求量；需求数字缩小到槽位右上角内部，不会越出边框。占位物品只接受对应物品，并受每格上限限制。
- 槽位悬停会显示物品说明：空占位显示占位物品和需求量，已放入物品显示物品本身的主要说明；蓝图箱物品说明不再包含操作方式和“是否记录蓝图”的状态。
- 虚影只渲染正常放置时可见的面；相邻方块的隐藏面会被剔除，箱子等实体方块会先写深度再半透明绘制，避免看到内部贴图。
- 物品栏、快捷栏和掉落物都使用与世界中相同的箱子模型和等级贴图，不再使用旧的自定义物品模型。
- `一键存入` 会把背包里符合占位的物品放进箱子；`一键取出` 把物品放进背包并保留蓝图，背包满时多余物品留在箱内；`清空蓝图` 把物品放回背包并清空蓝图与占位，放不下的物品会掉落在玩家脚下。
- 与漏斗等自动化：箱子实现容器接口，漏斗可以像普通箱子一样抽取；插入会检查物品和每格上限。比较器按**已填数量 / 总需求数量**输出 0-15 红石强度。
- 掉落物保护：
  - 所有蓝图箱在水中向上漂浮；
  - 铜制及以上的**方块本体**防火 / 防岩浆（不会燃烧，也不会被岩浆破坏），但铜制、铁制、金制、黑曜石、钻石箱的**掉落物**仍可能被火 / 岩浆销毁；
  - 黑曜石及以上的**掉落物**不会被爆炸销毁；放置在世界里的黑曜石及以上蓝图箱方块也不会被爆炸破坏；
  - 只有下界合金的掉落物本身防火 / 防岩浆，并会在岩浆中漂浮；掉入虚空会传送到附近最近的可站立位置。
- 用铁砧改名会保留箱子内容 / 蓝图；破坏箱子会保留物品、蓝图和容器内容（创造模式也会生成掉落物）。
- 记录 / 建造时，容器内容物也作为材料成本：例如蓝图里有一个装着 3 个钻石的箱子，需要额外准备 3 个钻石；建造完成后会放回原容器的原槽位。
- 本模组物品有独立的创造模式分类「Aliya's Toolkit 物品」。

### 7. Aliya's Toolkit 配置界面

按 `Alt + X`（可改键）打开：

- **左侧**：5 个功能按钮（`物品切换` / `自动补充` / `连锁破坏` / `快速建造` / `蓝图建造`）。点哪个，右边就显示哪个功能的内容，默认显示第一个（物品切换）；当前功能的按钮会高亮。
- 左右两侧由一条接近贯穿整个界面的竖线隔开（底部留一点空白）：左边只有功能按钮，右边只有功能内容。
- **物品切换**：功能说明文字 + `交换行` 按钮（和快捷栏交换的背包行，1-3，默认 3；左键 +1、右键 -1）。快捷键可在「选项 → 控制 → 按键绑定」里修改。
- **自动补充**：`完全一致` 开关 + 3×3 物品白名单 + 玩家的背包 / 快捷栏（只在当前页面显示）。从背包拿起物品 → **左键**点白名单格：把该物品记入这一格（手上的物品不消耗，可放回原处）；**右键**点白名单格：清空并自动保存。
- **连锁破坏**：
  - `连锁数量`：左键 +1、右键 -1，按住 Shift 步进 16；
  - `大量方块`：预设选择（`<` / `>` / 名称）、`新建`（复制当前预设）、`删除`（至少保留 1 个）；切换行下面单独一行是**预设名称**（可直接编辑，不显示“预设名称”字样；默认「预设1」「预设2」……，新建时自动取下一个未占用的编号）；下面十字排列 `上` / `左` `层数` `右` / `下`（上 / 下 / 左 / 右 0-4、层数 1-8，左键 +1、右键 -1，Shift 步进 16）；`锁定方向`、`允许其它方块` 各占一整行；
  - 底部提示：按住连锁键滚动滚轮切换预设（不循环、不切换开关）。
- **快速建造**：
  - 预设选择（`<` / `>` / 名称）、`新建`、`删除`（至少保留 1 个，最多 16 个）；
  - 切换行下面单独一行是**预设名称**（同样可直接编辑，默认「预设1」「预设2」……）；
  - `操作` / `维度` / `形状` / `填充` 四个整行按钮：**左键下一个、右键上一个，循环**；鼠标悬停会显示当前可选的全部内容（当前项用 `[]` 标出）；只有一个可选项时按钮变灰。
- **蓝图建造**：功能说明 + 选区 / 收进蓝图箱 / 虚影预览 / 旋转 / 固定 / 建造的操作提示（显示的按键会跟随按键绑定变化）。
- 右上角有关闭按钮；白名单和设置（含所有预设）都**绑定在玩家身上**：多人游戏每人一份，随玩家数据保存，死亡重生后保留。

## 屏幕提示

- 按住 `Z` 滚动滚轮切换预设时，用**原版动作栏文字**显示预设名称 + 编号 + 形状 / 层数 / 锁定方向，随后自动淡出。
- 按住 `C` 滚动滚轮切换快速建造预设时，动作栏显示预设名称 + 编号 + 操作 / 维度 / 形状 / 填充。
- 快速建造选好第一个点后，动作栏持续显示「所需方块 / 背包里数量」，右键第二次即可开建。
- 连锁破坏的规划范围用白色描边、指向方块用更粗的白色框；快速建造的规划范围用绿色描边；蓝图建造的选区用黄色描边，虚影预览用蓝色描边（材料不足或位置不合法时变红）；固定后虚影保持在世界中，方便走近查看。
- 切换快捷栏物品时直接使用**原版自带的选中物品名称**提示，本模组不再额外画提示。


## 使用

| 操作 | 默认按键 | 修改位置 |
| --- | --- | --- |
| 快捷栏 ↔ 背包指定行交换 | `X` | 选项 → 控制 → 按键绑定 → *Aliya's Toolkit* |
| 连锁破坏开关 | 短按 `Z`（松开时生效） | 同上 |
| 切换大量方块预设 | 按住 `Z` 滚动滚轮（需先开启连锁破坏；下滚下一个，上滚上一个，不循环；不会切换开关） | 同上 |
| 快速建造开关 | 短按 `C`（松开时生效） | 同上 |
| 切换快速建造预设 | 按住 `C` 滚动滚轮（需先开启快速建造；不循环；不会切换开关） | 同上 |
| 蓝图建造开关 / 选区模式 | 短按 `V` | 同上 |
| 蓝图建造：把选区收进蓝图箱 | 换成无物品的蓝图箱后右键选区内方块 | 同上 |
| 蓝图建造：进入虚影预览 | 手持带蓝图的蓝图箱，`Ctrl + 右键` | 同上 |
| 蓝图建造：调整虚影距离 | 滚轮（预览期间不会切换物品） | 同上 |
| 蓝图建造：旋转虚影 | `Tab`（固定之前） | 同上 |
| 蓝图建造：固定 / 取消固定 | 右键 / 左键（虚影预览中） | 同上 |
| 蓝图建造：确认建造 | 固定后再次右键 | 同上 |
| 打开 Aliya's Toolkit 配置界面 | `Alt + X` | 同上 |

所有按键只在游戏内生效（打开界面时不会触发）。

## 配置

「模组列表 → Aliya's Toolkit → 配置」或 `config/aliyastoolkit-common.toml`。这里**只保留功能开关**（全局）；具体参数（连锁数量、预设、物品完全一致等）都是按玩家保存的，在游戏内的 **Aliya's Toolkit 配置界面**里修改。

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `enableHotbarSwap` | `true` | 启用快捷栏交换快捷键 |
| `enableAutoRefill` | `true` | 启用自动补充 |
| `enableChainMining` | `true` | 启用连锁破坏 |
| `enableQuickBuild` | `true` | 启用快速建造 |
| `enableCopyPaste` | `true` | 启用蓝图建造 |

玩家设置（游戏内界面）：

| 设置 | 默认 | 说明 |
| --- | --- | --- |
| 物品完全一致 | 开 | 开启：物品类型 + 全部数据组件（附魔、自定义名称等）都要一致；关闭：只比较物品类型。**耐久值始终不参与比较**。 |
| 交换行 | 3 | 与快捷栏交换的背包行：1 = 最上面一行，3 = 最后一行；左键 +1、右键 -1。 |
| 连锁数量 | 64 | 一次连通连锁最多破坏的方块数（1-256，包含指向的那个方块）；左键 +1、右键 -1，按住 Shift 步进 16。「大量方块」的精确形状不受它截断。 |
| 预设名称 | 预设1、预设2…… | 连锁破坏、快速建造的每个预设都有名称，切换行下面单独一行可直接编辑（不显示“预设名称”字样）。新建预设时自动取下一个未占用的「预设N」。 |
| 连锁预设 | 1 个：上下左右 `0`、层数 `1`、锁定方向关 | 「大量方块」形状最多可保存 16 个预设；数值左键 +1、右键 -1（按住 Shift 步进 16）；切换预设到两端不会循环。 |
| 形状 上 / 下 / 左 / 右 | `0`（0-4） | 以指向的方块为基准、以玩家朝向描述的横截面延伸格数。 |
| 层数 | `1`（1-8） | 横截面沿朝前方向延伸的层数。 |
| 锁定方向 | 关 | 开：平时水平朝前，潜行时可朝前 / 正斜上 / 正斜下；关：平时也可朝正上 / 正下，潜行时前 / 正斜上 / 正斜下 / 正上 / 正下都可选。 |
| 允许其它方块 | 开 | 大量方块的形状范围里，其它可破坏的方块也一并破坏；不可破坏的方块和液体方块始终跳过。 |
| 快速建造预设 | 1 个：点到点 · 二维 · 方形 · 实心 | 最多可保存 16 个预设；操作 / 维度 / 形状 / 填充用按钮切换（左键下一个、右键上一个，循环；悬停显示可选项）。 |

## 多人游戏

- 所有物品栏 / 方块的改动都在**服务端**执行，客户端只负责发请求，因此**客户端和服务端都需要安装**本模组。
- 网络通道是「可选」的：装了模组的客户端可以正常进入没装模组的服务器（此时按键无效果，不会掉线）；反之亦然。
- 玩家的白名单和各项设置随玩家数据保存，多人游戏每人一份，死亡重生后保留。

## 构建

需要 **JDK 21**（项目自带 Gradle wrapper）：

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat build

# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

- 产物：`build/libs/aliyastoolkit-<version>.jar`
- 开发客户端：`gradlew runClient`
- 开发服务端：`gradlew runServer`
- 数据生成：`gradlew runData`

## 许可

本项目使用 **MIT License**，完整文本见根目录的 [LICENCE](LICENCE)。

项目基于 [NeoForge MDK](https://github.com/NeoForged/MDK) 模板创建。

---

# English

**Aliya's Toolkit** is a compact quality-of-life mod for the hotbar and inventory, built for **Minecraft 1.21.1** on **NeoForge**.

## Requirements

| Item | Version |
| --- | --- |
| Minecraft | **1.21.1** |
| Mod loader | **NeoForge 21.1.265** or any newer 21.1.x |
| Java | **21** |
| Environment | **Client + server** (see [Multiplayer](#multiplayer)) |
| Build tooling | Gradle 8.10.2 · ModDevGradle 2.0.148 · Parchment 2024.11.17 |

- Mod id: `aliyastoolkit`
- Namespace: `aliyastoolkit:`
- Tested on: Minecraft 1.21.1 / NeoForge 21.1.265 (single player + integrated server)

## Features

### 1. Swap the hotbar with a chosen inventory row

- Press `X` (default, rebindable) to exchange the nine hotbar slots with the **inventory row selected by the `Swap Row` setting** (1 = top row, 3 = bottom row; default 3, slots 27-35), without opening the inventory.
- The swap runs on the server. It empties some slots but **does not trigger automatic refill**.

### 2. Automatic refill

- When an item in the hotbar disappears (dropped, used up such as placing the last block, a tool breaking, ...) and the inventory still contains the same **whitelisted** item, that stack is moved back into the empty slot immediately.
- Only items in the **whitelist** are refilled.
- Not triggered while a GUI is open (inventory / chest / chat, ...).
- Not triggered right after using the hotbar swap.
- **Exact match**: ON requires the item type plus all data components (enchantments, custom name, ...) to be identical; OFF compares only the item type. **Durability is always ignored**, so a broken pickaxe can be replaced by a fresh one.


### 3. Chain mining

- Tap `Z` (default, rebindable) to toggle chain mining — **it takes effect when the key is released**. Once enabled, breaking a block also breaks the connected blocks of the same type around it; tap the key again to turn it off.
- **Mutually exclusive with quick build and other "mode" features**: chain mining, quick build and any future mode-like feature can have **only one active at a time**; enabling one automatically disables the other. Hotbar swap and automatic refill are not affected.
- The starting block is the one under the crosshair (normal block interaction / attack reach). The game looks at the 3×3×3 area around it, then continues from every matching block it found, until nothing matches or the **chain limit** (default 64, range 1-256, including the targeted block) is reached. If more is found, the blocks closest to the starting block are kept.
- **Massive blocks** (stone, cobblestone, granite, netherrack, dirt, ... — see the `aliyastoolkit:chain_vertical_only` block tag) use a **preset** instead: a cross-section (up / down / left / right, each 0-4) plus **layers** (1-8) extruded forward. For example up 2, down 2, left 0, right 1 with 3 layers mines a 2×5×3 region; with the defaults (all 0, 1 layer) only the targeted block is broken. The shape is exact and is not truncated by the chain limit.
- **Allow other blocks** (default ON): inside that shape, blocks that are *not* identical to the targeted block are broken as well. Unbreakable blocks (bedrock, barrier, ...) and liquid blocks (water, lava, ...) are always skipped, and **they never show up in the preview outline either**. When OFF, only blocks identical to the targeted block are broken.
- **Switch preset** (chain mining must be enabled first): hold `Z` and scroll the mouse wheel (down = next, up = previous). It stops at both ends (**no wrapping**). While the key is held the wheel is only used for presets, and releasing Z does **not** toggle chain mining. The preset name, number and its parameters are shown as a vanilla action-bar message, which fades out automatically.
- **Lock direction** (default OFF): ON always extrudes along the horizontal facing (even when looking up / down); OFF follows the look direction, so you can also mine straight up or down. The cross-section's up / down / left / right are relative to the player.
- **Sneaking**: the extrusion follows your look direction and may also go **45° diagonally up / down**, using the same forward-facing cross-section. This is independent of the lock direction; with lock direction OFF, straight up / down is also available.
- While the chain key is active, the **outer surface of the chained blocks** is outlined (visible even through blocks). Only 90° / 270° corner lines are drawn: edges where two faces are flat (180°, e.g. between two side-by-side blocks) and edges inside the chain are skipped.
- The outline color tells you the depth: **bright white where the line is directly visible, dimmer white for parts hidden behind blocks**.
- The block under the crosshair is highlighted with a **thicker pure-white frame**: while chain mining is active it replaces the vanilla black selection box.
- All drops (and experience) are collected at the position of the first block and merged into as few stacks as possible (each stack follows the item's normal max stack size). Every chained block costs tool durability exactly like mining it by hand.
- Without the chain key active nothing changes; with the feature disabled in the mod config nothing changes either.


### 4. Quick build

- Tap `C` (default, rebindable) to toggle quick build (**takes effect on release**); hold `C` and scroll to switch build presets (quick build must be enabled first; stops at both ends, no wrapping; releasing the key does not toggle the mode).
- **Mutually exclusive with chain mining and other "mode" features**: only one can be active at a time (this also applies to future mode-like features); hotbar swap and automatic refill are not affected.
- **First right click** selects the first point: the planned region is shown as a **green outline** (same depth shading as chain mining: bright green where directly visible, dimmer green behind blocks), a **helper line** is drawn from the current crosshair position to the actual planned position, and the action bar keeps showing the **required block count** and how many you have.
- **Second right click** selects the second point: it checks that the main hand holds a **placeable block** (all building blocks are allowed: slabs, stairs, fences, walls, glass panes, carpets, ...) and that the inventory (hotbar + backpack) has enough of it (**creative mode neither checks nor consumes anything**). If not, you get a message and the plan is cancelled; otherwise the region is built immediately. In survival, items are **consumed from the backpack first** (slots 9-35, then hotbar 0-8).
- Point selection prefers the air block on the side of the targeted block that faces the player; if that position is not replaceable it falls back to the block itself (when replaceable); without a block target (including aiming at the sky or a MISS beyond reach) it uses a position about 2 blocks in front of the player.
- **Orientation / state**: when the first point is selected, the mod computes the state the currently held block would get if **placed at the first point using vanilla placement rules** (stair facing and half, slab top/bottom, log axis, ...). Every block in the build then uses that state; rotating afterwards does not change it (and switching to another stack of the same block does not either). After placement, every block's own connections are also recomputed with vanilla's `updateShape` rules (fences, walls, panes, stair inner/outer corners, double chests, ...), so the first point's connections are not copied to positions without matching neighbours.
- A build preset contains `Operation`, `Dimension`, `Shape` and `Fill`, with constraints:
  - Operation: `Point to point` (the two points are opposite corners) / `Center to point` (the first point is the center and the region grows symmetrically);
  - Dimension: `1D` / `2D` / `3D`;
  - Shape: `Line` / `Square` (2D rectangle, 3D cuboid) / `Circle` (2D circle, 3D sphere);
  - Fill: `Solid` / `Hollow` / `Frame` (edges only);
  - Constraints: 1D is line + solid only; 2D cannot use frame; a 3D circle (sphere) cannot use frame.
- Snapping rules:
  - **1D line**: the endpoint is the closest of the 6 axis directions and 12 45° diagonal directions to the mouse movement. The diagonal tolerance is very wide, so a slightly diagonal drag snaps to 45°.
  - **2D / 3D "Square"**: rectangles / cuboids are allowed; the two points are opposite corners (or center-to-corner in center mode), and each axis keeps its own length.
  - **2D "Circle" / 3D "Circle"**: a true circle / sphere. The second point snaps to an axis-aligned position (center-to-axis in center mode) to define the radius; the bounding box is then expanded into a square / cube and the inscribed circle / sphere is used.
  - Ties are resolved with the player's view direction.
- Up to 4096 blocks per build, and targets must be within 40 blocks of the player; positions that are out of range or not replaceable are skipped.
- The server re-computes the region and validates distance and block count, so the client cannot cheat.


### 5. Blueprint build

- Tap `V` (default, rebindable) to toggle blueprint build. It is **mutually exclusive with the other mode features** (chain mining, quick build): enabling one disables the others. The old `Ctrl + C` / `Ctrl + X` / `Ctrl + V` shortcuts have been removed.
- **Mark regions**: your main hand must not hold a blueprint box. Right click a block to set the first corner, then right click another block to set the opposite corner; the cuboid between them becomes one region, and the whole region is **previewed live** after the first point. Repeat as often as you like — regions may overlap (they are merged and de-duplicated). Left click cancels the pending region or removes the marked region under the crosshair.
- Marked regions are outlined in **yellow**; the live region that follows the crosshair uses a **brighter yellow**. Both use the same edge rules as chain mining / quick build (only 90° / 270° corner edges, hidden parts are darker). The separate thick box on the first point is no longer drawn during the live preview. When no block can be picked (pointing at air / out of reach), the position about 2 blocks ahead of the view is used, just like quick build.
- **Store into a blueprint box**: after marking, switch your main hand to a **blueprint box with no items** (it may already contain a blueprint; the new one overwrites it), then right click a block inside the selection. Both client and server re-read the structure and validate capacity; on success the selected blocks disappear, all block items and container contents are stored in the box, and blueprint mode exits.
- **Not enough space**: if the required slots exceed the box capacity, you get "Too many items; use a higher tier blueprint box" and **blueprint mode stays active**, so you can switch to a larger box and right click again.
- Up to 4096 blocks per operation and the targets must be within 40 blocks of the player; the server re-reads the blocks and validates distance and item counts. The recording stores block states, block entity data, and container contents with their original slots; when rotating, block orientations (stairs, chests, signs, logs, ...) are rotated as well.

### 6. Blueprint boxes

- Recording flow: press `V` and mark the region → switch to a **blueprint box with no items** → right click the selection to store it. The recording stores the complete structure: block states, block entity data, and **container contents together with their original slots**.
- **Ghost preview building**: hold a blueprint box with a blueprint and press `Ctrl + right click` to enter the preview. The ghost starts **8 blocks** ahead of your view; if the structure would touch you, it automatically moves to "just far enough + 2 blocks". **Scroll** to change the distance (scroll forward = farther, backward = closer; the wheel does not switch items during the preview), and press **Tab** to rotate 90° clockwise around the Y axis; block orientations rotate with it. `ENTITYBLOCK_ANIMATED` blocks such as chests, shulker boxes and beds render their block models with the real BlockState. The ghost checks every target block: it searches a sphere of radius **4** around the indicator for the nearest position where all blocks are replaceable and the structure does not touch the player, and reports that no position fits if none is found.
- **Pin / build**: the first right click pins the ghost so you can walk around and inspect it; left click unpins it and it follows your view again. While pinned, right click again to confirm the build. Block items and container contents are consumed exactly from the box; not enough materials or too much distance cancels the build.
- Tiers and capacities (all 9 columns):
  - Blueprint Box: 3×9 (27), lapis trim, chest + 8 paper;
  - Copper Blueprint Box: 5×9 (45), copper trim, blueprint box + 8 copper ingots;
  - Iron Blueprint Box: 7×9 (63), iron trim, copper box + 8 iron ingots;
  - Gold Blueprint Box: 9×9 (81), gold trim, iron box + 8 gold ingots;
  - Obsidian Blueprint Box: 12×9 (108), crying obsidian trim, gold box + 8 obsidian;
  - Diamond Blueprint Box: 15×9 (135), diamond trim, obsidian box + 8 diamonds;
  - Netherite Blueprint Box: 18×9 (162), netherite trim, smithing table: diamond box + netherite ingot + netherite upgrade template.
- The block directly extends vanilla `ChestBlock` / `ChestBlockEntity` and uses vanilla `ChestRenderer`: it has the chest shape, facing, comparator, open/close sounds and the **vanilla lid animation**; blueprint boxes always stay single, never merge into double chests, and cannot be placed inside another blueprint box. Each tier uses a separate vanilla chest texture whose dark frame is recoloured to the matching material, instead of tinting the whole chest, while the vanilla black interior is preserved.
- The screen shows at most **5 rows × 9 columns**, aligned with the player inventory columns; if the box has more than 5 rows, a **scrollbar** on the right can be used with the mouse wheel or dragging, using the creative-inventory thumb with a vanilla-slot-style track. The main panel uses vanilla `generic_54.png` directly: the slots, player inventory and title area are all drawn by the vanilla texture; only the right extension contains the scrollbar and buttons, with no black vertical seam between the two panels. Without a blueprint every slot uses the **crafter disabled-slot overlay**; after recording, **semi-transparent ghost placeholders** and per-slot required counts are shown. The count is scaled down inside the top-right corner of the slot so it never leaves the border. Placeholder slots only accept the matching item and respect the per-slot limit.
- Hovering a slot shows the item description: an empty placeholder shows the placeholder item and required count, while a stored item shows its main tooltip. Blueprint box item tooltips no longer include operation instructions or whether a blueprint has been recorded.
- Ghosts only draw faces that would be visible in a normal placement; hidden faces between adjacent blocks are culled, and entity-rendered blocks such as chests use a depth pre-pass before translucent drawing so their interior textures do not show through.
- Items in the inventory, hotbar and as dropped entities use the same chest model and tier texture as the placed block, instead of the old custom item model.
- `Store All` moves matching items from the inventory into the box; `Quick Take` moves items to the inventory and keeps the blueprint, leaving overflow in the box; `Clear Blueprint` returns items and clears the blueprint and placeholders, dropping overflow at the player's feet.
- Hoppers and automation work like a normal chest: the box implements the container interface, so hoppers can extract; insertion checks the item and per-slot limit. A comparator outputs 0-15 redstone strength based on **filled count / total required count**.
- Dropped-item protection:
  - all blueprint boxes float upward in water;
  - for copper and above, the **placed block** is fire / lava proof (it does not burn and lava does not destroy it), but the **dropped item** of copper, iron, gold, obsidian and diamond boxes can still be destroyed by fire / lava;
  - the **dropped item** of obsidian and above is not destroyed by explosions; a placed obsidian-or-higher blueprint box block is also not destroyed by explosions;
  - only the netherite dropped item is fire / lava proof and floats in lava; if it falls into the void, it teleports to the nearest safe standing position.
- Renaming with an anvil keeps the contents / blueprint; breaking the box keeps the items, blueprint, and container contents (creative mode also drops the item).
- Container contents are extra material cost when recording / building: e.g., a chest holding 3 diamonds in the blueprint needs 3 extra diamonds; after building, they are placed back into the original container slots.
- This mod's items have their own creative tab, "Aliya's Toolkit Items".

### 7. Aliya's Toolkit settings screen

Press `Alt + X` (rebindable) to open it:

- **Left side**: 5 feature buttons (`Item Switching` / `Automatic Refill` / `Chain Mining` / `Quick Build` / `Blueprint Build`). Clicking one shows that feature's settings on the right; the first one (Item Switching) is shown by default and the active button is highlighted.
- The left and right halves are separated by a vertical divider that runs down almost the whole screen (with a small gap at the bottom): the left side only has the feature buttons, the right side only the feature content.
- **Item Switching**: a short description plus a `Swap Row` button (which inventory row is swapped with the hotbar, 1-3, default 3; left click +1, right click -1). The key can be changed in Options → Controls → Key Binds.
- **Automatic Refill**: the `Exact Match` toggle, the 3×3 **item whitelist** and the player's inventory / hotbar (only shown on this page). Pick up an item from the inventory, **left click** a whitelist cell to store that item there (the item on the cursor is not consumed — put it back afterwards). **Right click** a cell to clear it; everything is saved automatically.
- **Chain Mining**:
  - `Chain Limit`: left click +1, right click -1, hold Shift for steps of 16;
  - `Massive blocks`: a **preset selector** (`<` / `>` / name), `New` (copies the current preset) and `Del` (at least one preset always remains); below the selector there is a single **preset name** row (editable, without a “Preset Name” label; defaults to 预设1, 预设2, ... and new presets take the next unused number); below it a cross-shaped layout of `Up` / `Left` `Layers` `Right` / `Down` (up / down / left / right 0-4, layers 1-8; left click +1, right click -1, Shift for steps of 16); `Lock Direction` and `Allow Other Blocks` each on its own full-width row;
  - A hint at the bottom: hold the chain key and scroll to switch presets (no wrapping, does not toggle the mode).
- **Quick Build**:
  - A **preset selector** (`<` / `>` / name), `New` and `Del` (at least one preset always remains, up to 16);
  - Below the selector there is a single **preset name** row (editable, no label; defaults to 预设1, 预设2, ...);
  - `Operation` / `Dimension` / `Shape` / `Fill`, each on its own full-width row: **left click = next, right click = previous, wrapping**; hovering shows all available choices with the current one in `[]`; greyed out when there is only one choice.
- **Blueprint Build**: a short description plus hints for marking regions / storing into a box / ghost preview / rotate / pin / build (the displayed keys follow your key bindings).
- The close button is in the top-right corner. The whitelist and settings (including all presets) are attached to the **player**, so in multiplayer every player has their own values, they are stored with the player data and they survive death / respawn.

## On-screen hints

- Switching presets (hold `Z` and scroll) shows the preset name, number, shape / layers / lock direction in the **action bar** (vanilla text overlay), which fades out automatically.
- Switching quick-build presets (hold `C` and scroll) shows the preset name, number, operation / dimension / shape / fill in the action bar.
- After the first quick-build point is selected, the action bar keeps showing the required block count and how many you have; the second right click starts the build.
- Chain mining outlines the planned region in white and the targeted block with a thicker white frame; quick build outlines the planned region in green; blueprint build outlines the marked regions in yellow and the ghost preview in blue (red when materials are insufficient or the position is invalid); a pinned ghost stays in the world so you can walk around and inspect it.
- Switching the selected hotbar slot uses vanilla's built-in **selected item name** display; the mod does not add a second prompt.


## Usage

| Action | Default key | Change it in |
| --- | --- | --- |
| Swap hotbar ↔ chosen inventory row | `X` | Options → Controls → Key Binds → *Aliya's Toolkit* |
| Chain mining toggle | Tap `Z` (applied on release) | same place |
| Switch massive-block preset | Hold `Z` and scroll (chain mining must be enabled; down = next, up = previous, no wrapping; does not toggle the mode) | same place |
| Quick build toggle | Tap `C` (applied on release) | same place |
| Switch quick-build preset | Hold `C` and scroll (quick build must be enabled; no wrapping; does not toggle the mode) | same place |
| Blueprint build toggle / selection mode | Tap `V` | same place |
| Blueprint build: store the selection | Switch to a blueprint box with no items, then right click inside the selection | same place |
| Blueprint build: ghost preview | Hold a box with a blueprint, `Ctrl + right click` | same place |
| Blueprint build: change ghost distance | Scroll (does not switch items during the preview) | same place |
| Blueprint build: rotate the ghost | `Tab` (before pinning) | same place |
| Blueprint build: pin / unpin | right click / left click (during ghost preview) | same place |
| Blueprint build: confirm | right click again while pinned | same place |
| Open the Aliya's Toolkit settings screen | `Alt + X` | same place |

All keys only work in-game (not while a screen is open).

## Configuration

Open **Mods → Aliya's Toolkit → Config**, or edit `config/aliyastoolkit-common.toml`. Only the feature switches live here (they are global); every fine-grained setting is per player and is changed in the in-game **Aliya's Toolkit settings screen**.

| Option | Default | Description |
| --- | --- | --- |
| `enableHotbarSwap` | `true` | Enable the hotbar swap key. |
| `enableAutoRefill` | `true` | Enable automatic refill. |
| `enableChainMining` | `true` | Enable chain mining. |
| `enableQuickBuild` | `true` | Enable quick build. |
| `enableCopyPaste` | `true` | Enable blueprint build. |

Per-player settings (in the in-game screen):

| Setting | Default | Description |
| --- | --- | --- |
| Exact Item Match | `ON` | `ON`: item type plus all data components (enchantments, custom name, ...) must match; `OFF`: only the item type is compared. **Durability is always ignored.** |
| Swap Row | `3` | Which inventory row is swapped with the hotbar: 1 = top row, 3 = bottom row. Left click +1, right click -1. |
| Chain Limit | `64` | Maximum number of blocks broken by one connected chain (1-256, including the targeted block). Left click +1, right click -1, hold Shift for steps of 16. Does not apply to the exact "massive blocks" shape. |
| Preset Name | 预设1, 预设2, ... | Every chain-mining / quick-build preset has a name. The row below the preset selector is editable (no “Preset Name” label). New presets automatically take the next unused 预设N. |
| Chain presets | 1 preset: up/down/left/right `0`, 1 layer, lock direction OFF | Up to 16 presets for the "massive blocks" shape. Left click a value to increase it, right click to decrease it (hold Shift for steps of 16). Switching stops at both ends. |
| Shape up / down / left / right | `0` (0-4) | Cross-section extension relative to the targeted block and the player's facing. |
| Layers | `1` (1-8) | How many layers the cross-section is extruded forward. |
| Lock Direction | `OFF` | `ON`: horizontally forward normally, 45° up / down while sneaking. `OFF`: straight up / down is also possible normally; while sneaking forward / 45° up / 45° down / straight up / straight down are all available. |
| Allow Other Blocks | `ON` | Inside the massive-block shape, other breakable blocks are broken too. Unbreakable blocks and liquid blocks are always skipped. |
| Quick-build presets | 1 preset: Point to point · 2D · Square · Solid | Up to 16 presets. Operation / dimension / shape / fill are switched with buttons (left click = next, right click = previous, wrapping; hover shows the choices). |

## Multiplayer

- All inventory / block changes are performed **on the server**; the client only sends requests. The mod therefore has to be installed on **both the client and the server**.
- Network channels are registered as *optional*: a client with the mod can still join a server without it (the keys simply do nothing and the connection is never refused), and vice versa.
- Each player's whitelist and settings are stored with their player data, so in multiplayer everyone has their own values and they survive death / respawn.

## Building from source

Requires **JDK 21** (the Gradle wrapper is included):

```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat build

# Linux / macOS
export JAVA_HOME=/path/to/jdk-21
./gradlew build
```

- Output jar: `build/libs/aliyastoolkit-<version>.jar`
- Development client: `gradlew runClient`
- Development server: `gradlew runServer`
- Data generation: `gradlew runData`

## License

This project is licensed under the **MIT License**; see [LICENCE](LICENCE) for the full text.

The project was bootstrapped from the [NeoForge MDK](https://github.com/NeoForged/MDK) template.

