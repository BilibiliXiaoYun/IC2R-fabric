# beta2601

First public Fabric build. Welcome — this file tracks what is planned and what is currently broken.

## Known issues

### Blocking

- **No recipe viewer.** JEI and AE2 integration (`ic2/integration/**`, 18 files) is not ported yet, so there
  is no in-game recipe lookup and no AE2 energy integration. The IC2 power grid itself works; it simply
  cannot connect to AE2's energy acceptor yet.
- **Save reload and multiplayer sync are unverified.** Everything below was confirmed in a single-player
  session. Reloading a world and playing with several clients on a dedicated server have not been tested,
  so treat persistence and multiplayer as unproven.

### Minor

- Two game tests are **intermittently** red under concurrent batches:
  `AdvMinerGameTests#advMinerSkipsOreOutsideWhitelist` and
  `ChunkLoaderGameTests#chunkLoaderForceLoadsWhilePoweredAndStopsWhenDrained`. Both pass in other runs, and
  the underlying behaviour was verified by hand, so this looks like test isolation between batches sharing a
  template rather than a gameplay bug.
- 6 harmless `No data fixer registered for ic2:<entity>` errors on startup. Vanilla prints these for any
  modded entity without a DFU schema entry. Every loader has them.
- Four client hooks are deliberately unwired because upstream's implementations are empty:
  `RenderLivingEvent.Pre/Post`, `RenderHighlightEvent.Block`, `ScreenEvent.Init.Post`.
- The Obscurator's per-stack overlay is wired through `BuiltinItemRendererRegistry`, but has not been tested
  with an NBT-carrying Obscurator.

### Not planned here

Gameplay, balance, recipe and asset changes are not made in this repository. Those belong to
[IC2R](https://github.com/neo-industrial-mc/IC2R) — the code is shared, so a fix there reaches every
platform. Report them upstream.

## What this build is

The Fabric port of IC2R for Minecraft 1.21.1. The mod's content is upstream's and was carried over unchanged;
the platform layer underneath it was rebuilt against Fabric's APIs.

Verified in game for this release:

- Client and dedicated server start; all 388 custom models bake.
- Game tests: 426 tests across 57 classes, 424 passing.
- Manually confirmed: Miner, jetpack, charged nano/quantum armour values, nano saber damage, Scrap Box,
  steam generator fluid output.

## Installing

- Minecraft **1.21.1**
- **Fabric Loader 0.16.14** or newer
- **Fabric API 0.116.17+1.21.1** or newer — required
- `ic2-fabric-beta2601.jar` in `mods/`

Do not install the `-sources.jar`; it is a development artifact.

## Reporting problems

Fabric-specific problems — startup crashes, mixin failures, rendering that differs from the NeoForge build,
Fabric API incompatibilities, anything that only reproduces on Fabric — belong in this repository's issue
tracker. Include your Fabric Loader and Fabric API versions, the full log, and a crash report if there is one.

Gameplay, balance, recipe or asset problems belong upstream at
[neo-industrial-mc/IC2R](https://github.com/neo-industrial-mc/IC2R).

## Differences from the original IC2

Unchanged from upstream. See IC2R's own release notes for the current list; this port adds no gameplay
changes of its own.
