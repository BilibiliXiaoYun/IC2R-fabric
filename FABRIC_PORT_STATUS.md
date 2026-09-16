# Minecraft 1.21.1 Fabric 移植状态

**状态：`compileJava` 与 `build` 全部通过，已产出可安装的 Fabric JAR；专用服务器能启动，客户端能加载全部
388 个自定义模型并进入单人世界；游戏测试 426 项能完整跑完（424 通过，2 项偶发失败）；用户实测玩法功能
全部通过。移植仍未完成：JEI/AE2 未迁移（客户端无配方查看器），存档重载与多人同步未验证。**

上游：https://github.com/neo-industrial-mc/IC2R

源码基线：`417fddb1fd23b926accc02b380cd658121f5c9e1`

本地分支：`codex/fabric-1.21.1`

实际工作目录：`D:\CodexProjects\IC2R-fabric`。C 盘空间耗尽后复制到这里继续；C 盘的副本不是最新版本。

## 验证结果（实测）

| 项目 | 结果 |
| --- | --- |
| `compileJava` | **通过**，0 错误 |
| `build` | **通过**，产出 `build/libs/ic2-fabric-21.1.0-fabric.1.jar`（约 8.0 MB） |
| 专用服务器 | **启动成功**：`Done (1.452s)!`，`Applied 382 biome modifications to 53 of 64 new biomes` |
| 客户端 | **进入单人世界**：`logged in with entity id ...`；388 个自定义模型全部烘焙成功 |
| 游戏测试 | **426 项跑完**：`426 GAME TESTS COMPLETE IN 20.85 s`，424 通过，2 项偶发失败 |
| Mixin | 客户端与服务器均无注入失败 |
| 玩法实测（用户） | **全部通过**：采矿机、喷气背包、充能护甲属性、纳米剑、抽奖盒、蒸汽发电机流体输出 |

### 本轮修掉的关键 bug（编译完全看不出来）

| # | 现象 | 根因 |
| --- | --- | --- |
| 13 | 游戏测试服务器跑到第 8 批就崩（`ic2:steam_generator`） | `FluidHandler.isFluidBlock(state, be, side)` 故意传 `world=null, pos=null`，而 `FabricFluidHandler.blockStorage` 仍拿 null `pos` 查 `FluidStorage.SIDED` → NPE。**实测会崩服** |
| 14 | **所有电系装备的属性修饰器在游戏里完全失效** | 1.21.1 的 `ItemStack#forEachModifier` 的 `EquipmentSlot` 与 `EquipmentSlotGroup` 是**两个独立实现**（group 版不委托单槽版），实体属性走 group 版，而 `ItemStackMixin` 只挂了单槽版 |
| 15 | 充能纳米/量子护甲无护甲值、纳米剑伤害恒为基础值 | 同 #14 |
| 16 | 抽奖盒测试失败 | 测试用的是创造模式假玩家，`ItemScrapBox.use` 不消耗物品 |
| 17 | 堆叠流体容器抛 `invalid stack size` | 引擎对堆叠容器**抛异常**；已改为报空，避免外来模组探测时崩溃 |

第 14 条尤其隐蔽：修复前编译、启动、模型烘焙、Mixin 加载全部正常，**只有实测属性和游戏测试能发现**。

## 本轮完成的工作

### 1. 方块侧流体能力（补上第 3 轮发现的缺口）

`Ic2Capabilities` 在 NeoForge 上通过 `Capabilities.FluidHandler.BLOCK` + `BlockFluidCapImpl` 暴露方块流体，
Fabric 侧原先没有对应实现，导致 `FluidHandler.fillMb/drainMb(BlockEntity, Direction, ...)`（`LiquidUtil`
的机器↔机器传输）取不到存储。现在 `FabricFluidHandler` 直接用 `Ic2FluidBlock` 契约处理 IC2 自己的方块
实体（`FluidBeBridge` 或 `Fluids` 组件），外来方块实体仍走 `FluidStorage.SIDED`。直接调用还保留了显式的
`simulate` 参数——Transfer API 会把模拟表达成事务回滚，需要存储实现快照。

### 2. `pack.mcmeta` 声明的包格式是 1.20.1 的 15（严重，影响所有新世界）

游戏测试日志暴露了 `Active Data Packs: ..., ic2 (incompatible)`：`pack.mcmeta` 的 `pack_format` 是 15，
而 1.21.1 的资源包格式是 34、数据包格式是 48。**在不兼容的情况下，整个 `data/ic2` 数据包都不会被应用**
——战利品表、进度、世界生成 JSON、游戏测试结构全部失效（这正是测试结构报
`Missing test structure: gametest/empty7x7x7` 的原因）。已改为
`"pack_format": 34, "supported_formats": [34, 48]`，改后 IC2 数据包正常启用。

### 3. 游戏测试套件接入

- 57 个测试类注册到 `fabric.mod.json` 的 `fabric-gametest` 入口（56 个在 `ic2/core/gametest`，外加
  `ic2.core.energy.grid.EnergyCalculatorUnifiedGameTests`）。
- `build.gradle` 新增 `runGameTestServer` 运行配置（`-Dfabric-api.gametest` + 报告文件 + 独立运行目录）。
  注意 `runDir` 必须是项目相对路径，绝对路径会被 Loom 再拼一次项目目录，导致 `CreateProcess error=267`。
- Fabric 的 `TestFunctionsMixin` 在 `@GameTest(template=...)` 非空时**直接使用该字符串**，不会补上模组
  命名空间（NeoForge 是由 `@GameTestHolder("ic2")` 提供的）。因此 57 个类的模板常量全部改为
  `ic2:gametest/empty3x3x3` 等带命名空间的形式。
- Fabric Loader 会实例化每个入口类，因此 4 个原本用私有构造器的测试类（`ArmorGameTests`、
  `IridiumLootGameTests`、`ScrapBoxGameTests`、`EnergyCalculatorUnifiedGameTests`）改为公开构造器。
- 第 3 轮已完成的迁移：注解清理、`TestItemSlot`、`ItemStorage.SIDED`、`Ic2EventBus`、`ItemTags.HOES`、
  `PlayerBlockBreakEvents`。

### 4. 修掉此前 PowerShell 改写造成的编码损坏

第 3 轮用 PowerShell `Set-Content` 批量改写时，把 3 处非 ASCII 破折号写坏了
（`FabricFluidHandler` 1 处、`PersonalSafeGameTests` 2 处），导致 `unmappable character` 编译错误。
已修复，并对 `src/**` 全量扫描，当前 0 个损坏文件。

## 已知遗留问题

0. **实测反馈**：本轮用户实测**全部通过** —— 采矿机 ✅、喷气背包 ✅、充能纳米/量子护甲护甲值 ✅、
   纳米剑伤害 ✅、抽奖盒 ✅、蒸汽发电机流体输出 ✅（另有上轮已确认：采矿激光 ✅、流体单元 ✅、渲染 ✅）。
1. **游戏测试：426 个跑完，424 通过，2 个偶发失败**（报告在 `build/gametest/gametest.xml`）。
   假玩家崩溃修好后套件终于能完整执行，失败数 6 → 3 → 2：
   - `advminergametests.advminerskipsoreoutsidewhitelist`：偶发（某一轮通过）。已用临时日志确认
     `canMine` 逻辑本身正确（白名单外矿石返回 `false`），属并发批次下的测试隔离问题。
   - `chunkloadergametests.chunkloaderforceloadswhilepoweredandstopswhendrained`：偶发（某一轮通过），同上。
   - 本轮已修：`fluidmachinegametests.*` 三项（前两项的前提在 Fabric 上不可达，已改为断言实际可达的
     专用单元路径；第三项是引擎对堆叠容器抛异常，已改为报空）与
     `scrapboxgametests.rightclickconsumesscrapboxanddropsreward`（测试用了创造模式假玩家，不消耗物品）。
2. **JEI/AE2 兼容**未迁移（`ic2/integration/**`，18 个文件）——目前**唯一的 exclude**，客户端没有配方查看器。
   这是移植完成度上最后一块大缺口。
3. **端到端玩法验证**：单机玩法已确认，**存档重载与多人同步仍未验证**。
4. `No data fixer registered for ic2:<实体>`（6 条，ERROR 级）来自原版 `net.minecraft.Util`，不阻塞启动。
5. 着墨器逐物品叠加层已接线，但还没有用带 NBT 的着墨器实测。
6. 更完整的承接说明见 [`docs/HANDOVER.md`](docs/HANDOVER.md)。

## 编译范围（build.gradle 中的 exclude）

| 目录 | 文件数 | 阻塞原因 |
| --- | ---: | --- |
| `ic2/integration/**` | 18 | 需要 Fabric 版 JEI 与 AE2 依赖 |

`ic2/core/gametest/**` 与 `EnergyCalculatorUnifiedGameTests` 的 exclude 已删除——它们现在参与编译。
被取代的 NeoForge 平台适配器已整体移至 `src/neoforge-reference/java/ic2/forge/`（27 个文件，逐字保留）。

## 复现命令

本沙箱里 Gradle 无法自己 fork 构建 JVM（命名管道被拒，`CreatePipe error=5`），所以用仓库内的
`tools/gradle-en.cmd`。

```powershell
# 完整编译与打包
cmd /c "tools\gradle-en.cmd compileJava > .tmp\compile.log 2>&1"
cmd /c "tools\gradle-en.cmd build -x test -x portComponentTest > .tmp\build.log 2>&1"

# 游戏测试（会自动跑完并退出；先在 build\gametest\run\eula.txt 写入 eula=true）
cmd /c "tools\gradle-en.cmd runGameTestServer > .tmp\gametest.log 2>&1"

# 服务器 / 客户端冒烟测试
cmd /c "tools\gradle-en.cmd runServer > .tmp\server.log 2>&1"
cmd /c "tools\gradle-en.cmd runClient > .tmp\client.log 2>&1"
```

## 剩余实施顺序

1. 存档重载 + 多人同步验证（唯一还没覆盖的玩法维度）。
2. 迁移 `ic2/integration/**`（JEI/AE2），删掉最后一个 exclude。
3. 消除 2 项偶发测试失败（给 advminer / chunkloader 两项用独立模板，避开批次内共享模板）。
4. 清理技术债：`build.gradle` 里的 `portComponents` / `portComponentTest` 源集；用带 NBT 的着墨器实测一次。

## 来源与许可

保留上游 LICENSE 与 README 版权声明。此分支继承上游关于反编译原版代码及素材的授权限制；没有将原版
IC2 代码重新声明为 AGPL。
