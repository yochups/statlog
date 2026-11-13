Statlog

a stats/leveling mod for NeoForge 1.21.1. install Pufferfish's Attributes too. build with `gradlew.bat build`; the jar goes in `build/libs`.

configs are in `config/statlog/`, with a `README.md` there for quick examples. add stats, tabs, and currencies in `statlog-attributes.toml`, item tiers in `statlog-tiers.toml`, set progression and access in `statlog-common.toml`, and change the menu in `statlog-client.toml`. `/statlog reload` applies attribute and tier edits. old config files move into the folder automatically. an editable English language file is created at `config/statlog/lang/en_us.json`; add other locale JSON files beside it and reload resources with F3+T.

the menu opens in edit mode when you have access. spending is only a preview until you press Finalize; closing the menu cancels it. `after_finalize` chooses CLOSE, EDIT, or VIEW (CLOSE by default). the edit tab and XP bar are hidden by default. the add/remove/finalize sounds and maxed stat text can be changed in the client config.

vanilla XP levels are the default currency, with each level costing 50 XP by default. `linear_vanilla_xp` and `vanilla_xp_per_level` control that. set `currency_mode` to `XP` for raw points or `STATLOG_XP` for separate Statlog XP.

put custom currency IDs in `custom_points`, their names or translation keys in `[point_names]`, and their `#RRGGBB` colors in `[point_colors]`. XP and all custom currencies appear in a grid under the player. `statlog:empty` is a stat with no attribute bonus; `statlog:empty/strength` and other suffixes let you have more than one.

`/statlog` opens your own menu and `/statlog help` lists commands. management commands need operator level 2. examples:

`/statlog stat list`
`/statlog stat Dev health 5`
`/statlog stat Dev strength`
`/statlog currency Dev POWER 10`
`/statlog op open Dev`
`/statlog respec Dev all`
`/statlog reset Dev`

`op open` lets an operator edit another player's stats on their own screen. `respec all` refunds every recorded purchase. `reset` clears Statlog stats, points, and history without touching vanilla XP.

item tiers go in `statlog-tiers.toml`. each `[[tiers]]` has an item, stat, required_level, and type. `UNUSABLE` blocks use. `EFFECT` adds a potion effect with `target` and `amplifier`. `PLAYER_ATTRIBUTE` changes an attribute while the item is equipped with `target` and `amount` (for example, -0.5 attack speed). `ITEM_DAMAGE` and `ITEM_MINING_SPEED` multiply that item's melee damage or mining speed using `multiplier` (0.5 means half). old `requirements` rules in the common config still work.

the example in the tier file uses 5/10/15 levels, but you can use any number of tiers and any levels. only the closest unmet tier for an item and stat applies. actions at the same tier and rules from different stats and equipped items stack.

for KubeJS, use `const Statlog = Java.loadClass('dev.nidza.statlog.server.StatlogApi')` in a `kubejs/server_scripts/` script. read one stat with `Statlog.statLevel(event.player, 'strength')` and change it with `Statlog.setAttributeLevel(event.player, 'strength', 5)`. full attribute IDs still work. the example is in `examples/kubejs/server_scripts/statlog_example.js`.

attribute icons are created on the client at `config/statlog/icons/assets/statlog/textures/gui/attributes/` from the filename in each attribute's `icon` field. bundled originals live in `assets/statlog/icon_placeholders/attributes/`; an unfamiliar filename gets a random placeholder. existing PNGs are kept when the game loads or resources reload. generated PNG icons make a sepia `<name>_max.png` at maximum level by default; set `max_icon = false` or `max_icon_sepia = false` per attribute to change that. edit either PNG directly. the `config/statlog/icons/` folder includes `pack.mcmeta` and can be copied into `resourcepacks/`; an enabled resource pack using the same asset paths overrides local generated files.

define up to eleven `[[attribute_tabs]]` entries in `statlog-attributes.toml` with `id`, `name`, an item ID, and a PNG `texture` filename. assign a stat to one or more tabs using `tabs = ["combat", "survival"]` in its `[[listed_attributes]]` entry. the first six tabs fill the bottom edge from right to left, and the next five fill the top edge from left to right; the edit tab occupies the sixth top slot even when hidden. unassigned stats appear on the first tab. the screen opens on the first tab, then remembers your last tab during the game session. tab PNGs are created under `config/statlog/icons/assets/statlog/textures/gui/tabs/` with the edit tab texture as a placeholder; bottom placeholders are upside down. long stat names scroll left and right with pauses. `maxed_text_sepia` in the client config controls sepia names and numbers at max level; `maxed_color` overrides it.

resource packs can also replace other UI textures under `assets/statlog/textures/gui/`, including the currency dot. `statlog_edit_tab.png` has normal and highlighted halves; the book/barrier is drawn separately. text colors are in `assets/statlog/ui_colors.json`. FancyMenu can target `statlog_screen` for buttons and overlays.
