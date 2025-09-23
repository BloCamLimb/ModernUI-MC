# Modern UI for Minecraft
[![CurseForge](http://cf.way2muchnoise.eu/full_352491_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/modern-ui)
[![CurseForge](http://cf.way2muchnoise.eu/versions/For%20Minecraft_352491_all.svg)](https://www.curseforge.com/minecraft/mc-mods/modern-ui)
### Description
**Modern UI for Minecraft**, a.k.a ModernUI-MC, is a Minecraft Mod that is based on
[ModernUI Framework](https://github.com/BloCamLimb/ModernUI) (a cross-platform framework to
build rich desktop applications on top of JDK) and its extensions.  

This project provides ModernUI bootstrap program in Minecraft environment, allowing ModernUI-based applications
to run *natively* in Minecraft. It also provides Modding API based on Forge/NeoForge/Fabric
to make powerful Graphical User Interface in Minecraft.

This Mod also includes a powerful text layout engine and text rendering system designed for Minecraft.
This engine allows Minecraft and Mods based on Vanilla GUI system to enjoy a modern text system similar
to ModernUI framework without modifying their code. It provides appropriate methods for processing Unicode
text and gives you more readable text in any scale, in 2D/3D. In details:
* Real-time preview and reload TrueType/OpenType fonts
* A better font fallback implementation
* Anti-aliasing text and FreeType font hinting
* Use improved SDF text rendering in 2D/3D (also use batch rendering)
* Compute exact font size in device space for native glyph rendering
* Use Google Noto Color Emoji and support all the Unicode 16.0 Emoji
* Configurable bidirectional text heuristic algorithm
* Configurable text shadow and raw font size
* Unicode line breaking and CSS line-break & word-break
* Fast, exact and asynchronous Unicode text layout computation
* Faster and more memory efficient rectangle packing algorithm for glyphs
* Use real alpha mask texture (1 byte-per-pixel, whereas Minecraft is 4 bpp)
* Many optimizations for text rendering, multiplying the performance of
  GUI text rendering, Sign/Glowing Sign text rendering, greatly improving framerate,
  significantly reducing object allocations and lowering GC pressure.
* Compatible with OptiFine, Sodium (Rubidium), Iris (Oculus) and many mods
* Compatible with Minecraft's JSON font definition (bitmap fonts, TTF fonts)

Additionally, this Mod provides many utilities which improve game performance and gaming experience. Currently, we have:
* Changing screen background color
* Gaussian blur to screen backdrop image
* Fade-in animation to screen background
* More window modes: Fullscreen (borderless), Maximized (borderless)
* Framerate limit and master volume fading on window inactive (out of focus) and minimized
* Pausing single-player game when Inventory is open
* Changing GUI scale option to Slider and providing hint text
* Playing a "Ding" sound when Minecraft loads and reaches the Main Menu
* Enabling smooth scrolling in Vanilla's Selection List and Forge's Scroll Panel
* Pressing "C" to Zoom that is the same as OptiFine
* Undo/Redo and Unicode word iterator for all text fields
* Playing local music, allowing to seek and view spectrum
* Support Discord/Slack/GitHub/IamCal/JoyPixels emoji shortcodes in Chatting
* A fancy tooltip style
  + Choose rounded border or normal border (with anti-aliasing)
  + Add title break and control title line spacing
  + Center the title line, support RTL layout direction
  + Exactly position the tooltip to pixel grid (smooth movement)
  + Change background color and border color (with gradient and animation)

You can think of this project as a bridge/service layer, along with several improvements and extensions specifically
made for Minecraft. Applications (or program fragments) you develop with ModernUI can run independently of Minecraft,
or inside Minecraft — via this Mod but without any code changes. Other Minecraft Mods based on Minecraft Vanilla
can also benefit from this Mod.

If you want to develop mods — especially ones with feature-rich interfaces — it's recommended to build on the
**ModernUI framework** (instead of just putting ModernUI-MC in your mod list). You may also implement a hybrid UI:
part of it based on ModernUI, and part of it on the Vanilla GUI system (which will be enhanced by ModernUI-MC,
though these enhancements can be disabled).

Releases for Minecraft Mod are available on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modern-ui) and
[Modrinth](https://modrinth.com/mod/modern-ui).  
For historical reasons, issues should go to Core Repo's [Issue Tracker](https://github.com/BloCamLimb/ModernUI/issues). 
If you have any questions, feel free to join our [Discord](https://discord.gg/kmyGKt2) server.
### License
* Modern UI for Minecraft
  - Copyright (C) 2019-2025 BloCamLimb et al.
  - [![License](https://img.shields.io/badge/License-LGPL--3.0--or--later-blue.svg?style=flat-square)](https://www.gnu.org/licenses/lgpl-3.0.en.html)
* Additional Assets
  - [source-han-sans](https://github.com/adobe-fonts/source-han-sans) by Adobe, licensed under the OFL-1.1
  - [jetbrains-mono](https://www.jetbrains.com/lp/mono/) by JetBrains, licensed under the OFL-1.1
  - [inter](https://github.com/rsms/inter) by RSMS, licensed under the OFL-1.1
    + Modern UI for Minecraft contains a modified version of Inter font family, the original license and
      copyright notice were retained in the font file

#### Gradle configuration
You MUST use our Maven if you are in development environment, the version we publish to Maven is different
from CurseForge and Modrinth.
```groovy
repositories {
    maven {
        name 'IzzelAliz Maven'
        url 'https://maven.izzel.io/releases/'
    }
    // If you are on Fabric, uncomment this.
    /*maven {
        url 'https://raw.githubusercontent.com/Fuzss/modresources/main/maven/'
        content {
            includeGroup 'fuzs.forgeconfigapiport'
        }
    }*/
}
```
##### ArchLoom / FabricLoom
```groovy
dependencies {
  // If you are on Fabric, uncomment this and find a compatible FCAPI version.
  // modApi "fuzs.forgeconfigapiport:forgeconfigapiport-fabric:${fcapi_version}"
  implementation "icyllis.modernui:ModernUI-Core:${modernui_version}"
  // Modern UI core extensions
  // Markdown (<=3.11.1) / Markflow (>=3.12.0) is required, others are optional
  implementation "icyllis.modernui:ModernUI-Markflow:${modernui_version}"
  // Choose one of Fabric or NeoForge
  modImplementation("icyllis.modernui:ModernUI-Fabric:${minecraft_version}-${modernui_version}.+")
}
```
##### ModDevGradle
```groovy
dependencies {
  // Modern UI
  implementation("icyllis.modernui:ModernUI-NeoForge:${minecraft_version}-${modernui_version}.+")
  additionalRuntimeClasspath(compileOnly("icyllis.modernui:ModernUI-Core:${modernui_version}")) {
    exclude group: "org.slf4j", module: "slf4j-api"
    exclude group: "org.apache.logging.log4j", module: "log4j-core"
    exclude group: "org.apache.logging.log4j", module: "log4j-api"
    exclude group: "com.google.code.findbugs", module: "jsr305"
    exclude group: "org.jetbrains", module: "annotations"
    exclude group: "com.ibm.icu", module: "icu4j"
    exclude group: "it.unimi.dsi", module: "fastutil"
  }
  // Modern UI core extensions
  // Markdown (<=3.11.1) / Markflow (>=3.12.0) is required, others are optional
  additionalRuntimeClasspath(compileOnly("icyllis.modernui:ModernUI-Markflow:${modernui_version}")) {
    exclude group: "org.slf4j", module: "slf4j-api"
    exclude group: "org.apache.logging.log4j", module: "log4j-core"
    exclude group: "org.apache.logging.log4j", module: "log4j-api"
    exclude group: "com.google.code.findbugs", module: "jsr305"
    exclude group: "org.jetbrains", module: "annotations"
    exclude group: "com.ibm.icu", module: "icu4j"
    exclude group: "it.unimi.dsi", module: "fastutil"
  }
}
```
##### ForgeGradle 5
```groovy
configurations {
    library
    implementation.extendsFrom library
}
minecraft.runs.all {
  lazyToken('minecraft_classpath') {
    configurations.library.copyRecursive().resolve().collect { it.absolutePath }.join(File.pathSeparator)
  }
}
// Add this block if you have not MixinGradle (https://github.com/SpongePowered/MixinGradle):
minecraft {
  runs {
    client {
      property 'mixin.env.remapRefMap', 'true'
      property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
    }
    server {
      property 'mixin.env.remapRefMap', 'true'
      property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
    }
    // apply to data if you have datagen
  }
  // You need to regenerate run configurations if you make any changes on this.
}
dependencies {
    library("icyllis.modernui:ModernUI-Core:${modernui_version}") {
      exclude group: "org.slf4j", module: "slf4j-api"
      exclude group: "org.apache.logging.log4j", module: "log4j-core"
      exclude group: "org.apache.logging.log4j", module: "log4j-api"
      exclude group: "com.google.code.findbugs", module: "jsr305"
      exclude group: "org.jetbrains", module: "annotations"
      exclude group: "com.ibm.icu", module: "icu4j"
      exclude group: "it.unimi.dsi", module: "fastutil"
    }
    // Modern UI core extensions
    // Markdown (<=3.11.1) / Markflow (>=3.12.0) is required, others are optional
    library("icyllis.modernui:ModernUI-Markflow:${modernui_version}") {
      exclude group: "org.slf4j", module: "slf4j-api"
      exclude group: "org.apache.logging.log4j", module: "log4j-core"
      exclude group: "org.apache.logging.log4j", module: "log4j-api"
      exclude group: "com.google.code.findbugs", module: "jsr305"
      exclude group: "org.jetbrains", module: "annotations"
      exclude group: "com.ibm.icu", module: "icu4j"
      exclude group: "it.unimi.dsi", module: "fastutil"
    }
    // Modern UI for Minecraft Forge
    implementation fg.deobf("icyllis.modernui:ModernUI-Forge:${minecraft_version}-${modernui_version}.+")
}
```
#### Building Modern UI for Minecraft
**Modern UI for Minecraft** requires the latest [ModernUI](https://github.com/BloCamLimb/ModernUI) codebase to build.
You should clone `ModernUI` into the same parent directory of `ModernUI-MC` and ensure they're up-to-date.
Checkout `ModernUI/master` branch or a stable branch.  
You can also cherry-pick a stable version of Modern UI for Minecraft and build from maven.

When you build ModernUI-MC, the universal jar will contain not only ModernUI-MC itself, but also shadow the
ModernUI framework and extensions (except Kotlin extensions), as well as additional assets and runtime.
### Screenshots (OUTDATED, see CurseForge/Modrinth or try yourself)
Center Screen  
![2024-03-30_16.17.11.png](https://cdn.modrinth.com/data/3sjzyvGR/images/2571f7372b1f9bbb116c118f29a93255338f4e41.png)
New Tooltip  
![new tooltip.png](https://s2.loli.net/2024/03/30/VhyoFPAD2Js1HWO.png)
Markdown  
![markdown](https://cdn.modrinth.com/data/3sjzyvGR/images/989a77ba61c62ff580a30dcf158e391080b949bd.png)  
Enhanced Texts for Minecraft Vanilla  
![fast text](https://cdn.modrinth.com/data/3sjzyvGR/images/d27f5d77555fd3f45392f5b8eb28efcb80f0b677.png)
![new4](https://s2.loli.net/2022/03/06/TM5dVKnpqNvDiJH.png)  
Navigation  
![new5](https://s2.loli.net/2022/03/06/hwAoHTgZNWBvEdq.png)  
Graphics  
![new3.gif](https://i.loli.net/2021/09/27/yNsL98XtpKP7UVA.gif)  
Audio visualization  
![new2](https://i.loli.net/2021/09/24/TJjyzd6oOf5pPcq.png)  
Out-of-date widgets  
![a](https://i.loli.net/2020/05/15/fYAow29d4JtqaGu.png)
![b](https://i.loli.net/2020/04/10/LDBFc1qo5wtnS8u.png)
