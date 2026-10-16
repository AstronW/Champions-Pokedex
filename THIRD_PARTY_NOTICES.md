# 第三方内容

## Pokémon 游戏文本、角色与图片

Pokémon 及相关游戏、角色、商标、文本、图像属于其权利人，包括 Nintendo、The Pokémon Company、Creatures 和 GAME FREAK。本项目为非官方爱好者图鉴，无上述公司的隶属或背书关系。

本地 `champout` 提供游戏提取字段，具体文件散列见 `data/import-report.json`，不使用 PokeAPI 的其他作品数值覆盖这些字段。

图片来自 [PokeAPI/sprites](https://github.com/PokeAPI/sprites) 的 `generation-ix/champions` 目录。该仓库的 [LICENCE.txt](https://github.com/PokeAPI/sprites/blob/master/LICENCE.txt) 声明仓库采用 CC0 1.0，同时明确图像内容版权属于 The Pokémon Company。不能将仓库的 CC0 声明解释为角色或图像权利的转让。

图片 ID 对应关系参考 [PokeAPI/pokeapi](https://github.com/PokeAPI/pokeapi)，版本、下载地址和 SHA-256 保存在 `data/pokeapi/SOURCE.json`。

## 52poke 百科文字

正文来自 [52poke 神奇宝贝百科](https://wiki.52poke.com/) 的对应条目及其贡献者，使用本地 `52poke-2026.09.13.xml` 存档。原站 [版权声明](https://wiki.52poke.com/wiki/神奇宝贝百科:版权声明) 指明其原创文字默认采用 [署名—非商业性使用—相同方式共享 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)。本项目包含的文字及其阅读格式改编沿用该许可。每篇正文保留原站标题、修订号、原文链接和来源哈希；原文及贡献记录可从该链接访问。

改动包括模板展示转换、繁体到简体转换、链接适配及移动端排版。游戏版本语境保留，不把其他作品的规则宣称为 Champions 的实测规则。

## 软件依赖

伤害计算规则移植自 [Smogon damage-calc](https://github.com/smogon/damage-calc)，提交 `eb9af6d94e7ee0ce034d2d6f2d44d4b2d66ff981`，Copyright (c) 2013–2025 Honko and other contributors，MIT。应用内的 `DamageCalc.kt` 是其 Champions 规则的 Kotlin 移植（衍生作品，沿用 MIT），`catalog.json` 含从上游数据表导出的招式、道具与属性数据。原样源码保存在 `vendor/damage-calc/src`，许可证原文在 `vendor/damage-calc/LICENSE`，并以 `app/src/main/assets/calculator/LICENSE-damage-calc.txt` 随应用分发。

计算器导入与参考用例生成期 TypeScript 4.9.5：Apache-2.0（不随应用分发）。测试期 JSON-java 20240303：Public Domain。

AndroidX / Jetpack Compose：Apache-2.0。Kotlin：Apache-2.0。
构建期 Python 包 mwparserfromhell：MIT；opencc-python-reimplemented：Apache-2.0。
Gradle 与 Android 构建工具的许可由各自发行版提供。

## 字体

界面字体来自 [Google Fonts](https://github.com/google/fonts)，均为 SIL Open Font License 1.1，未修改字体文件：

- Caprasimo Regular（标题）：Copyright 2023 The Caprasimo Project Authors，`app/src/main/res/font/caprasimo_regular.ttf`。
- Figtree 可变字重（正文）：Copyright 2022 The Figtree Project Authors，`app/src/main/res/font/figtree_variable.ttf`。

许可证原文随应用分发于 `app/src/main/assets/licenses/OFL-Caprasimo.txt` 与 `OFL-Figtree.txt`，可在“设置与关于”中阅读。两款字体不含中文字形，中文由系统字体显示。
