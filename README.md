# IC2R — Fabric port

<img src="https://img.shields.io/badge/Minecraft-1.21.1-brightgreen" alt="Minecraft 1.21.1">
<img src="https://img.shields.io/badge/Fabric%20Loader-0.16.14-dbd0b4" alt="Fabric Loader 0.16.14">
<img src="https://img.shields.io/badge/Fabric%20API-0.116.17%2B1.21.1-dbd0b4" alt="Fabric API 0.116.17+1.21.1">
<img src="https://img.shields.io/badge/Version-21.1.0--fabric.1-blue" alt="Version 21.1.0-fabric.1">
<img src="https://img.shields.io/badge/License-AGPL--3.0-blue" alt="License AGPL-3.0">

IndustrialCraft 2 on **Minecraft 1.21.1 Fabric**.

This repository is the Fabric build of [IC2R](https://github.com/neo-industrial-mc/IC2R). IC2R is the community
continuation of IC2 and the source of truth for everything the player sees: gameplay, balance values, recipes
and assets. That content is carried over here unchanged. What is different is the platform underneath it —
IC2R targets NeoForge, this repository targets Fabric — so the loader-facing layer was rebuilt against
Fabric's APIs while the mod itself was left alone.

If you want the NeoForge build, go upstream. If you play on Fabric, you are in the right place.

## Status

Working and verified in game:

- Client and dedicated server both start; all 388 custom models bake without errors.
- Game tests: **426 tests across 57 classes** run headless, 424 passing. The two remaining failures are
  intermittent under concurrent batches, not gameplay bugs.
- Manually confirmed: the Miner, the jetpack, charged nano/quantum armour values, the nano saber's damage,
  the Scrap Box, and the steam generator's fluid output.

Known gaps:

- **No recipe viewer.** JEI and AE2 integration (`ic2/integration/**`) is not ported yet, so there is no
  in-game recipe lookup.
- Save reload and multiplayer sync have not been verified.


## Installing

1. Minecraft **1.21.1** with **Fabric Loader 0.16.14** or newer.
2. [Fabric API](https://modrinth.com/mod/fabric-api) **0.116.17+1.21.1** or newer — required.
3. Drop `ic2-fabric-<version>.jar` into your `mods/` folder.

The JAR is the remapped production build; the `-sources.jar` beside it is for development only and should not
be installed.

## Building

Java 21 is required.

```shell
./gradlew build          # -> build/libs/ic2-fabric-<version>.jar
./gradlew runClient      # development client
./gradlew runServer      # development dedicated server
```

`runClient` and `runServer` need `eula=true` in `run/eula.txt`.

### Game tests

The full suite runs headless and writes a JUnit-style report:

```shell
mkdir -p build/gametest/run && echo "eula=true" > build/gametest/run/eula.txt
./gradlew runGameTestServer
```

`build/gametest/gametest.xml` is the authoritative result — the console only prints assertion failures, so
counting from it will under-report. If a previous run crashed, delete `build/gametest/run/world` first;
the leftover `session.lock` otherwise makes the next run fail to start.

### Restricted environments

On Windows hosts where Gradle cannot fork its own build JVM (the named pipe is denied and Gradle reports
`CreatePipe error=5`), use the bundled launcher, which runs Gradle in-process:

```shell
cmd /c "tools\gradle-en.cmd build -x test -x portComponentTest"
```

## What the port had to rebuild

Both loaders expose different APIs, so these layers are Fabric-specific implementations of IC2R's behaviour.
Everything else in `src/main/java/ic2/**` is shared with upstream.

- **Platform layer** — `ic2/fabric/Ic2Fabric` and `Ic2FabricClient` replace the NeoForge `FmlMod` and
  `ClientModEventHandlerForge`, reproducing NeoForge's registry initialization order explicitly.
  `FabricEnvProxy` replaces `EnvProxyForge`.
- **Events** — `NeoForge.EVENT_BUS` is replaced by IC2's own `Ic2EventBus`. Where Fabric offers no callback at
  all, real mixins are used: fog rendering, sound replacement, item attribute modifiers, living-entity hooks,
  and `onDroppedByPlayer`.
- **Fluids** — vanilla 1.21.1 has neither `FluidType` nor `FlowingFluid.Properties` (both are NeoForge
  patches), so each IC2 fluid is backed by a vanilla `FlowingFluid` pairing with `FluidVariantAttributes`.
  Unit conversion is exact: 1 mB = 81 droplets.
- **Rendering** — Fabric removed custom JSON model loaders, so the 388 `"loader": "ic2:*"` definitions are
  taken over by a `PreparableModelLoadingPlugin`, and `BakedQuad`s are replayed into Indigo's `QuadEmitter`.
- **Data** — NeoForge-only biome modifiers and the global loot modifier are reproduced through
  `fabric-biome-api-v1` and `LootTableEvents.MODIFY`.

The superseded NeoForge adapters are kept verbatim under `src/neoforge-reference/` for reference. They are not
compiled and are not shipped.

## Credits and licensing

IC2R is built by the IC2R team on top of IC2 by the **IC2 Dev Team**. Upstream describes its code as obtained
by decompiling the official `2.9.40-ex119` build, with missing functionality migrated from `2.8.222-ex112`.
Neither the IC2R team nor this port owns the copyright to IC2's code or assets.

**This port claims no authorship of the mod's content.** The gameplay, values, recipes and assets are IC2R's;
what this repository contributes is the Fabric platform layer and the port maintenance around it. The
NeoForge-to-Fabric work here is likewise derivative of upstream's structure and does not relicense it.

This repository is distributed under **AGPL-3.0** (see [LICENSE](./LICENSE)), which covers the original
contributions made here. It cannot determine the licensing of the project as a whole, because the underlying
IC2 code and assets remain the property of their original authors. Because of that, no permission to use this
mod on commercial servers can be granted from this repository — that is a right held by the original authors,
and any such statement made here would have no effect on the parts they own.

If you redistribute this mod or include it in a modpack, you must credit IC2 and IC2R, keep the descriptions
and links they designate — `https://industrial-craft.net/` and `https://forum.industrial-craft.net/` — and
preserve the licence notices above.

## Reporting problems

Issues with **gameplay, balance, recipes or assets** belong upstream at
[neo-industrial-mc/IC2R](https://github.com/neo-industrial-mc/IC2R) — that code is shared, and a fix there
reaches every platform.

Issues that are **Fabric-specific** belong here: crashes on startup, mixin failures, rendering that differs
from the NeoForge build, Fabric API incompatibilities, or anything that only reproduces on Fabric. Include
your Fabric Loader and Fabric API versions, the full log, and a crash report if there is one.
