# Statlog config quick guide

Edit these files in this folder:

- `statlog-attributes.toml`: currencies, tabs, visible stats, icons, costs and limits.
- `statlog-common.toml`: XP mode, progression, editing and refunds.
- `statlog-client.toml`: menu display, colors, sounds and positions.
- `statlog-tiers.toml`: item requirements and penalties.
- `lang/en_us.json`: English menu text. Add another `<language_code>.json` here for another language.

Run `/statlog reload` after editing attributes or item tiers. Reopen the menu to see changes. Use F3+T or restart the client after editing language files. Existing files are never replaced by a mod update.

For common changes, look under `[progression]` for XP costs and leveling, and `[access]` for editing and respec. In the client file, `[interface]` has visible controls, sounds, and maxed-stat styling; the other sections adjust tooltip, XP bar, and scrollbar placement. Every setting has a comment in its TOML file. You can leave a setting at its default and only change the ones you need.

## Add a stat

Copy a `[[listed_attributes]]` block in `statlog-attributes.toml`:

```toml
[[listed_attributes]]
attribute = "minecraft:generic.attack_damage"
key = "strength"
name = "Strength"
description = "Increases attack damage."
icon = "strength.png"
tab = "combat"
increment_value = 0.2
```

`key` is the short name for `/statlog stat` and KubeJS. Keep it stable. Full attribute IDs also work. Without a key, Statlog derives one from `name`. PNG filenames are created under `icons/assets/statlog/textures/gui/attributes/` on the client.

Defaults: `min_level = 0`, `max_level = 10`, `starting_level = min_level`, `increment_value = 1.0`, `cost = 1`, `currency = "XP"`, `max_icon = true`, `max_icon_sepia = true`. Set any of these in a stat block to override it. For several tabs use `tabs = ["combat", "survival"]`. Add `[[listed_attributes.extra_attributes]]` with its own `attribute` and `increment_value` to level several attributes together. `level_values = ["5:4"]` overrides an attribute's value at a specific level.

## Currencies and tabs

Add a currency ID to `custom_points`, a display name under `[point_names]`, and a `#RRGGBB` color under `[point_colors]`. XP is built in. Add a `[[attribute_tabs]]` block with `id`, `name`, `item`, and `texture`. The first six tabs are below the stat panel, and the next five are above it; the edit button has the sixth top slot.

Names, descriptions, tab names and currency names can be plain text or translation keys from `lang/en_us.json`. For example, set `name = "statlog.attribute.strength"` and add `"statlog.attribute.strength": "Strength"` to the language file. A missing key falls back to a resource pack translation.
For another locale, copy `en_us.json` to a file such as `de_de.json` and translate the values. Missing entries use the editable English file.

## Commands and scripts

`/statlog` opens your menu; `/statlog help` shows commands. Admin examples:

```text
/statlog stat list
/statlog stat Dev strength
/statlog stat Dev strength 5
/statlog stat add Dev strength 1
/statlog currency list
/statlog currency Dev POWER 10
/statlog xp Dev 50
/statlog level Dev
/statlog respec Dev strength all
```

The longer `attributeLevel` and `points` command forms still work. Server-side KubeJS scripts can read an individual level with:

```js
const Statlog = Java.loadClass('dev.nidza.statlog.server.StatlogApi')
const strength = Statlog.statLevel(event.player, 'strength')
```

Caps Lock opens Statlog by default. `/statlog orb spawn POWER 10` creates a pickup orb worth 10 POWER. Restriction rules in `statlog-tiers.toml` can target items or blocks by ID or tag, with actions such as `USE_ITEM`, `EAT`, `USE_BLOCK`, `PLACE_BLOCK`, `BREAK_BLOCK`, `ATTACK_ENTITY`, and `INTERACT_ENTITY`.
