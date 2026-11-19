Statlog

A stats/leveling mod for NeoForge 1.21.1. Build with `gradlew.bat build`, and the jar will appear in `build/libs`.

Configs are in `config/statlog/`, with a `README.md` there for examples. Add stats, tabs, and currencies in `statlog-attributes.toml`, item tiers in `statlog-tiers.toml`, set progression and access in `statlog-common.toml`, and change the menu in `statlog-client.toml`. `/statlog reload` applies attribute and tier edits. Old config files move into the folder automatically. An editable English language file is created at `config/statlog/lang/en_us.json`; add other local JSON files beside it and reload resources with F3+T.

The menu opens in edit mode when you have the right permissions. Spending is only a preview until you press Finalize, whilst closing the menu cancels it. `after_finalize` chooses CLOSE, EDIT, or VIEW (CLOSE by default). The edit tab and XP bar are hidden by default. The add/remove/finalize sounds and maxed stat text can be changed in the client config.

Vanilla XP levels are the default currency, with each level costing 50 XP by default, controlled by `linear_vanilla_xp` and `vanilla_xp_per_level`. Set `currency_mode` to `XP` to spend vanilla XP points instead.

Put custom currency IDs in `custom_points`, their names or translation keys in `[point_names]`, and their `#RRGGBB` colors in `[point_colors]`. Set menu PNGs under `[point_icons]`, orb PNGs under `[point_orb_textures]`, and orb tint under `[point_orb_colors]`. XP and custom currencies appear under the player when custom currencies exist. The registered `statlog:empty` attribute does nothing; use unique IDs such as `statlog:empty/strength` for cosmetic stats. Registered currencies can be spawned as pickup orbs with `/statlog orb spawn POWER 10`.

`/statlog` opens your own menu and `/statlog help` lists commands. Management commands need operator level 2. Examples:

`/statlog stat list`
`/statlog stat Dev health 5`
`/statlog stat Dev strength`
`/statlog currency Dev POWER 10`
`/statlog op open Dev`
`/statlog open tab combat`
`/statlog respec Dev all`
`/statlog reset Dev`

`op open` lets an operator edit another player's stats on their own screen. `respec all` refunds every recorded purchase. `reset` clears Statlog stats, points, and history without resetting vanilla XP.

Item tiers go in `statlog-tiers.toml`. Each `[[tiers]]` has a stat, required_level, and type. `UNUSABLE` denies item use. `EFFECT` adds a potion effect with `target` and `amplifier`. `PLAYER_ATTRIBUTE` changes an attribute while the item is equipped with `target` and `amount` (for example, -0.5 attack speed). `ITEM_DAMAGE` and `ITEM_MINING_SPEED` multiply that item's melee damage or mining speed using `multiplier` (0.5 means half). `RESTRICT` takes an item and/or block ID or `#tag`, several `actions`, and an optional `message`. See the examples in `statlog-tiers.toml`.

The example in the tier file uses 5/10/15 levels, but you can use any number of tiers and any levels. Only the closest unmet tier for an item and stat applies. Actions at the same tier and rules from different stats and equipped items stack.

The 16 default stats include Attack Speed, which also lowers the registered `statlog:target_invulnerability_ticks` attribute. It starts at 10 ticks on living entities and reaches 4 ticks at the default Attack Speed maximum. The target's hurt flash follows the same duration. Armor and Knockback Resistance also apply their matching secondary attributes, and Fall Protection includes Safe Fall Distance. Configure multi-attribute stats with aligned `attributes`, `increments`, `descriptions`, and other value arrays in `statlog-attributes.toml`. Every stat needs a `currency` field, even when it uses XP.

For KubeJS, use `const Statlog = Java.loadClass('dev.nidza.statlog.server.StatlogApi')` in a `kubejs/server_scripts/` script. Read one stat with `Statlog.statLevel(event.player, 'strength')` and change it with `Statlog.setAttributeLevel(event.player, 'strength', 5)`. Full attribute IDs still work. The example is in `examples/kubejs/server_scripts/statlog_example.js`.

Attribute icons are created on the client at `config/statlog/icons/assets/statlog/textures/gui/attributes/` from the filename in each attribute's `icon` field. Bundled originals are in `assets/statlog/icon_placeholders/attributes/`. An unfamiliar or broken filename gets a random placeholder. Existing PNGs are kept when the game loads or resources reload. Generated PNG icons make a sepia `<name>_max.png` at maximum level by default. Set `max_icon = false` or `max_icon_sepia = false` per attribute to change this. The `config/statlog/icons/` folder includes `pack.mcmeta` and can be copied into `resourcepacks/` - An enabled resource pack using the same asset paths overrides local generated files, per usual resource pack rules.

Define up to eleven `[[attribute_tabs]]` entries in `statlog-attributes.toml` with `id`, `name`, an item ID, and a PNG `texture` filename. Assign a stat to one or more tabs using `tabs = ["combat", "survival"]` in its `[[listed_attributes]]` entry. Set `visible = false` to hide a tab button without removing its assignments. Six slots are available along each edge; the edit tab reserves one top slot. A single visible tab is hidden by default. Unassigned stats appear on the first tab. The screen opens on the first tab, then remembers your last tab during the game session. `/statlog open tab combat` opens a specified tab. Tab PNGs are created under `config/statlog/icons/assets/statlog/textures/gui/tabs/`, with the edit tab texture as a placeholder. Bottom placeholders are upside down. Too-long stat names scroll left and right with pauses. `maxed_text_sepia` in the client config controls sepia names and numbers at max level; `maxed_color` overrides it.

The client config controls panel width and height, whether the sole XP balance and row icons are shown, currency icons in tooltips, and the Finalize sound. Current and next value text is gray; set either separator to `Break` to put values on separate lines. For a vanilla attribute whose base is zero, use `percentage_bases` to supply a percentage reference. `Next Level` is hidden at maximum level.

Questlog is optional. When installed, it can track stat gains, losses, or reaching a target level through the `statlog:stat` objective. The `statlog:currency` and `statlog:stat` rewards grant currencies and levels. An example repeated quest is in `examples/questlog/quests/statlog_training.json`.

Resource packs can also replace other UI textures under `assets/statlog/textures/gui/`, including the currency dot. `statlog_edit_tab.png` has normal and highlighted halves (The book/barrier is drawn separately). Text colors are in `assets/statlog/ui_colors.json`. FancyMenu can target `statlog_screen` for buttons and overlays.
